package com.ewos.attendance.api;

import com.ewos.attendance.api.dto.CreateHolidayRequest;
import com.ewos.attendance.api.dto.HolidayResponse;
import com.ewos.attendance.application.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sprint 24L item 3 — holiday calendar administration. */
@RestController
@RequestMapping("/api/v1/attendance/holidays")
@Tag(name = "Holidays", description = "Tenant/company holiday calendar for attendance-driven LOP")
public class HolidayController {

    private final HolidayService service;

    public HolidayController(HolidayService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATT_ADMIN')")
    @Operation(summary = "Add a holiday (tenant-wide when companyId is omitted)")
    public ResponseEntity<HolidayResponse> create(
            @Valid @RequestBody CreateHolidayRequest request) {
        HolidayResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/attendance/holidays/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Fetch a holiday by ID")
    public HolidayResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "List every holiday for the tenant")
    public List<HolidayResponse> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/effective")
    @PreAuthorize("hasAuthority('ATT_READ')")
    @Operation(summary = "Holidays effective for one company (company-specific plus tenant-wide)")
    public List<HolidayResponse> forCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.forCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ATT_ADMIN')")
    @Operation(summary = "Remove a holiday")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
