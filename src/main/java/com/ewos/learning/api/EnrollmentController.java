package com.ewos.learning.api;

import com.ewos.learning.api.dto.AssessmentRequest;
import com.ewos.learning.api.dto.AttendanceRequest;
import com.ewos.learning.api.dto.EnrollmentResponse;
import com.ewos.learning.api.dto.NominateRequest;
import com.ewos.learning.api.dto.WithdrawRequest;
import com.ewos.learning.application.EnrollmentService;
import com.ewos.learning.domain.EnrollmentStatus;
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
@RequestMapping("/api/v1/training-enrollments")
@Tag(
        name = "Training Enrollments",
        description = "Nomination + enrollment + attendance + assessment")
public class EnrollmentController {

    private final EnrollmentService enrollments;

    public EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @PostMapping("/nominate")
    @PreAuthorize("hasAuthority('LEARNING_NOMINATE')")
    @Operation(summary = "Nominate an employee for a course")
    public ResponseEntity<EnrollmentResponse> nominate(@Valid @RequestBody NominateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollments.nominate(req));
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasAuthority('LEARNING_ENROLL')")
    @Operation(summary = "Confirm enrollment for a nomination")
    public EnrollmentResponse enroll(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return enrollments.enroll(tenantId, id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('LEARNING_ATTEND')")
    @Operation(summary = "Mark the enrollment as started")
    public EnrollmentResponse start(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return enrollments.start(tenantId, id);
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('LEARNING_ATTEND')")
    @Operation(summary = "Record attendance percentage")
    public EnrollmentResponse recordAttendance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequest req) {
        return enrollments.recordAttendance(tenantId, id, req);
    }

    @PostMapping("/{id}/assessment")
    @PreAuthorize("hasAuthority('LEARNING_ATTEND')")
    @Operation(summary = "Record assessment score + pass/fail")
    public EnrollmentResponse recordAssessment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AssessmentRequest req) {
        return enrollments.recordAssessment(tenantId, id, req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('LEARNING_ATTEND')")
    @Operation(summary = "Complete the enrollment (pass → COMPLETED, fail → FAILED)")
    public EnrollmentResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return enrollments.complete(tenantId, id);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('LEARNING_ENROLL')")
    @Operation(summary = "Withdraw from the enrollment")
    public EnrollmentResponse withdraw(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest req) {
        return enrollments.withdraw(tenantId, id, req);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('LEARNING_ATTEND')")
    @Operation(summary = "Mark the enrollment as NO_SHOW")
    public EnrollmentResponse noShow(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return enrollments.markNoShow(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Fetch an enrollment by id")
    public EnrollmentResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return enrollments.getById(tenantId, id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List all enrollments for an employee")
    public List<EnrollmentResponse> byEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return enrollments.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/by-session")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List enrollments attached to a session")
    public List<EnrollmentResponse> bySession(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID sessionId) {
        return enrollments.forSession(tenantId, sessionId);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List enrollments by status")
    public List<EnrollmentResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam EnrollmentStatus status) {
        return enrollments.byStatus(tenantId, companyId, status);
    }
}
