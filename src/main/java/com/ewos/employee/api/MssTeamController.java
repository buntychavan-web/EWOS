package com.ewos.employee.api;

import com.ewos.employee.api.dto.MssTeamMemberDetailResponse;
import com.ewos.employee.api.dto.MssTeamPageResponse;
import com.ewos.employee.application.MssTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — My Team (PRD §4.3/§4.4). Read-only: direct reports of the caller (or, via {@code
 * actingForEmployeeId}, a peer who has actively delegated to them), field-masked per {@code
 * MssFieldVisibilityService}. No indirect-report hierarchy — every read is scoped to {@code
 * manager_employee_id} equality only.
 */
@RestController
@RequestMapping("/api/v1/manager-self-service/team")
@Tag(
        name = "Manager Self-Service Team",
        description = "Direct-report list and detail, field-masked per tenant visibility config")
public class MssTeamController {

    private final MssTeamService service;

    public MssTeamController(MssTeamService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Cursor-paginated list of the caller's direct reports")
    public MssTeamPageResponse list(
            @RequestParam(required = false) UUID actingForEmployeeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return service.list(actingForEmployeeId, cursor, limit);
    }

    @GetMapping("/{employeeId}")
    @Operation(
            summary = "A single direct report's detail; 404 for anyone who is not a direct report")
    public MssTeamMemberDetailResponse detail(
            @PathVariable UUID employeeId,
            @RequestParam(required = false) UUID actingForEmployeeId) {
        return service.detail(employeeId, actingForEmployeeId);
    }
}
