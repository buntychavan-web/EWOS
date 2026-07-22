package com.ewos.learning.api;

import com.ewos.learning.api.dto.CertificationResponse;
import com.ewos.learning.api.dto.IssueCertificationRequest;
import com.ewos.learning.api.dto.RevokeCertificationRequest;
import com.ewos.learning.application.CertificationService;
import com.ewos.learning.domain.CertificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/certifications")
@Tag(name = "Certifications", description = "Employee certifications + expiry tracking")
public class CertificationController {

    private final CertificationService certifications;

    public CertificationController(CertificationService certifications) {
        this.certifications = certifications;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LEARNING_CERTIFY')")
    @Operation(summary = "Issue a certification")
    public ResponseEntity<CertificationResponse> issue(
            @Valid @RequestBody IssueCertificationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(certifications.issue(req));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('LEARNING_CERTIFY')")
    @Operation(summary = "Revoke a certification")
    public CertificationResponse revoke(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody RevokeCertificationRequest req) {
        return certifications.revoke(tenantId, id, req);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasAuthority('LEARNING_CERTIFY')")
    @Operation(summary = "Mark a certification as expired")
    public CertificationResponse markExpired(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return certifications.markExpired(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Fetch a certification by id")
    public CertificationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return certifications.getById(tenantId, id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List certifications for an employee")
    public List<CertificationResponse> byEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return certifications.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "List certifications by status")
    public List<CertificationResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam CertificationStatus status) {
        return certifications.byStatus(tenantId, companyId, status);
    }

    @GetMapping("/reports/expiring")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Certifications expiring on or before the given date")
    public List<CertificationResponse> expiring(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        return certifications.expiringBy(tenantId, companyId, through);
    }
}
