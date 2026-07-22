package com.ewos.goals.application;

import com.ewos.goals.api.GoalMapper;
import com.ewos.goals.api.dto.CreateGoalLibraryItemRequest;
import com.ewos.goals.api.dto.GoalLibraryItemResponse;
import com.ewos.goals.api.dto.UpdateGoalLibraryItemRequest;
import com.ewos.goals.domain.GoalLibraryItem;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.domain.events.GoalEventType;
import com.ewos.goals.infrastructure.persistence.GoalLibraryRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoalLibraryService {

    private final GoalLibraryRepository library;
    private final GoalMapper mapper;
    private final ApplicationEventPublisher events;

    public GoalLibraryService(
            GoalLibraryRepository library, GoalMapper mapper, ApplicationEventPublisher events) {
        this.library = library;
        this.mapper = mapper;
        this.events = events;
    }

    public GoalLibraryItemResponse create(CreateGoalLibraryItemRequest req) {
        if (library.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Goal library item code already exists: " + req.code());
        }
        GoalLibraryItem g = new GoalLibraryItem();
        g.setTenantId(req.tenantId());
        g.setCompanyId(req.companyId());
        g.setCode(req.code());
        g.setName(req.name());
        g.setDescription(req.description());
        g.setGoalType(req.goalType());
        g.setCategory(req.category());
        g.setDefaultWeightage(
                req.defaultWeightage() == null ? BigDecimal.ZERO : req.defaultWeightage());
        g.setDefaultTarget(req.defaultTarget());
        g.setUnitOfMeasure(req.unitOfMeasure());
        g.setActive(true);
        g = library.save(g);
        publish(GoalEventType.LIBRARY_ITEM_CREATED, g);
        return mapper.toResponse(g);
    }

    public GoalLibraryItemResponse update(
            UUID tenantId, UUID id, UpdateGoalLibraryItemRequest req) {
        GoalLibraryItem g = require(tenantId, id);
        boolean deactivating = g.isActive() && !req.active();
        g.setName(req.name());
        g.setDescription(req.description());
        g.setCategory(req.category());
        g.setDefaultWeightage(
                req.defaultWeightage() == null ? BigDecimal.ZERO : req.defaultWeightage());
        g.setDefaultTarget(req.defaultTarget());
        g.setUnitOfMeasure(req.unitOfMeasure());
        g.setActive(req.active());
        publish(
                deactivating
                        ? GoalEventType.LIBRARY_ITEM_DEACTIVATED
                        : GoalEventType.LIBRARY_ITEM_UPDATED,
                g);
        return mapper.toResponse(g);
    }

    @Transactional(readOnly = true)
    public GoalLibraryItemResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<GoalLibraryItemResponse> listActive(UUID tenantId, UUID companyId) {
        return library.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    GoalLibraryItem require(UUID tenantId, UUID id) {
        return library.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Goal library item not found"));
    }

    private void publish(GoalEventType type, GoalLibraryItem g) {
        events.publishEvent(
                new GoalEvent(
                        type,
                        g.getTenantId(),
                        g.getCompanyId(),
                        null,
                        g.getId(),
                        null,
                        null,
                        null,
                        GoalSecurity.currentActor(),
                        Instant.now()));
    }
}
