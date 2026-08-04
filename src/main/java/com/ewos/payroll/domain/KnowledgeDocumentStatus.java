package com.ewos.payroll.domain;

/** Lifecycle state of one {@link KnowledgeDocument} version. */
public enum KnowledgeDocumentStatus {
    /** Uploaded but not yet reviewed/effective. */
    DRAFT,
    /** The current effective version for its document family. */
    PUBLISHED,
    /** Replaced by a newer version in the same family; kept for history. */
    SUPERSEDED,
    /** No longer relevant (e.g. a repealed circular) but retained for record. */
    ARCHIVED
}
