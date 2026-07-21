# EWOS implementation roadmap

**Authoritative source:** `EWOS_MASTER_ARCHITECTURE_v1.0.md` (frozen v1.0).
**Sequence rule:** each module is production-ready before the next starts. No parallel module scaffolding.

## Priority order (frozen)

| # | Module group | Modules included | Depends on | Status |
|---|---|---|---|---|
| 1 | **Platform** | shared, identity | — | Identity IN USE; shared kernel IN USE. WP-003 refactor lands package layout. |
| 2 | **Core HR** | organization, employee | Platform | Not started. Rebuild required (rejected sprints do not comply). |
| 3 | **Attendance** | attendance | Employee | Not started. |
| 4 | **Leave** | leave | Employee, Attendance | Not started. |
| 5 | **Payroll** | payroll | Employee, Attendance, Leave | Not started. Requires data-classification catalogue. |
| 6 | **Recruitment** | recruitment | Employee | Not started. |
| 7 | **Talent** | talent (learning + performance) | Employee | Not started. Merged per master arch. |
| 8 | **Analytics** | analytics, integration, notification, workflow | multiple | Foundational cross-cutting; scheduled with Attendance + Leave. |
| 9 | **AI Platform** | ai, governance | Analytics | Not started. Requires governance framework. |

## Per-module definition of done

Every module ships all 15 charter artifacts before it is called done:

1. Flyway migration (append-only, `V{N}__<slug>.sql`)
2. Domain entity + value objects
3. Repository (JPA + Specification where filtering is dynamic)
4. Request / response DTOs (records, validation annotations)
5. Mapper (explicit; no reflection-heavy magic)
6. Application service (use-case orchestrator)
7. Domain service (business rules, no framework dependencies)
8. REST controller (with `@PreAuthorize`, correlation-ID friendly)
9. Validation (`jakarta.validation.constraints.*` + `@Valid`)
10. Exception handling (custom `ApiException` subtypes wired to `GlobalExceptionHandler`)
11. Audit (extends `AuditableEntity`; sensitive fields excluded from access logs)
12. Security (permissions seeded in Flyway; new perms added to `SecurityConfig` allow-list only through review)
13. OpenAPI (springdoc annotations on every public method; example payloads where non-trivial)
14. Unit tests (positive + at least one negative per branch)
15. Integration tests (Testcontainers, `AbstractIntegrationTest`, real Postgres, real Flyway)

Any module missing any artifact is not merged.

## Milestones

| Milestone | Definition |
|---|---|
| **M0 — Platform** | WP-003 foundation merged; identity module reviewed against all 15 artifacts and gaps closed. |
| **M1 — Core HR** | organization + employee production-ready. Multi-tenant / multi-company enforced at repository layer. |
| **M2 — Time** | attendance + leave production-ready. |
| **M3 — Payroll** | payroll production-ready with data-classification catalogue in place. |
| **M4 — Talent & Growth** | recruitment + talent production-ready. |
| **M5 — Insight** | analytics + integration + notification + workflow production-ready. |
| **M6 — AI Platform** | ai + governance production-ready with human-in-the-loop approval gates for governed actions. |

## Not on the roadmap

- Front-end (React 19 / TanStack / shadcn) — Kimi's stream (per master arch AI responsibilities).
- Terraform / Argo CD / Helm — Platform team's stream, tracked outside this repo.
- Business-specific customization — not permitted under freeze rule #2.

## Change control

Any change to this roadmap requires a new architecture-doc version. This document is derived from `EWOS_MASTER_ARCHITECTURE_v1.0.md` and does not override it.
