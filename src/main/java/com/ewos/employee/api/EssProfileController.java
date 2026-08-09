package com.ewos.employee.api;

import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EssProfileUpdateRequest;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.application.EmployeeService;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — Profile Self-Service (PRD §4.5). {@code employeeId} is always resolved from {@link
 * EmployeeContext#currentEmployeeId()}; it is never accepted from a path/body parameter, so a
 * caller cannot update anyone's record but their own regardless of what a forged request claims.
 */
@RestController
@RequestMapping("/api/v1/self-service/me")
@Tag(
        name = "Profile Self-Service",
        description =
                "The caller's own non-sensitive profile fields (phone, personal email,"
                        + " emergency contact, avatar URI)")
public class EssProfileController {

    private static final String HEADER = "Idempotency-Key";
    private static final String ENDPOINT = "self-service.profile.update";

    private final EmployeeService employees;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;
    private final IdempotencyService idempotency;

    public EssProfileController(
            EmployeeService employees,
            EmployeeContext employeeContext,
            TenantContext tenantContext,
            IdempotencyService idempotency) {
        this.employees = employees;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
        this.idempotency = idempotency;
    }

    @PatchMapping
    @Operation(
            summary =
                    "Update the caller's own phone, personal email, emergency contact, or avatar"
                            + " URI; workEmail and displayName are not editable here")
    public EmployeeResponse updateMe(
            @RequestHeader(value = HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody EssProfileUpdateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, HEADER + " header is required");
        }
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        UUID actorId =
                tenantContext
                        .currentUserId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Authenticated user required"));
        return idempotency.execute(
                tenantId,
                actorId,
                ENDPOINT,
                idempotencyKey,
                EmployeeResponse.class,
                () -> employees.updateMe(tenantId, employeeId, request));
    }

    private UUID requireEmployeeId() {
        return employeeContext
                .currentEmployeeId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }
}
