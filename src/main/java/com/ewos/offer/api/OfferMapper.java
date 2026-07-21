package com.ewos.offer.api;

import com.ewos.offer.api.dto.OfferNegotiationResponse;
import com.ewos.offer.api.dto.OfferResponse;
import com.ewos.offer.api.dto.OfferTemplateResponse;
import com.ewos.offer.api.dto.PreboardingChecklistResponse;
import com.ewos.offer.api.dto.PreboardingTaskInstanceResponse;
import com.ewos.offer.api.dto.PreboardingTaskTemplateResponse;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNegotiation;
import com.ewos.offer.domain.OfferTemplate;
import com.ewos.offer.domain.preboarding.PreboardingChecklist;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import com.ewos.organization.domain.OrganizationUnit;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reflection-free mapper: domain → response DTO. */
@Component
public class OfferMapper {

    public OfferTemplateResponse toResponse(OfferTemplate t) {
        return new OfferTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getBodyTemplate(),
                t.getDefaultCurrency(),
                t.getDefaultNoticePeriodDays(),
                t.getDefaultProbationDays(),
                t.getDefaultExpiryDays(),
                t.isActive(),
                t.getVersionNo());
    }

    public OfferResponse toResponse(Offer o) {
        return new OfferResponse(
                o.getId(),
                o.getTenantId(),
                o.getCompanyId(),
                o.getOfferNumber(),
                o.getApplication() == null ? null : o.getApplication().getId(),
                o.getCandidate() == null ? null : o.getCandidate().getId(),
                o.getJobRequisition() == null ? null : o.getJobRequisition().getId(),
                o.getTemplate() == null ? null : o.getTemplate().getId(),
                o.getVersion(),
                o.getPreviousOfferId(),
                o.getDesignation(),
                idOf(o.getDepartmentOrgUnit()),
                o.getLocation(),
                o.getEmploymentType(),
                o.getTargetJoiningDate(),
                o.getCurrency(),
                o.getBaseSalary(),
                o.getVariablePay(),
                o.getOneTimeBonus(),
                o.getHiringBonus(),
                o.getRetentionBonus(),
                o.getTotalCtc(),
                o.getNoticePeriodDays(),
                o.getProbationDays(),
                o.getOfferDocumentUri(),
                o.getStatus(),
                o.getApprovalWorkflowInstanceId(),
                o.getSubmittedAt(),
                o.getApprovedAt(),
                o.getExtendedAt(),
                o.getExpiresAt(),
                o.getAcceptedAt(),
                o.getDeclinedAt(),
                o.getDeclineReason(),
                o.getRevisedAt(),
                o.getWithdrawnAt(),
                o.getWithdrawnReason(),
                o.getVersionNo());
    }

    public OfferNegotiationResponse toResponse(OfferNegotiation n) {
        return new OfferNegotiationResponse(
                n.getId(),
                n.getOffer() == null ? null : n.getOffer().getId(),
                n.getProposedBy(),
                n.getProposedChangesJson(),
                n.getNotes(),
                n.getSubmittedAt(),
                n.getRespondedAt(),
                n.getAccepted(),
                n.getResultingOfferId());
    }

    public PreboardingTaskTemplateResponse toResponse(PreboardingTaskTemplate t) {
        return new PreboardingTaskTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getTaskType(),
                t.getSortOrder(),
                t.isMandatory(),
                t.getDefaultOwner(),
                t.getDefaultSlaDays(),
                t.isActive(),
                t.getVersionNo());
    }

    public PreboardingChecklistResponse toResponse(PreboardingChecklist c) {
        return new PreboardingChecklistResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getOffer() == null ? null : c.getOffer().getId(),
                c.getApplication() == null ? null : c.getApplication().getId(),
                c.getCandidate() == null ? null : c.getCandidate().getId(),
                c.getJoiningDate(),
                c.getStatus(),
                c.getCompletionPercent(),
                c.getJoiningConfirmedAt(),
                c.getJoiningConfirmedBy(),
                c.getEmployee() == null ? null : c.getEmployee().getId(),
                c.getNotes(),
                c.getVersionNo());
    }

    public PreboardingTaskInstanceResponse toResponse(PreboardingTaskInstance t) {
        return new PreboardingTaskInstanceResponse(
                t.getId(),
                t.getChecklist() == null ? null : t.getChecklist().getId(),
                t.getTemplate() == null ? null : t.getTemplate().getId(),
                t.getName(),
                t.getTaskType(),
                t.getOwner(),
                t.getAssignedEmployee() == null ? null : t.getAssignedEmployee().getId(),
                t.isMandatory(),
                t.getSortOrder(),
                t.getStatus(),
                t.getDueDate(),
                t.getStartedAt(),
                t.getCompletedAt(),
                t.getCompletedBy(),
                t.getExternalRef(),
                t.getResultJson(),
                t.getNotes(),
                t.getVersionNo());
    }

    private static UUID idOf(OrganizationUnit u) {
        return u == null ? null : u.getId();
    }
}
