# EWOS Project Health Report

**As of:** 2026-07-19
**Branch under review:** `claude/wp-003-foundation`
**Baseline architecture:** `docs/architecture/EWOS_MASTER_ARCHITECTURE_v1.0.md` (frozen v1.0)

## 1. Scorecard

| Dimension | Score | Notes |
|---|---|---|
| **Repository** | 8 / 10 | Single Maven module; charter calls for multi-module. Package layout matches charter after WP-003. |
| **Architecture** | 8 / 10 | DDD + Hexagonal + Clean layout in place inside `identity`. Layout template published for all future modules. |
| **Code Quality** | 9 / 10 | Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs 4.8 — all pass with 0 findings. |
| **Security** | 7 / 10 | JWT prod guard, rate limit, account lockout, security checklist doc live. Kafka/Redis IAM, secret rotation, dependency scanning not yet automated. |
| **Performance** | 7 / 10 | Directional benchmark framework in place; JaCoCo 80% BUNDLE floor enforced. No load/soak tests yet. |
| **Technical Debt** | 8 / 10 | Rejected Sprint 6/7/8.1 branches contain unshipped Company/Organization/Person code — must be rebuilt to charter, not cherry-picked. |
| **Build Status** | 10 / 10 | `mvn -B compile spotless:check checkstyle:check pmd:check spotbugs:check` → **BUILD SUCCESS**. Unit tests: 79 passing (integration tests require Docker; CI only). |

## 2. Stack conformance

| Charter req | Present | Notes |
|---|---|---|
| Java 21 | ✅ | `<maven.compiler.release>21` |
| Spring Boot 3.3 | ✅ | 3.3.5 |
| PostgreSQL 16 | ✅ | `docker-compose.yml` pins `postgres:16-alpine`; Testcontainers pulls same major. |
| Flyway | ✅ | 8 migrations (V1–V8), append-only, validated by `FlywayMigrationValidationTest`. |
| Redis | ✅ | Compose service present; `spring-boot-starter-data-redis` on classpath. |
| Kafka | ❌ | Not on classpath. Add when first Notification/Workflow feature ships. |
| Maven | ✅ | Single module — see debt below. |
| Docker | ✅ | Multi-stage BuildKit, layered jar, numeric non-root UID, tini PID-1. |
| Docker Compose | ✅ | postgres + redis + app services. |
| Kubernetes | ❌ | No manifests / Helm chart yet. |
| GitHub Actions | ✅ | `.github/workflows/ci.yml` runs Spotless/Checkstyle/PMD/SpotBugs + verify with coverage gate. |
| Testcontainers | ✅ | Singleton pattern in `AbstractIntegrationTest`. |

## 3. Package layout after WP-003

```
com.ewos
├── EwosApplication
├── shared/                 # cross-cutting kernel (exception, persistence, purge, web, config)
├── identity/               # IN USE — users, roles, permissions, auth
├── organization/           # RESERVED
├── employee/               # RESERVED (Core HR)
├── attendance/             # RESERVED
├── leave/                  # RESERVED
├── payroll/                # RESERVED
├── recruitment/            # RESERVED
├── talent/                 # RESERVED (learning + performance consolidated per master arch)
├── analytics/              # RESERVED
├── integration/            # RESERVED
├── notification/           # RESERVED
├── workflow/               # RESERVED
├── governance/             # RESERVED
└── ai/                     # RESERVED
```

Every module (whether IN USE or RESERVED) carries a `package-info.java` describing the target internal layout: `.api`, `.application`, `.domain`, `.infrastructure`.

## 4. Missing modules

Every module besides `identity` is a stub. Delivery order per master arch:

1. Platform (shared + identity) — **in progress**
2. Core HR (organization + employee)
3. Attendance
4. Leave
5. Payroll
6. Recruitment
7. Talent
8. Analytics
9. AI Platform

## 5. Missing packages / adapters

- **Kafka producer/consumer** — no adapter, no configuration, no topic model.
- **OpenTelemetry** exporter — no OTel starter, no trace propagation wiring.
- **Kubernetes** — no Helm chart, no manifests, no HPA/PDB.
- **Argo CD** application spec — not present.
- **Terraform** infra — not in this repo (may live elsewhere).
- **Prometheus/Grafana/Loki/Tempo** dashboards — dashboards + alerts not yet defined.
- **CQRS** read models — no separate read-side infra yet (single JPA repo per aggregate today).
- **AI Platform** — no ai module scaffolding beyond package stub.

## 6. Missing standards / policies

- **Dependency scanning**: no Dependabot, no Snyk / OWASP Dependency-Check on the pipeline.
- **Secret scanning**: no automated pre-commit / CI scan (Gitleaks or GH secret scanning enrollment).
- **SBOM**: no SBOM emitted per release.
- **API contract testing**: no OpenAPI diff / contract tests on the PR pipeline.
- **Load / soak tests**: no k6 or Gatling suite; benchmark framework is unit-scoped only.
- **DR / backup runbook**: not authored.
- **Data classification catalogue**: not authored (needed before Payroll).

## 7. Technical debt log

| Item | Severity | Rationale | Owner |
|---|---|---|---|
| Single Maven module | MED | Charter says multi-module. Deferred — flat module makes early-stage refactors cheaper. Reassess after Payroll ships. | Backend lead |
| Rejected Sprint 6/7/8.1 code | HIGH | Company/Organization/Person exist on abandoned branches, do not comply with charter. Must be rebuilt on WP-003 base. | Backend lead |
| No Kafka | MED | Notification / Workflow modules will need it. Add with first consumer. | Backend lead |
| No k8s manifests | HIGH | Blocks staging deployment beyond Docker Compose. | Platform |
| No dependency / secret scanning in CI | HIGH | Security policy gap. | Platform |
| No OpenAPI contract tests | MED | Regression risk grows with each module. | Backend lead |

## 8. Build & test status

```
$ mvn -B compile spotless:check checkstyle:check pmd:check spotbugs:check
BUILD SUCCESS

$ mvn -B test -Dtest='!*IntegrationTest,!EwosApplicationTests,!FlywayMigrationValidationTest'
Tests run: 79, Failures: 0, Errors: 0, Skipped: 0

Integration tests (require Docker daemon): 25 tests — CI-only.
```

## 9. Explicit CTO-visible risks

1. **Sprint 6/7/8.1 salvage** — do not merge the rejected branches. Rebuild fresh under WP-002B/002C/002D per master arch, starting with `organization`.
2. **Multi-module migration** — schedule after Employee ships; earlier is premature.
3. **Ops gap** — Kubernetes + secret + dependency scanning must land before the first pilot tenant.
