package com.ewos.exit.api;

import com.ewos.exit.api.dto.AlumniResponse;
import com.ewos.exit.api.dto.CreateAlumniRequest;
import com.ewos.exit.api.dto.UpdateAlumniRequest;
import com.ewos.exit.application.ExitService;
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
@RequestMapping("/api/v1/exit/alumni")
@Tag(name = "Exit — Alumni", description = "Alumni records, rehire eligibility")
public class AlumniController {

    private final ExitService exit;

    public AlumniController(ExitService exit) {
        this.exit = exit;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALUMNI_MANAGE')")
    @Operation(summary = "Create an alumni record")
    public ResponseEntity<AlumniResponse> create(@Valid @RequestBody CreateAlumniRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exit.createAlumni(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ALUMNI_MANAGE')")
    @Operation(summary = "Update an alumni record")
    public AlumniResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAlumniRequest req) {
        return exit.updateAlumni(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "Fetch an alumni record")
    public AlumniResponse get(@RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return exit.getAlumni(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "List alumni records for a company")
    public List<AlumniResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return exit.listAlumni(tenantId, companyId);
    }
}
