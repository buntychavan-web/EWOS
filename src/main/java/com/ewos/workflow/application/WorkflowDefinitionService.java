package com.ewos.workflow.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.ewos.workflow.api.dto.StateDefinitionSpec;
import com.ewos.workflow.api.dto.TransitionDefinitionSpec;
import com.ewos.workflow.api.dto.WorkflowDefinitionResponse;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTransition;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowDefinitionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring API for workflow definitions. A definition is immutable once created; to change the
 * shape, publish a new version with an incremented {@code definitionVersion}.
 */
@Service
@Transactional
public class WorkflowDefinitionService {

    private static final String DEF_CACHE = "workflow.definition";

    private final WorkflowDefinitionRepository repository;
    private final WorkflowTransitionPolicy policy;
    private final WorkflowMapper mapper;
    private final ApplicationEventPublisher events;

    public WorkflowDefinitionService(
            WorkflowDefinitionRepository repository,
            WorkflowTransitionPolicy policy,
            WorkflowMapper mapper,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    @CacheEvict(value = DEF_CACHE, allEntries = true)
    public WorkflowDefinitionResponse create(CreateWorkflowDefinitionRequest request) {
        int version = request.definitionVersion() != null ? request.definitionVersion() : 1;
        if (repository.existsByTenantIdAndCodeIgnoreCaseAndDefinitionVersion(
                request.tenantId(), request.code(), version)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Workflow definition '" + request.code() + "' v" + version + " already exists");
        }
        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(request.tenantId());
        def.setCode(request.code());
        def.setName(request.name());
        def.setDescription(request.description());
        def.setSubjectType(request.subjectType());
        def.setDefinitionVersion(version);
        def.setActive(true);

        Map<String, WorkflowState> statesByCode = new HashMap<>();
        for (StateDefinitionSpec spec : request.states()) {
            WorkflowState state = new WorkflowState();
            state.setDefinition(def);
            state.setCode(spec.code());
            state.setName(spec.name());
            state.setInitial(spec.initial());
            state.setTerminal(spec.terminal());
            if (spec.sortOrder() != null) {
                state.setSortOrder(spec.sortOrder());
            }
            state.setSlaHours(spec.slaHours());
            def.getStates().add(state);
            statesByCode.put(spec.code().toLowerCase(Locale.ROOT), state);
        }

        if (request.transitions() != null) {
            for (TransitionDefinitionSpec spec : request.transitions()) {
                WorkflowState from =
                        statesByCode.get(spec.fromStateCode().toLowerCase(Locale.ROOT));
                WorkflowState to = statesByCode.get(spec.toStateCode().toLowerCase(Locale.ROOT));
                if (from == null || to == null) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Transition references unknown state: "
                                    + spec.fromStateCode()
                                    + " -> "
                                    + spec.toStateCode());
                }
                WorkflowTransition transition = new WorkflowTransition();
                transition.setDefinition(def);
                transition.setFromState(from);
                transition.setToState(to);
                transition.setActionCode(spec.actionCode());
                transition.setRequiredRole(spec.requiredRole());
                transition.setAuto(spec.auto());
                transition.setGuardExpression(spec.guardExpression());
                def.getTransitions().add(transition);
            }
        }

        policy.assertDefinitionShape(def);
        WorkflowDefinition saved = repository.save(def);

        events.publishEvent(
                new WorkflowEvent(
                        WorkflowEventType.DEFINITION_PUBLISHED,
                        null,
                        saved.getId(),
                        saved.getTenantId(),
                        null,
                        saved.getSubjectType(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        currentActor(),
                        Instant.now()));

        return mapper.toResponse(saved);
    }

    @Cacheable(value = DEF_CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderByCodeAscDefinitionVersionDesc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @CacheEvict(value = DEF_CACHE, allEntries = true)
    public WorkflowDefinitionResponse setActive(UUID tenantId, UUID id, boolean active) {
        WorkflowDefinition def = require(tenantId, id);
        def.setActive(active);
        return mapper.toResponse(def);
    }

    @CacheEvict(value = DEF_CACHE, allEntries = true)
    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    /** Package-private lookup used by other workflow services; skips cache to see latest state. */
    WorkflowDefinition require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Workflow definition not found"));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
