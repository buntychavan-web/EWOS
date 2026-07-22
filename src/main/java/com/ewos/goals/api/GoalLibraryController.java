package com.ewos.goals.api;

import com.ewos.goals.api.dto.CreateGoalLibraryItemRequest;
import com.ewos.goals.api.dto.GoalLibraryItemResponse;
import com.ewos.goals.api.dto.UpdateGoalLibraryItemRequest;
import com.ewos.goals.application.GoalLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goal-library")
@Tag(name = "Goal Library", description = "Reusable goal templates (KRA / KPI / OKR)")
public class GoalLibraryController {

    private final GoalLibraryService library;

    public GoalLibraryController(GoalLibraryService library) {
        this.library = library;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('GOAL_ADMIN')")
    @Operation(summary = "Create a goal library item")
    public ResponseEntity<GoalLibraryItemResponse> create(
            @Valid @RequestBody CreateGoalLibraryItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(library.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GOAL_ADMIN')")
    @Operation(summary = "Update a goal library item")
    public GoalLibraryItemResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalLibraryItemRequest req) {
        return library.update(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "Fetch a goal library item by id")
    public GoalLibraryItemResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return library.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GOAL_READ')")
    @Operation(summary = "List active goal library items for a company")
    public List<GoalLibraryItemResponse> listActive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return library.listActive(tenantId, companyId);
    }
}
