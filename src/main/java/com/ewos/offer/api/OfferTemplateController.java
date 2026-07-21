package com.ewos.offer.api;

import com.ewos.offer.api.dto.CreateOfferTemplateRequest;
import com.ewos.offer.api.dto.OfferTemplateResponse;
import com.ewos.offer.application.OfferTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/offers/templates")
@Tag(name = "Offer Templates", description = "Reusable offer-letter templates")
public class OfferTemplateController {

    private final OfferTemplateService templates;

    public OfferTemplateController(OfferTemplateService templates) {
        this.templates = templates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Create an offer template")
    public OfferTemplateResponse create(@Valid @RequestBody CreateOfferTemplateRequest req) {
        return templates.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "Fetch an offer template by ID")
    public OfferTemplateResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return templates.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "List offer templates for a company")
    public List<OfferTemplateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return templates.listForCompany(tenantId, companyId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OFFER_ADMIN')")
    @Operation(summary = "Soft-delete an offer template")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        templates.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
