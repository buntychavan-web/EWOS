package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateCommunicationResponse;
import com.ewos.ats.api.dto.CandidateDocumentResponse;
import com.ewos.ats.api.dto.CandidateNoteResponse;
import com.ewos.ats.api.dto.CandidateResponse;
import com.ewos.ats.api.dto.CandidateResumeResponse;
import com.ewos.ats.api.dto.CandidateTagResponse;
import com.ewos.ats.api.dto.CandidateTimelineEventResponse;
import com.ewos.ats.api.dto.DuplicateCandidateMatchResponse;
import com.ewos.ats.api.dto.JobApplicationResponse;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateCommunication;
import com.ewos.ats.domain.CandidateDocument;
import com.ewos.ats.domain.CandidateNote;
import com.ewos.ats.domain.CandidateResume;
import com.ewos.ats.domain.CandidateTag;
import com.ewos.ats.domain.CandidateTimelineEvent;
import com.ewos.ats.domain.DuplicateCandidateMatch;
import com.ewos.ats.domain.JobApplication;
import com.ewos.employee.domain.Employee;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reflection-free ATS mapper: domain → response DTO. */
@Component
public class AtsMapper {

    public CandidateResponse toResponse(Candidate c) {
        return new CandidateResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCandidateNumber(),
                c.getFirstName(),
                c.getMiddleName(),
                c.getLastName(),
                c.getEmail(),
                c.getPhone(),
                c.getDateOfBirth(),
                c.getGender(),
                c.getNationality(),
                c.getCurrentLocation(),
                c.getCountry(),
                c.getCurrentEmployer(),
                c.getCurrentDesignation(),
                c.getTotalExperienceMonths(),
                c.getCurrentCtcCurrency(),
                c.getCurrentCtcAmount(),
                c.getExpectedCtcCurrency(),
                c.getExpectedCtcAmount(),
                c.getNoticePeriodDays(),
                c.getSource(),
                c.getSourceDetails(),
                employeeId(c.getReferrerEmployee()),
                c.isInternal(),
                employeeId(c.getInternalEmployee()),
                c.getStatus(),
                c.getLinkedinUrl(),
                c.getGithubUrl(),
                c.getPortfolioUrl(),
                c.getSummary(),
                c.getVersionNo());
    }

    public CandidateResumeResponse toResponse(CandidateResume r) {
        return new CandidateResumeResponse(
                r.getId(),
                r.getCandidate() == null ? null : r.getCandidate().getId(),
                r.getFilename(),
                r.getMimeType(),
                r.getSizeBytes(),
                r.getStorageUri(),
                r.isPrimary(),
                r.isParsed(),
                r.getParsedAt(),
                r.getParserVersion(),
                r.getUploadedAt(),
                r.getVersionNo());
    }

    public CandidateDocumentResponse toResponse(CandidateDocument d) {
        return new CandidateDocumentResponse(
                d.getId(),
                d.getCandidate() == null ? null : d.getCandidate().getId(),
                d.getDocumentType(),
                d.getFilename(),
                d.getMimeType(),
                d.getSizeBytes(),
                d.getStorageUri(),
                d.getNotes(),
                d.getUploadedAt(),
                d.getVersionNo());
    }

    public CandidateTagResponse toResponse(CandidateTag t) {
        return new CandidateTagResponse(
                t.getId(), t.getCandidate() == null ? null : t.getCandidate().getId(), t.getTag());
    }

    public CandidateNoteResponse toResponse(CandidateNote n) {
        return new CandidateNoteResponse(
                n.getId(),
                n.getCandidate() == null ? null : n.getCandidate().getId(),
                n.getNoteType(),
                n.getBody(),
                n.isPrivateNote(),
                n.getCreatedBy(),
                n.getCreatedAt(),
                n.getVersionNo());
    }

    public CandidateTimelineEventResponse toResponse(CandidateTimelineEvent e) {
        return new CandidateTimelineEventResponse(
                e.getId(),
                e.getCandidate() == null ? null : e.getCandidate().getId(),
                e.getApplicationId(),
                e.getEventType(),
                e.getEventSummary(),
                e.getEventData(),
                e.getActorId(),
                e.getOccurredAt());
    }

    public CandidateCommunicationResponse toResponse(CandidateCommunication c) {
        return new CandidateCommunicationResponse(
                c.getId(),
                c.getCandidate() == null ? null : c.getCandidate().getId(),
                c.getApplicationId(),
                c.getChannel(),
                c.getDirection(),
                c.getSubject(),
                c.getBodySummary(),
                c.getExternalRef(),
                c.getOccurredAt(),
                c.getSentBy(),
                c.getVersionNo());
    }

    public JobApplicationResponse toResponse(JobApplication a) {
        return new JobApplicationResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getApplicationNumber(),
                a.getCandidate() == null ? null : a.getCandidate().getId(),
                a.getJobRequisition() == null ? null : a.getJobRequisition().getId(),
                a.getResume() == null ? null : a.getResume().getId(),
                a.getSource(),
                a.getSourceDetails(),
                employeeId(a.getReferredByEmployee()),
                a.getStatus(),
                a.getWorkflowInstanceId(),
                a.getAppliedAt(),
                a.getScreenedAt(),
                a.getDecidedAt(),
                a.getDecidedBy(),
                a.getDecisionNotes(),
                a.getRejectionReason(),
                a.getVersionNo());
    }

    public DuplicateCandidateMatchResponse toResponse(DuplicateCandidateMatch m) {
        return new DuplicateCandidateMatchResponse(
                m.candidateId(), m.candidateNumber(), m.fullName(), m.matchType());
    }

    private static UUID employeeId(Employee e) {
        return e == null ? null : e.getId();
    }
}
