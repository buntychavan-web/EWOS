package com.ewos.exit.api;

import com.ewos.exit.api.dto.AlumniResponse;
import com.ewos.exit.api.dto.ClearanceResponse;
import com.ewos.exit.api.dto.DocumentResponse;
import com.ewos.exit.api.dto.InterviewResponse;
import com.ewos.exit.api.dto.KtItemResponse;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.domain.AlumniRecord;
import com.ewos.exit.domain.ExitClearance;
import com.ewos.exit.domain.ExitDocument;
import com.ewos.exit.domain.ExitInterview;
import com.ewos.exit.domain.KnowledgeTransferItem;
import com.ewos.exit.domain.Resignation;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for exit + alumni entities. */
@Component
public class ExitMapper {

    public ResignationResponse toResponse(Resignation r) {
        return new ResignationResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getEmployee() == null ? null : r.getEmployee().getId(),
                r.getSubmittedAt(),
                r.getSubmittedBy(),
                r.getIntendedLastDay(),
                r.getReason(),
                r.getNoticePeriodDays(),
                r.getNoticeStartDate(),
                r.getNoticeEndDate(),
                r.getBuyoutDays(),
                r.getBuyoutAmount(),
                r.getAcceptedAt(),
                r.getAcceptedBy(),
                r.getExitWorkflowInstanceId(),
                r.getStatus(),
                r.getActualLastDay(),
                r.getRehireEligibility(),
                r.getRehireNotes());
    }

    public ClearanceResponse toResponse(ExitClearance c) {
        return new ClearanceResponse(
                c.getId(),
                c.getTenantId(),
                c.getResignation() == null ? null : c.getResignation().getId(),
                c.getDepartment(),
                c.getOwnerEmployeeId(),
                c.getStatus(),
                c.getClearedAt(),
                c.getClearedBy(),
                c.getNotes());
    }

    public KtItemResponse toResponse(KnowledgeTransferItem k) {
        return new KtItemResponse(
                k.getId(),
                k.getTenantId(),
                k.getResignation() == null ? null : k.getResignation().getId(),
                k.getTopic(),
                k.getDescription(),
                k.getTransferredTo(),
                k.isCompleted(),
                k.getCompletedAt(),
                k.getCompletedBy(),
                k.getNotes());
    }

    public InterviewResponse toResponse(ExitInterview i) {
        return new InterviewResponse(
                i.getId(),
                i.getTenantId(),
                i.getResignation() == null ? null : i.getResignation().getId(),
                i.getConductedAt(),
                i.getConductedBy(),
                i.getInterviewerName(),
                i.getRating(),
                i.getWouldRecommend(),
                i.getResponsesJson(),
                i.getComments());
    }

    public DocumentResponse toResponse(ExitDocument d) {
        return new DocumentResponse(
                d.getId(),
                d.getTenantId(),
                d.getResignation() == null ? null : d.getResignation().getId(),
                d.getDocumentType(),
                d.getDocumentUri(),
                d.getIssuedAt(),
                d.getIssuedBy(),
                d.getReferenceNumber(),
                d.getNotes());
    }

    public AlumniResponse toResponse(AlumniRecord a) {
        return new AlumniResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getEmployee() == null ? null : a.getEmployee().getId(),
                a.getResignation() == null ? null : a.getResignation().getId(),
                a.getExitedOn(),
                a.getAlumniEmail(),
                a.getLinkedinUrl(),
                a.getCurrentEmployer(),
                a.isStayInTouch(),
                a.getRehireEligibility(),
                a.getNotes());
    }
}
