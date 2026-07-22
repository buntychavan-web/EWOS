package com.ewos.performance.api;

import com.ewos.performance.api.dto.CalibrationSessionResponse;
import com.ewos.performance.api.dto.CreateCalibrationSessionRequest;
import com.ewos.performance.application.CalibrationSessionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calibration-sessions")
@Tag(name = "Calibration Sessions", description = "Group calibration for a performance cycle")
public class CalibrationSessionController {

    private final CalibrationSessionService sessions;

    public CalibrationSessionController(CalibrationSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERF_CALIBRATE')")
    @Operation(summary = "Create a calibration session")
    public ResponseEntity<CalibrationSessionResponse> create(
            @Valid @RequestBody CreateCalibrationSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.create(req));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PERF_CALIBRATE')")
    @Operation(summary = "Mark a calibration session complete")
    public CalibrationSessionResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam(required = false) String notes) {
        return sessions.complete(tenantId, id, notes);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "List calibration sessions for a cycle")
    public List<CalibrationSessionResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID cycleId) {
        return sessions.listForCycle(tenantId, cycleId);
    }
}
