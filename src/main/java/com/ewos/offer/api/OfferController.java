package com.ewos.offer.api;

import com.ewos.offer.api.dto.AcceptOfferRequest;
import com.ewos.offer.api.dto.CreateOfferRequest;
import com.ewos.offer.api.dto.DeclineOfferRequest;
import com.ewos.offer.api.dto.ExtendOfferRequest;
import com.ewos.offer.api.dto.LogNegotiationRequest;
import com.ewos.offer.api.dto.OfferDecisionRequest;
import com.ewos.offer.api.dto.OfferNegotiationResponse;
import com.ewos.offer.api.dto.OfferResponse;
import com.ewos.offer.api.dto.SubmitOfferRequest;
import com.ewos.offer.api.dto.UpdateOfferRequest;
import com.ewos.offer.api.dto.WithdrawOfferRequest;
import com.ewos.offer.application.OfferService;
import com.ewos.offer.domain.OfferStatus;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/offers")
@Tag(
        name = "Offers",
        description =
                "Offer lifecycle: draft, submit for approval, extend, accept / decline / revise /"
                        + " withdraw. Subject-type 'offer.approval'.")
public class OfferController {

    private final OfferService offers;

    public OfferController(OfferService offers) {
        this.offers = offers;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Draft a new offer")
    public ResponseEntity<OfferResponse> create(@Valid @RequestBody CreateOfferRequest req) {
        OfferResponse created = offers.create(req);
        return ResponseEntity.created(URI.create("/api/v1/offers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Update a DRAFT offer")
    public OfferResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOfferRequest req) {
        return offers.update(tenantId, id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Submit a DRAFT offer for approval — starts a workflow instance")
    public OfferResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitOfferRequest req) {
        return offers.submitForApproval(tenantId, id, req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('OFFER_APPROVE')")
    @Operation(summary = "Approve a PENDING_APPROVAL offer")
    public OfferResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) OfferDecisionRequest req) {
        return offers.approve(tenantId, id, req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('OFFER_APPROVE')")
    @Operation(summary = "Reject a PENDING_APPROVAL offer")
    public OfferResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) OfferDecisionRequest req) {
        return offers.reject(tenantId, id, req);
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Extend an APPROVED offer to the candidate")
    public OfferResponse extend(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ExtendOfferRequest req) {
        return offers.extend(tenantId, id, req);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('OFFER_ACCEPT')")
    @Operation(summary = "Record candidate acceptance (digital signature)")
    public OfferResponse accept(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AcceptOfferRequest req) {
        return offers.accept(tenantId, id, req);
    }

    @PostMapping("/{id}/decline")
    @PreAuthorize("hasAuthority('OFFER_ACCEPT')")
    @Operation(summary = "Record candidate decline")
    public OfferResponse decline(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody DeclineOfferRequest req) {
        return offers.decline(tenantId, id, req);
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Create a revised offer replacing the given one")
    public OfferResponse revise(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateOfferRequest req) {
        return offers.revise(tenantId, id, req);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Withdraw a non-terminal offer")
    public OfferResponse withdraw(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawOfferRequest req) {
        return offers.withdraw(tenantId, id, req);
    }

    @PostMapping("/{id}/expire")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Mark an EXTENDED offer past expires_at as EXPIRED")
    public OfferResponse markExpired(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return offers.markExpired(tenantId, id);
    }

    @PostMapping("/{id}/negotiations")
    @PreAuthorize("hasAuthority('OFFER_WRITE')")
    @Operation(summary = "Log an offer negotiation round")
    public OfferNegotiationResponse logNegotiation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody LogNegotiationRequest req) {
        return offers.logNegotiation(tenantId, id, req);
    }

    @GetMapping("/{id}/negotiations")
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "List negotiation rounds for an offer")
    public List<OfferNegotiationResponse> listNegotiations(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return offers.negotiationsFor(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "Fetch an offer by ID")
    public OfferResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return offers.getById(tenantId, id);
    }

    @GetMapping("/by-application/{applicationId}")
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "List every version of the offer for an application")
    public List<OfferResponse> forApplication(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID applicationId) {
        return offers.forApplication(tenantId, applicationId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OFFER_READ')")
    @Operation(summary = "List offers for a company by status")
    public List<OfferResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam OfferStatus status) {
        return offers.byStatus(tenantId, companyId, status);
    }
}
