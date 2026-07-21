package com.ewos.offer.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.ConfirmJoiningRequest;
import com.ewos.offer.api.dto.PreboardingChecklistResponse;
import com.ewos.offer.api.dto.PreboardingTaskInstanceResponse;
import com.ewos.offer.api.dto.UpdateTaskStatusRequest;
import com.ewos.offer.domain.BackgroundVerificationService;
import com.ewos.offer.domain.EmployeeIdGenerator;
import com.ewos.offer.domain.MedicalCheckService;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNotifier;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.domain.events.OfferEventType;
import com.ewos.offer.domain.preboarding.PreboardingChecklist;
import com.ewos.offer.domain.preboarding.PreboardingChecklistStatus;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import com.ewos.offer.domain.preboarding.PreboardingTaskStatus;
import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import com.ewos.offer.domain.preboarding.PreboardingTaskType;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.offer.infrastructure.persistence.PreboardingChecklistRepository;
import com.ewos.offer.infrastructure.persistence.PreboardingTaskInstanceRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns pre-boarding — creates a checklist for an accepted offer, materialises task instances from
 * the tenant's task-template catalogue, drives task lifecycle, records BGV / medical / employee-id
 * initiation through the framework contracts, and produces the joining confirmation that hands off
 * to the Employee master.
 */
@Service
@Transactional
public class PreboardingService {

    private final PreboardingChecklistRepository checklists;
    private final PreboardingTaskInstanceRepository tasks;
    private final PreboardingTaskTemplateService templates;
    private final OfferRepository offers;
    private final EmployeeRepository employees;
    private final BackgroundVerificationService bgv;
    private final MedicalCheckService medical;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final OfferNotifier notifier;
    private final OfferMapper mapper;
    private final ApplicationEventPublisher events;

    public PreboardingService(
            PreboardingChecklistRepository checklists,
            PreboardingTaskInstanceRepository tasks,
            PreboardingTaskTemplateService templates,
            OfferRepository offers,
            EmployeeRepository employees,
            BackgroundVerificationService bgv,
            MedicalCheckService medical,
            EmployeeIdGenerator employeeIdGenerator,
            OfferNotifier notifier,
            OfferMapper mapper,
            ApplicationEventPublisher events) {
        this.checklists = checklists;
        this.tasks = tasks;
        this.templates = templates;
        this.offers = offers;
        this.employees = employees;
        this.bgv = bgv;
        this.medical = medical;
        this.employeeIdGenerator = employeeIdGenerator;
        this.notifier = notifier;
        this.mapper = mapper;
        this.events = events;
    }

    /**
     * Materialise a checklist for an accepted offer. Idempotent: a second call returns the existing
     * checklist.
     */
    public PreboardingChecklistResponse createChecklistForOffer(UUID tenantId, UUID offerId) {
        Offer offer =
                offers.findByIdAndTenantId(offerId, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be ACCEPTED to start pre-boarding (current: "
                            + offer.getStatus()
                            + ")");
        }

        PreboardingChecklist existing =
                checklists.findByTenantIdAndOfferId(tenantId, offerId).orElse(null);
        if (existing != null) {
            return mapper.toResponse(existing);
        }

        PreboardingChecklist c = new PreboardingChecklist();
        c.setTenantId(tenantId);
        c.setCompanyId(offer.getCompanyId());
        c.setOffer(offer);
        c.setApplication(offer.getApplication());
        c.setCandidate(offer.getCandidate());
        c.setJoiningDate(offer.getTargetJoiningDate());
        c.setStatus(PreboardingChecklistStatus.PENDING);
        c.setCompletionPercent(BigDecimal.ZERO);
        c = checklists.save(c);

        List<PreboardingTaskTemplate> active =
                templates.activeTemplatesFor(tenantId, offer.getCompanyId());
        for (PreboardingTaskTemplate tpl : active) {
            spawnTask(c, tpl);
        }
        recomputeCompletion(c);
        publish(OfferEventType.PREBOARDING_CHECKLIST_CREATED, c, null, null);
        return mapper.toResponse(c);
    }

    public PreboardingTaskInstanceResponse updateTaskStatus(
            UUID tenantId, UUID taskId, UpdateTaskStatusRequest req) {
        PreboardingTaskInstance t =
                tasks.findByIdAndTenantId(taskId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Preboarding task not found"));
        PreboardingTaskStatus previous = t.getStatus();
        if (t.isTerminal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Task is already terminal (" + previous + ")");
        }
        applyStatus(t, req.status(), req.notes(), req.resultJson());
        PreboardingChecklist c = t.getChecklist();
        c.setStatus(computeChecklistStatus(c));
        recomputeCompletion(c);

        OfferEventType eventType =
                switch (req.status()) {
                    case COMPLETED -> OfferEventType.PREBOARDING_TASK_COMPLETED;
                    case SKIPPED -> OfferEventType.PREBOARDING_TASK_SKIPPED;
                    case FAILED -> OfferEventType.PREBOARDING_TASK_FAILED;
                    default -> OfferEventType.PREBOARDING_TASK_STARTED;
                };
        publish(eventType, c, t.getId(), previous + "->" + t.getStatus());
        return mapper.toResponse(t);
    }

    public PreboardingChecklistResponse confirmJoining(
            UUID tenantId, UUID checklistId, ConfirmJoiningRequest req) {
        PreboardingChecklist c = require(tenantId, checklistId);
        if (c.getStatus() != PreboardingChecklistStatus.COMPLETED
                && c.getStatus() != PreboardingChecklistStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Checklist must be COMPLETED or IN_PROGRESS to confirm joining (current: "
                            + c.getStatus()
                            + ")");
        }
        if (hasOutstandingMandatory(c)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot confirm joining while mandatory tasks are still outstanding");
        }
        c.setStatus(PreboardingChecklistStatus.JOINED);
        c.setJoiningConfirmedAt(Instant.now());
        c.setJoiningConfirmedBy(OfferSecurity.currentActor());
        if (req != null && req.employeeId() != null) {
            Employee e =
                    employees
                            .findByIdAndTenantId(req.employeeId(), tenantId)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST, "Employee not found"));
            c.setEmployee(e);
        }
        if (req != null && req.notes() != null) {
            c.setNotes(req.notes());
        }
        publish(OfferEventType.PREBOARDING_CHECKLIST_JOINED, c, null, null);
        notifier.notifyCandidateJoined(c.getOffer());
        return mapper.toResponse(c);
    }

    public PreboardingChecklistResponse markNoShow(UUID tenantId, UUID checklistId, String reason) {
        PreboardingChecklist c = require(tenantId, checklistId);
        if (c.getStatus() == PreboardingChecklistStatus.JOINED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Candidate has already joined; cannot mark NO_SHOW");
        }
        c.setStatus(PreboardingChecklistStatus.NO_SHOW);
        c.setNotes(reason);
        publish(OfferEventType.PREBOARDING_CHECKLIST_NO_SHOW, c, null, reason);
        return mapper.toResponse(c);
    }

    public PreboardingChecklistResponse cancel(UUID tenantId, UUID checklistId, String reason) {
        PreboardingChecklist c = require(tenantId, checklistId);
        if (c.getStatus() == PreboardingChecklistStatus.JOINED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Candidate has already joined; cannot cancel");
        }
        c.setStatus(PreboardingChecklistStatus.CANCELLED);
        c.setNotes(reason);
        publish(OfferEventType.PREBOARDING_CHECKLIST_CANCELLED, c, null, reason);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public PreboardingChecklistResponse getById(UUID tenantId, UUID checklistId) {
        return mapper.toResponse(require(tenantId, checklistId));
    }

    @Transactional(readOnly = true)
    public List<PreboardingTaskInstanceResponse> tasksForChecklist(
            UUID tenantId, UUID checklistId) {
        require(tenantId, checklistId);
        return tasks
                .findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(tenantId, checklistId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PreboardingChecklistResponse> byStatus(
            UUID tenantId, UUID companyId, PreboardingChecklistStatus status) {
        return checklists
                .findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void spawnTask(PreboardingChecklist c, PreboardingTaskTemplate tpl) {
        PreboardingTaskInstance t = new PreboardingTaskInstance();
        t.setTenantId(c.getTenantId());
        t.setChecklist(c);
        t.setTemplate(tpl);
        t.setName(tpl.getName());
        t.setTaskType(tpl.getTaskType());
        t.setOwner(tpl.getDefaultOwner());
        t.setMandatory(tpl.isMandatory());
        t.setSortOrder(tpl.getSortOrder());
        if (tpl.getDefaultSlaDays() != null && c.getJoiningDate() != null) {
            t.setDueDate(c.getJoiningDate().minusDays(tpl.getDefaultSlaDays()));
        }

        // Auto-initiate long-running vendor tasks so the checklist reflects real progress.
        switch (tpl.getTaskType()) {
            case BACKGROUND_VERIFICATION -> {
                String ref = bgv.initiate(t);
                if (ref != null) {
                    t.setExternalRef(ref);
                    t.setStatus(PreboardingTaskStatus.IN_PROGRESS);
                    t.setStartedAt(Instant.now());
                }
            }
            case MEDICAL_CHECK -> {
                String ref = medical.initiate(t);
                if (ref != null) {
                    t.setExternalRef(ref);
                    t.setStatus(PreboardingTaskStatus.IN_PROGRESS);
                    t.setStartedAt(Instant.now());
                }
            }
            case EMPLOYEE_ID -> {
                String id = employeeIdGenerator.generate(c.getTenantId(), c.getCompanyId());
                t.setExternalRef(id);
                t.setResultJson("{\"employeeId\":\"" + id + "\"}");
                t.setStatus(PreboardingTaskStatus.COMPLETED);
                t.setStartedAt(Instant.now());
                t.setCompletedAt(Instant.now());
            }
            default -> {
                // manual task; leave PENDING
            }
        }
        tasks.save(t);
        publish(OfferEventType.PREBOARDING_TASK_CREATED, c, t.getId(), tpl.getTaskType().name());
    }

    private void applyStatus(
            PreboardingTaskInstance t,
            PreboardingTaskStatus target,
            String notes,
            String resultJson) {
        Instant now = Instant.now();
        if (target == PreboardingTaskStatus.IN_PROGRESS && t.getStartedAt() == null) {
            t.setStartedAt(now);
        }
        if (target == PreboardingTaskStatus.COMPLETED
                || target == PreboardingTaskStatus.SKIPPED
                || target == PreboardingTaskStatus.FAILED) {
            t.setCompletedAt(now);
            t.setCompletedBy(OfferSecurity.currentActor());
        }
        if (target == PreboardingTaskStatus.FAILED
                && t.getTaskType() == PreboardingTaskType.BACKGROUND_VERIFICATION) {
            bgv.cancel(t);
        }
        if (target == PreboardingTaskStatus.FAILED
                && t.getTaskType() == PreboardingTaskType.MEDICAL_CHECK) {
            medical.cancel(t);
        }
        t.setStatus(target);
        if (notes != null) {
            t.setNotes(notes);
        }
        if (resultJson != null) {
            t.setResultJson(resultJson);
        }
    }

    private void recomputeCompletion(PreboardingChecklist c) {
        List<PreboardingTaskInstance> siblings =
                tasks.findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(
                        c.getTenantId(), c.getId());
        if (siblings.isEmpty()) {
            c.setCompletionPercent(BigDecimal.ZERO);
            return;
        }
        long done = siblings.stream().filter(PreboardingTaskInstance::isTerminal).count();
        BigDecimal pct =
                new BigDecimal(done * 100L)
                        .divide(new BigDecimal(siblings.size()), 2, RoundingMode.HALF_UP);
        c.setCompletionPercent(pct);
        if (done == siblings.size() && c.getStatus() != PreboardingChecklistStatus.JOINED) {
            c.setStatus(PreboardingChecklistStatus.COMPLETED);
            publish(OfferEventType.PREBOARDING_CHECKLIST_COMPLETED, c, null, null);
        }
    }

    private PreboardingChecklistStatus computeChecklistStatus(PreboardingChecklist c) {
        if (c.getStatus() == PreboardingChecklistStatus.PENDING) {
            return PreboardingChecklistStatus.IN_PROGRESS;
        }
        return c.getStatus();
    }

    private boolean hasOutstandingMandatory(PreboardingChecklist c) {
        return tasks
                .findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(c.getTenantId(), c.getId())
                .stream()
                .anyMatch(t -> t.isMandatory() && !t.isTerminal());
    }

    private PreboardingChecklist require(UUID tenantId, UUID id) {
        return checklists
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Checklist not found"));
    }

    @SuppressWarnings("unused")
    private static LocalDate today() {
        return Instant.now().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void publish(OfferEventType type, PreboardingChecklist c, UUID taskId, String detail) {
        events.publishEvent(
                new OfferEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getOffer() == null ? null : c.getOffer().getId(),
                        c.getOffer() == null ? null : c.getOffer().getOfferNumber(),
                        c.getApplication() == null ? null : c.getApplication().getId(),
                        c.getCandidate() == null ? null : c.getCandidate().getId(),
                        c.getId(),
                        taskId,
                        detail,
                        OfferSecurity.currentActor(),
                        Instant.now()));
    }
}
