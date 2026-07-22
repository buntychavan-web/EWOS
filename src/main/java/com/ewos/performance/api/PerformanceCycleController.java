package com.ewos.performance.api;

import com.ewos.performance.api.dto.CreatePerformanceCycleRequest;
import com.ewos.performance.api.dto.PerformanceCycleResponse;
import com.ewos.performance.api.dto.UpdatePerformanceCycleRequest;
import com.ewos.performance.application.PerformanceCycleService;
import com.ewos.performance.domain.PerformanceCycleStatus;
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
@RequestMapping("/api/v1/performance-cycles")
@Tag(name = "Performance Cycles", description = "Per-company performance cycle configuration")
public class PerformanceCycleController {

    private final PerformanceCycleService cycles;

    public PerformanceCycleController(PerformanceCycleService cycles) {
        this.cycles = cycles;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(summary = "Create a performance cycle")
    public ResponseEntity<PerformanceCycleResponse> create(
            @Valid @RequestBody CreatePerformanceCycleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cycles.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(summary = "Update a performance cycle")
    public PerformanceCycleResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePerformanceCycleRequest req) {
        return cycles.update(tenantId, id, req);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('PERF_WRITE')")
    @Operation(summary = "Transition a cycle to a new status")
    public PerformanceCycleResponse transition(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam PerformanceCycleStatus target) {
        return cycles.transition(tenantId, id, target);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Fetch a performance cycle by id")
    public PerformanceCycleResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return cycles.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "List performance cycles for a company")
    public List<PerformanceCycleResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return cycles.list(tenantId, companyId);
    }
}
