# Knowledge Centre — backend foundation (Sprint 24K item 7)

This is the **foundation** for a future Knowledge Centre feature — not the Knowledge Centre
itself. It gives future work a place to ingest, version, and retrieve statutory source documents
and company payroll policies. Nothing described as "future" below is built yet.

## What exists today

- `KnowledgeDocument` (`src/main/java/com/ewos/payroll/domain/KnowledgeDocument.java`) — metadata +
  a `storageUri` for one versioned document. Content is never stored inline, consistent with this
  codebase's document-metadata pattern (`CandidateDocument`, `ExitDocument`,
  `TaxDeclarationProof`).
- **Version history**: every edit is a new row sharing `documentFamilyId`, with an incremented
  `versionNumber`. Publishing a new version moves the previously `PUBLISHED` row to `SUPERSEDED` —
  it is never mutated or deleted. `KnowledgeDocumentService.versionHistory()` returns the full
  chain.
- **Effective dates**: `effectiveFrom`/`effectiveTo` plus `KnowledgeDocumentService.effectiveAsOf()`
  answer "what was in force on date X," not just "what's current."
- **Search**: `KnowledgeDocumentService.search()` is a plain SQL `ILIKE` substring match over
  title/summary/tags. This is genuinely useful today (an admin looking for "ESI notification
  2024") but is not semantic search.
- **Source types**: `KnowledgeSourceType` covers Income Tax Act, CBDT circular, EPFO circular, ESIC
  circular, PT notification, LWF notification, company policy, and a catch-all `OTHER`.

## What is explicitly NOT built (future work)

- **Ingestion pipeline.** No crawler, no upload UI, no PDF/text extraction. Rows are created one
  at a time via the API (`POST /api/v1/payroll/knowledge-documents`); a real Knowledge Centre would
  need a way to actually get documents in, likely alongside file storage the same way
  `TaxDeclarationProof` uploads are handled.
- **AI retrieval.** No embeddings, no vector index, no LLM call anywhere in this codebase. When a
  retrieval feature is built, the natural integration point is: resolve candidate documents via
  `KnowledgeDocumentService.search()`/`effectiveAsOf()` first (deterministic, auditable), then hand
  those specific documents to a retrieval/LLM layer as grounding context — never let a model
  freelance an answer without a citable `KnowledgeDocument` behind it. This mirrors the same
  "rule-based today, LLM-groundable later" pattern used by `PayrollInsightProvider`.
- **Full-text/semantic indexing.** The `ILIKE` search does not stem, rank by relevance, or handle
  synonyms. A production Knowledge Centre would likely want a dedicated search index (e.g.
  PostgreSQL full-text search columns, or an external search engine) once document volume justifies
  it.
- **Access nuance beyond company scoping.** `companyId = null` currently means "applies tenant-wide"
  (the natural default for a statutory source). There is no finer-grained visibility model (e.g.
  "only HR admins can see this policy draft") beyond the existing `PAYROLL_CONFIG`/`PAYROLL_READ`
  authorities.

## Why this shape

The mandatory Sprint 24K domain enhancements (§8.1 LTA blocks, §8.2 prorated recovery, §8.3 tax on
variable payments) all had to make judgment calls about which statutory facts are stable, long-
established law versus a specific notification that needs confirmation (see
`docs/business-rules/payroll-domain-enhancements.md`). A Knowledge Centre is the natural long-term
home for those confirmations — a dated, versioned, citable record of "here is the circular that
confirms this rule" instead of a comment in code. This foundation exists so that future work has
somewhere to put that record without a schema migration first.
