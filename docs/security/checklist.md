# EWOS security checklist

This is the checklist reviewers run against every PR that touches auth, persistence, or public
APIs. It is intentionally short: one page, no wall of theory. If an item does not apply, say why in
the PR description; do not silently skip.

Legend: **[MUST]** = merge blocker. **[SHOULD]** = discuss in review, exceptions require a written
rationale.

## 1. Authentication & session

- **[MUST]** No new endpoint added to Spring Security's `permitAll()` list without a written note in
  the PR. Public endpoints go through `SecurityConfig` explicitly, not by accident.
- **[MUST]** No storing raw passwords. Any new place that receives a password (bootstrap, reset,
  history) uses `PasswordEncoder`.
- **[MUST]** JWT secret is read from `app.security.jwt.secret` (env or Spring config), never
  inlined. `JwtSecretGuard` refuses the placeholder in the `prod` profile — do not weaken it.
- **[SHOULD]** New tokens (refresh, invite, verification) get a TTL and a server-side revocation
  path. No "valid forever" tokens.

## 2. Authorization

- **[MUST]** Controller methods on protected resources declare `@PreAuthorize(...)` with a
  permission literal (e.g. `hasAuthority('USER_READ')`) or an equivalent method-security check.
- **[MUST]** No role-string hardcoded in business logic. Permissions come from the seed data in
  `V*__*.sql`; if you need a new permission, add it to the seed migration.
- **[SHOULD]** Multi-tenant queries filter by `tenant_id` at the repository level, not only in the
  service layer. Cross-tenant leakage is a persistence bug.

## 3. Input validation & error surfaces

- **[MUST]** Every request DTO uses `jakarta.validation.constraints.*` annotations. `@Valid`
  is present on the controller parameter.
- **[MUST]** No user-supplied string is concatenated into JPQL/SQL. Use parameterised queries and
  Specifications.
- **[MUST]** `GlobalExceptionHandler` catches new exception types you introduce. Never let a
  stacktrace leak in the response body; `ApiError` is the only shape.
- **[MUST]** Rejected values for sensitive fields (`password*`, `secret`, `token`) are scrubbed by
  `sensitize()` in `GlobalExceptionHandler`. If you add a new sensitive field name, add it to
  `SENSITIVE_FIELDS`.

## 4. Persistence

- **[MUST]** New tables have `created_at`, `updated_at`, `created_by`, `updated_by` (or extend
  `AuditableEntity`) unless there's a documented reason not to.
- **[MUST]** Soft-deleted entities use `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` and
  the accompanying partial unique indexes on any natural key.
- **[MUST]** Optimistic locking (`@Version`) is present on any entity a user can mutate. Conflict
  → 409 via `GlobalExceptionHandler.handleOptimisticLock`.
- **[MUST]** Flyway migrations are append-only. Never edit a released `V*__*.sql`. See
  `docs/operations/flyway-migrations.md`.

## 5. Secrets & config

- **[MUST]** No secret material in `application*.yml` in the repo. Config-property records use
  `@ConfigurationProperties`; values come from env in real environments.
- **[MUST]** `.env`, `.p12`, `.pem`, `.key` files are covered by `.gitignore`. Reviewers spot-check
  new file names in `git status` before merge.
- **[SHOULD]** New third-party client libraries pin a version in `pom.xml` (no `LATEST`, no
  `RELEASE`).

## 6. Web

- **[MUST]** New endpoints under `/api/auth/**` are on the rate-limited path list in
  `AuthRateLimitFilter`. If you deliberately want to bypass, note it in the PR.
- **[MUST]** CORS allowed-origins list in `CorsProperties` is empty for the `prod` profile by
  default. Any addition is reviewed by a maintainer.
- **[SHOULD]** New endpoints that return large collections support pagination and don't return
  unbounded results.

## 7. Logging & observability

- **[MUST]** No password, token, secret, or PII in log lines. Never `log.info("payload={}", dto)`
  on anything containing credentials.
- **[MUST]** Every request has a correlation ID (`CorrelationIdFilter`); don't strip it.
- **[SHOULD]** New Actuator endpoints are locked down to authenticated internal callers only.

## 8. Dependencies

- **[MUST]** No new dependency added without a note in the PR describing what it is for. Prefer
  what's already on the classpath (Spring, Jackson, JJWT, JUnit).
- **[SHOULD]** If you bump a security-sensitive dependency (Spring, JJWT, Postgres driver), call it
  out in the PR body.

## 9. Tests

- **[MUST]** New auth-adjacent code has a unit test that exercises at least one negative case
  (denied, expired, tampered).
- **[MUST]** Integration tests use the singleton Testcontainers `POSTGRES` from
  `AbstractIntegrationTest`. Do not start ad-hoc containers.

## 10. Reviewer sign-off

Reviewer confirms in the PR description:

- [ ] I ran `mvn -B verify` locally or in CI and it is green.
- [ ] I read every migration in the PR line by line.
- [ ] I confirmed no new endpoint bypasses auth by accident.
- [ ] I confirmed no secret material is in the diff.
