package com.ewos.ats.domain;

import com.ewos.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** Note attached to a candidate. Private notes are only visible to the author's role peers. */
@Entity
@Table(name = "candidate_notes")
@SQLDelete(sql = "UPDATE candidate_notes SET deleted_at = NOW() WHERE id = ? AND version_no = ?")
@SQLRestriction("deleted_at IS NULL")
public class CandidateNote extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, updatable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 32)
    private NoteType noteType = NoteType.GENERAL;

    @Column(name = "body", nullable = false, length = 8000)
    private String body;

    @Column(name = "is_private", nullable = false)
    private boolean privateNote;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version_no", nullable = false)
    private long versionNo;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID v) {
        this.tenantId = v;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate v) {
        this.candidate = v;
    }

    public NoteType getNoteType() {
        return noteType;
    }

    public void setNoteType(NoteType v) {
        this.noteType = v;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String v) {
        this.body = v;
    }

    public boolean isPrivateNote() {
        return privateNote;
    }

    public void setPrivateNote(boolean v) {
        this.privateNote = v;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersionNo() {
        return versionNo;
    }
}
