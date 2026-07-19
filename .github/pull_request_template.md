<!--
  Read CONTRIBUTING.md before opening a PR. Every checkbox below must be
  filled honestly — a blank checklist tells the reviewer to bounce it back.
  Delete guidance comments (like this one) before publishing if you like.
-->

## Summary

<!-- What does this PR change and why? 2–4 sentences, no bullets. -->

## Type of change

<!-- Tick exactly one. -->

- [ ] Bug fix
- [ ] Feature (within the current sprint scope)
- [ ] Refactor (no behavior change)
- [ ] Docs / process
- [ ] Infrastructure / CI
- [ ] Migration / schema change

## Linked issue / spec

<!-- e.g. "closes #123", "implements Sprint 6 § 2.3", or "N/A". -->

---

## Author checklist

**Do not open this PR for review until every applicable box is ticked.** Untick and leave a note next to any that legitimately don't apply.

### Code

- [ ] Package layout follows `com.ewos.<context>.{api,application,domain,infrastructure}`
- [ ] Constructor injection only (no `@Autowired` on fields)
- [ ] Every new entity extends `AuditableEntity`
- [ ] Every mutating endpoint carries `@PreAuthorize` with an explicit authority
- [ ] Errors are surfaced via `ApiError` / `GlobalExceptionHandler`, not manual JSON
- [ ] No secrets, tokens, credentials, or `.env` files added
- [ ] No new external dependencies without a justification in the PR description

### Migrations (only if this PR touches the schema)

- [ ] New file follows `V<next>__<snake_case>.sql`
- [ ] Did **not** edit, rename, or delete any previously merged migration
- [ ] Soft-deletable tables use partial unique indexes (`WHERE deleted_at IS NULL`)
- [ ] Includes `created_at` / `updated_at` / `created_by` / `updated_by` / `version` on any new business table

### Tests

- [ ] Unit tests added / updated for changed logic (`*Test.java`)
- [ ] Integration tests extend `AbstractIntegrationTest` — **no** `@Testcontainers` or `@Container` in the test class ([why](../blob/main/CONTRIBUTING.md#64-testcontainers-best-practices--read-this))
- [ ] Tests use unique identifiers; do not assume an empty database
- [ ] `mvn verify` passes locally with Docker running

### Quality gates (all six must be green locally before requesting review)

- [ ] `mvn spotless:check`
- [ ] `mvn checkstyle:check`
- [ ] `mvn pmd:check`
- [ ] `mvn compile spotbugs:check`
- [ ] `mvn test`
- [ ] Coverage still ≥ 80 % (JaCoCo `check` inside `mvn verify`)

### Docs

- [ ] `PROJECT_STATUS.md` updated if delivered scope or tech debt shifted
- [ ] `README.md` updated if user-visible behavior, endpoints, or env vars changed
- [ ] `CONTRIBUTING.md` updated if the development / review process changed
- [ ] OpenAPI annotations reflect the new/changed endpoints

### Scope guardrails

- [ ] This PR does **not** add Employee, Payroll, Leave, Attendance, or Organization modules
- [ ] This PR does **not** disable or loosen any quality gate

---

## Test plan

<!--
  What did you actually run to convince yourself this works? Local commands,
  Swagger UI actions, curl invocations, screenshots. If the PR fixes a bug,
  reproduce the bug on `main` first, then show it fixed on this branch.
-->

## Rollback plan

<!--
  If this ships to production and breaks, what's the fastest safe revert?
  For schema changes, is the migration reversible or do we need a data-fix?
-->

## Reviewer notes (optional)

<!-- Anything the reviewer should look at first. Known gotchas. Alternatives you considered and rejected. -->
