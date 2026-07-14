# Sprints Index

_Every sprint's artifacts in one place. Update this file as each sprint ships._

Convention per row: **Scope** · **Head commit** · **Migration** · **ADR** · **Business rules** · **CTO review package**.

For the always-current running status see [`/PROJECT_STATUS.md`](../../PROJECT_STATUS.md).

| Sprint | Scope | Head | Migration | ADR | Business rules | Review package |
|---|---|---|---|---|---|---|
| 1 | Foundation — Spring Boot 3.3 / Java 21 / Postgres / Flyway / JWT scaffold / Actuator / OpenAPI / Docker | — | V1 | — | — | pre-checklist |
| 2 | Identity — users, roles, permissions, JWT login, refresh rotation | — | V2, V3 | — | — | pre-checklist |
| 3 | (skipped) | — | — | — | — | — |
| 4 | User management — CRUD, password policy, history, search | — | V4 | — | — | pre-checklist |
| 5 | Hardening — CI, static analysis, JaCoCo, soft delete, `@Version`, logout, correlation IDs | — | V5 | — | — | pre-checklist |
| 6 | Company Configuration — tenants, companies, versions, statutory, banks, policies, shared services | `cb100b9` | V6 | [ADR-0001](../adr/0001-company-effective-dating-and-tenancy.md) | — | pending |
| 7 | Organization Structure — configurable levels, nodes, append-only history, walk-up inheritance | `7466a60` | V7 | [ADR-0002](../adr/0002-organization-structure-engine.md) | — | pending |
| 8.1 | Person Engine — permanent identity, effective-dated profile, duplicate engine, readiness | `7bbefa0` | V8 | [ADR-0003](../adr/0003-person-engine.md) | [person-engine.md](../business-rules/person-engine.md) | [sprint-8.1-cto-review.md](../reviews/sprint-8.1-cto-review.md) |
| 8.1.1 | Dashboard summary — `GET /api/dashboard/summary` in a single native query | `a53af0a` | V9 | — | — | (included in Sprint 8.1 package) |
| 8.2 | (planned) Employment — Employment records referencing `persons.id`; one Person, many Employments | — | — | — | — | — |

---

## What each sprint must produce

Every sprint from Sprint 6 onward must deliver, at minimum:

1. **Flyway migration** at the next unused V-number.
2. **ADR** under `/docs/adr/NNNN-<slug>.md` if it introduced a new architecture decision.
3. **Business rules doc** under `/docs/business-rules/<module>.md` enumerating every rule + its
   enforcement point.
4. **CTO Review Package** at `/docs/reviews/sprint-<N>-cto-review.md`, filled out from the
   template in [`/docs/reviews/CTO_REVIEW_CHECKLIST.md`](../reviews/CTO_REVIEW_CHECKLIST.md).
5. **PROJECT_STATUS.md** updated — new "Delivered by sprint" subsection, any new tech debt
   under "Remaining technical debt", and a dated change log entry.
6. **Sprints Index update** — a new row in the table above pointing at every artifact.

---

## Sprint numbering rules

- Whole numbers (`6`, `7`, `8`) = a full sprint's worth of scope.
- Decimal children (`8.1`, `8.2`) = deliberately-split parts of a larger initiative. Sprint 8 was
  planned as "People", split into 8.1 (Person Engine) and 8.2 (Employment).
- Sub-decimal (`8.1.1`) = follow-on deliverables added inside an in-flight sprint (like the
  Dashboard summary shipped alongside 8.1 to unblock a UI team).
- Skipped sprints stay in the table with `(skipped)` so the numbering timeline is honest.
