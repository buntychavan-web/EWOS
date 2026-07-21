package com.ewos.ats.api.dto;

import com.ewos.ats.domain.ApplicationStatus;
import com.ewos.ats.domain.CandidateSource;
import com.ewos.ats.domain.RejectionReason;
import java.time.Instant;
import java.util.UUID;

public record JobApplicationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String applicationNumber,
        UUID candidateId,
        UUID jobRequisitionId,
        UUID resumeId,
        CandidateSource source,
        String sourceDetails,
        UUID referredByEmployeeId,
        ApplicationStatus status,
        UUID workflowInstanceId,
        Instant appliedAt,
        Instant screenedAt,
        Instant decidedAt,
        UUID decidedBy,
        String decisionNotes,
        RejectionReason rejectionReason,
        long versionNo) {}
