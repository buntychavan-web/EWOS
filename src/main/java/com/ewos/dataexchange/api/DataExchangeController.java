package com.ewos.dataexchange.api;

import com.ewos.dataexchange.api.dto.CreateDataExchangeRequest;
import com.ewos.dataexchange.api.dto.DataExchangeHistoryResponse;
import com.ewos.dataexchange.api.dto.DataExchangeResponse;
import com.ewos.dataexchange.api.dto.MarkFailedRequest;
import com.ewos.dataexchange.application.DataExchangeService;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/data-exchange")
@Tag(
        name = "Data Exchange",
        description =
                "Sprint 14.3 — operational queue for exchanging payroll/HR data with an external"
                        + " system. Tracks lifecycle only (Pending/Processing/Success/Failed/"
                        + "Retry/Acknowledged/Cancelled); no connector performs the actual call.")
public class DataExchangeController {

    private final DataExchangeService service;

    public DataExchangeController(DataExchangeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "Manually queue a data exchange record")
    @ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = DataExchangeResponse.class)))
    public ResponseEntity<DataExchangeResponse> create(
            @Valid @RequestBody CreateDataExchangeRequest request) {
        DataExchangeResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/data-exchange/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_READ')")
    @Operation(summary = "Fetch a data exchange record by ID")
    public DataExchangeResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_READ')")
    @Operation(
            summary = "List records for a company, optionally filtered by status (the Queue view)")
    public List<DataExchangeResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam(required = false) DataExchangeStatus status) {
        return service.list(tenantId, companyId, status);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_READ')")
    @Operation(summary = "Full status-transition history of a record")
    public List<DataExchangeHistoryResponse> history(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.historyOf(tenantId, id);
    }

    @PostMapping("/{id}/start-processing")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "PENDING/RETRY -> PROCESSING")
    public DataExchangeResponse startProcessing(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.startProcessing(tenantId, id);
    }

    @PostMapping("/{id}/mark-success")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "PROCESSING -> SUCCESS")
    public DataExchangeResponse markSuccess(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.markSuccess(tenantId, id);
    }

    @PostMapping("/{id}/mark-failed")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "PROCESSING -> FAILED, records error code/message")
    public DataExchangeResponse markFailed(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody MarkFailedRequest request) {
        return service.markFailed(tenantId, id, request);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "FAILED -> RETRY, increments retry count and schedules next attempt")
    public DataExchangeResponse retry(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.retry(tenantId, id);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "SUCCESS -> ACKNOWLEDGED")
    public DataExchangeResponse acknowledge(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.acknowledge(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('DATA_EXCHANGE_WRITE')")
    @Operation(summary = "Cancel a non-terminal record")
    public DataExchangeResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id);
    }
}
