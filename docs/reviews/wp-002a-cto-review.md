# WP-002A — Engineering Excellence: CTO review package

**Branch:** `claude/wp-002a-engineering-excellence`
**Base:** `claude/wp-001-final` (post-Final-Approval)
**Scope:** Cross-cutting engineering-excellence work; no new business capability.

## 1. What's in the scope

Ten items from the WP-002A brief:

| # | Item | Deliverable |
|---|------|-------------|
| 1 | Performance benchmark framework | `BenchmarkSupport` + `HotPathBenchmarks` (`@Tag("benchmark")`, excluded from default `mvn test`); `docs/operations/performance-benchmarks.md` |
| 2 | Security checklist | `docs/security/checklist.md` — one-page MUST/SHOULD gate for every PR touching auth, persistence, or public APIs |
| 3 | Global exception handling improvements | `GlobalExceptionHandler` reworked: structured `FieldViolation[]`, `sensitize()` scrubbing for password-like fields, new handlers for `ConstraintViolationException`, `MissingRequestHeaderException`, `HttpMediaTypeNotSupportedException`, `DataIntegrityViolationException` |
| 4 | Logging framework enhancement | `AccessLoggingFilter` (per-request line: method, path, status, duration_ms, user, ip); prod-profile JSON pattern in `logback-spring.xml`; extra MDC keys — no new dependency added |
| 5 | API response standardization | `ApiError` extended with `fieldErrors: List<FieldViolation>`; `FieldViolation(field, rejectedValue, message, code)` record; existing `ApiError.of(...)` factories preserved |
| 6 | Database indexing review | `docs/operations/database-indexing.md` (per-query index map); Flyway `V8__index_review.sql` adds 7 idempotent indexes on hot paths (login history, users lower-case lookups, refresh tokens, password history) |
| 7 | Flyway migration validation | `FlywayMigrationValidationTest` — sequential/no-gaps versions, clean fresh run against isolated schema; `docs/operations/flyway-migrations.md` documents the append-only rule |
| 8 | Test coverage improvements | New unit tests: `GlobalExceptionHandlerTest` (13), `CorrelationIdFilterTest` (3), `AuditorProviderTest` (4), `AuthRateLimitFilterTest` (4). Total unit test count is now **79 passing**. |
| 9 | SonarQube integration | `sonar-project.properties` at repo root; JaCoCo XML wired in; `docs/operations/sonarqube.md` (config + CI hook + no-lower-the-floor rule) |
| 10 | Docker optimization | Multi-stage BuildKit `Dockerfile`: cache-mounted Maven repo, layered-jar `COPY` layout, numeric non-root UID 10001, `tini` PID-1 signal handling, `MaxRAMPercentage` container-aware JVM; `.dockerignore` tightened; `docs/operations/docker.md` |

## 2. What is explicitly out of scope

- No new business modules. No HRMS work. No Person / Organization changes.
- No CI workflow rewrite. Sonar and benchmark integration are documented as opt-in additions to the existing pipeline; the actual GitHub Actions YAML change is deferred to whoever owns CI (they are one-line steps).
- No JMH-grade benchmark suite. `BenchmarkSupport` is directional-only; regressions are caught at order-of-magnitude bounds. See the doc for the rationale.
- No auto-remediation on the security checklist. It's a reviewer aid, not a scanner.

## 3. Files touched

**New (main):**
- `src/main/java/com/ewos/common/exception/FieldViolation.java`
- `src/main/java/com/ewos/common/web/AccessLoggingFilter.java`
- `src/main/resources/db/migration/V8__index_review.sql`

**Modified (main):**
- `src/main/java/com/ewos/common/exception/ApiError.java` — adds `fieldErrors`, `validation(...)` factory; existing factories preserved.
- `src/main/java/com/ewos/common/exception/GlobalExceptionHandler.java` — rewritten with structured `FieldViolation[]`, sensitive-field scrubbing, extra handlers. Existing handler signatures and status codes preserved.
- `src/main/resources/logback-spring.xml` — prod-profile JSON pattern, extra MDC keys.

**New (test):**
- `src/test/java/com/ewos/FlywayMigrationValidationTest.java`
- `src/test/java/com/ewos/benchmarks/BenchmarkSupport.java`
- `src/test/java/com/ewos/benchmarks/HotPathBenchmarks.java`
- `src/test/java/com/ewos/common/exception/GlobalExceptionHandlerTest.java`
- `src/test/java/com/ewos/common/persistence/AuditorProviderTest.java`
- `src/test/java/com/ewos/common/web/CorrelationIdFilterTest.java`
- `src/test/java/com/ewos/security/ratelimit/AuthRateLimitFilterTest.java`

**Modified (build):**
- `pom.xml` — Surefire `<excludedGroups>benchmark</excludedGroups>` so tagged benchmarks stay out of the default run.
- `Dockerfile` — multi-stage BuildKit + layered-jar rewrite (see item 10).
- `.dockerignore` — tightened.

**New (docs):**
- `docs/operations/database-indexing.md`
- `docs/operations/flyway-migrations.md`
- `docs/operations/performance-benchmarks.md`
- `docs/operations/sonarqube.md`
- `docs/operations/docker.md`
- `docs/security/checklist.md`
- `sonar-project.properties` (repo root)

## 4. Backwards compatibility

- **`ApiError` JSON shape** — additive only. Added `fieldErrors: []` (empty by default, `@JsonInclude(NON_NULL)`). Existing clients that never inspected `fieldErrors` are unaffected. Every existing factory (`of(...)`, `validation(...)`) still returns the same status/message/detail fields.
- **`GlobalExceptionHandler` HTTP status codes** — unchanged. Every existing handler still returns the same status; the new handlers cover cases that previously fell through to the generic 500 handler.
- **`Dockerfile`** — the produced image still exposes 8080, still runs as `ewos` (now UID 10001 instead of dynamic), still healthchecks against `/actuator/health/liveness`. Callers that hardcoded UID 0 or root shell will need to switch to a numeric SecurityContext.
- **`Flyway V8`** — all indexes use `IF NOT EXISTS`. Safe to run on any environment that already applied V1–V7; a no-op if the indexes already exist.

## 5. Verification

Ran in the sandbox (Docker not available for Testcontainers, so integration tests are skipped — same environmental limitation as WP-001 review):

```
mvn -B compile spotless:check checkstyle:check pmd:check   → SUCCESS
mvn -B spotbugs:check                                       → BUILD SUCCESS (0 bugs)
mvn -B test -Dtest='!*IntegrationTest,!EwosApplicationTests,!FlywayMigrationValidationTest'
                                                            → Tests run: 79, Failures: 0, Errors: 0
```

Sonar upload, Docker image build, and Testcontainers integration tests need an environment with Docker and Sonar credentials; the CI job that runs `mvn -B verify` on GHA is the authoritative gate.

## 6. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| `AccessLoggingFilter` adds one log line per request → volume increase | Filter is `@Order(LOWEST_PRECEDENCE - 100)`, single line, no payload. If ops sees noise they can raise the logger's level from `INFO` to `WARN` in `logback-spring.xml` without a code change. |
| Layered `Dockerfile` requires BuildKit | BuildKit is default in Docker 23+. The `# syntax=docker/dockerfile:1.7` header enables it explicitly on older runners. |
| Numeric UID 10001 may collide with an existing host user in bind-mount setups | Development uses volumes, not bind mounts. Documented in `docs/operations/docker.md`. |
| V8 index creation on very large tables may be slow | All indexes use `IF NOT EXISTS`; none use `CONCURRENTLY` because Flyway wraps migrations in a transaction. If a table becomes large enough that lock time matters, that migration becomes an ops procedure — noted in `docs/operations/database-indexing.md`. Current dataset is a fresh install. |
| Benchmark thresholds may flake on slow CI | Sanity bounds are order-of-magnitude only; the JWT test asserts <5ms mean (typical is ~50µs). |

## 7. Approval requested

- Merge branch `claude/wp-002a-engineering-excellence` into `main`.
- Confirm the Sonar and benchmark CI wiring is deferred to the CI owner rather than added here.
- Confirm no follow-up items from WP-002A land on the WP-002B backlog before final sign-off.
