# Organization module

**Package:** `com.ewos.organization`
**Introduced:** WP-004
**Depends on:** `shared`, `identity`
**Consumed by (future):** `employee`, `attendance`, `payroll`, `analytics`

## Purpose

Owns the hierarchical, multi-tenant, multi-company organizational structure — departments,
divisions, business units, cost centres, teams. Every future HR module keys off an organization
unit ID.

## Domain model

### `OrganizationUnitType` — metadata dictionary

- Per-tenant list of allowed unit kinds (department, division, business_unit, cost_center, team,
  region, ... whatever the tenant needs).
- Fields: `code`, `name`, `description`, `sortOrder`, `active`.
- Soft-deleted; partial unique index on `(tenant_id, LOWER(code))`.
- **No enum in code** — types are data.

### `OrganizationUnit` — aggregate root

- Belongs to exactly one tenant and one company.
- Points at exactly one `OrganizationUnitType`.
- Optional `parent` (self-reference) — forms an acyclic tree per tenant + company.
- Effective-dated (`effectiveFrom`, `effectiveTo`).
- Lifecycle: `ACTIVE` → `SUSPENDED` → `CLOSED`.
- Optimistic-locked (`@Version`).
- Soft-deleted via `@SQLDelete`; partial unique on `(tenant_id, company_id, LOWER(code))`.

## Domain policy — `OrganizationHierarchyPolicy`

Framework-free rule enforcer. Called by the application service on every mutation:

- Parent must be in the same tenant AND company.
- Parent must not be CLOSED.
- Assigning a parent must not create a cycle.
- Closing a unit requires zero active children.

## Public API

Base path: `/api/v1/organization`

### Unit types

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/unit-types` | `ORG_WRITE` | Create a unit type |
| `GET` | `/unit-types` | `ORG_READ` | List (needs `X-Tenant-Id` header) |
| `GET` | `/unit-types/{id}` | `ORG_READ` | Fetch by ID |
| `PATCH` | `/unit-types/{id}` | `ORG_WRITE` | Update |
| `DELETE` | `/unit-types/{id}` | `ORG_ADMIN` | Soft-delete |

### Units

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/units` | `ORG_WRITE` | Create |
| `GET` | `/units` | `ORG_READ` | Paged search (query params match `OrganizationUnitSearchCriteria`) |
| `GET` | `/units/{id}` | `ORG_READ` | Fetch |
| `PATCH` | `/units/{id}` | `ORG_WRITE` | Update mutable fields (re-parent, rename, ...) |
| `POST` | `/units/{id}/status?target=CLOSED` | `ORG_ADMIN` | Change status |
| `DELETE` | `/units/{id}` | `ORG_ADMIN` | Soft-delete (fails 409 with children) |
| `GET` | `/units/tree?companyId=...` | `ORG_READ` | Full hierarchical tree; Redis-cached |

All read/write endpoints require an `X-Tenant-Id` header. The tenant boundary is enforced in the
repository layer via `findByIdAndTenantId(...)` and `OrganizationUnitSpecifications`.

## Persistence — Flyway `V9__organization_engine.sql`

- Two tables (`organization_unit_types`, `organization_units`) with:
  - `tenant_id UUID NOT NULL` on both, no FK yet (Tenant module not built).
  - Partial unique indexes for soft-delete-friendly code reuse.
  - CHECK constraints for status enum, effective-date range, no-self-parent.
- Seeds three new permissions (`ORG_READ`, `ORG_WRITE`, `ORG_ADMIN`) and grants them to
  `SYSTEM_ADMIN` if that role exists.

## Events

Every write path emits an `OrganizationUnitEvent` through Spring's `ApplicationEventPublisher`.
The event carries the aggregate id, tenant/company/parent IDs, code, name, status, actor, and
timestamp.

`OrganizationEventPublisher` listens on `AFTER_COMMIT` and forwards to the Kafka topic
`ewos.organization.unit`. Firing after commit prevents leaked events on rollback. The publisher is
optional (`@Autowired(required = false)`) — if `KafkaTemplate` is not on the classpath (local dev
without a broker), events are logged and swallowed instead of failing the request.

Event types:

- `CREATED`
- `UPDATED`
- `REPARENTED` — parent changed
- `STATUS_CHANGED` — active ↔ suspended
- `CLOSED` — moved to CLOSED terminal state
- `DELETED` — soft-deleted

## Caching

The `/units/tree` endpoint is cached in Redis under key
`organization.unit.tree::{tenantId}:{companyId}` with a 10-minute default TTL. Any create /
update / status-change / delete on a unit in that tenant + company scope evicts the entry.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.messaging.kafka.enabled` | `false` | If `true`, wires `KafkaTemplate` and publishes events. Enable in `docker-compose.yml` and production. |
| `app.messaging.kafka.client-id` | `ewos-backend` | Kafka producer `client.id`. |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Broker address list. |
| `app.redis.enabled` | `true` | If `false`, disables both the RedisTemplate bean and the cache manager (unit tests). |

## Testing

- **Unit tests** — `OrganizationHierarchyPolicyTest` (8), `OrganizationMapperTest` (4),
  `OrganizationUnitServiceTest` (6), `OrganizationUnitTypeServiceTest` (4). Total: 22 tests.
- **Integration test** — `OrganizationUnitIntegrationTest` (Testcontainers Postgres, real Flyway,
  real Redis). Verifies:
  - Create-with-parent, re-parent, close-with-children, search, tree-endpoint caching.
  - Multi-tenant isolation (a unit under tenant A is invisible to tenant B).

## Deferred to later work packages

- **`OrganizationUnitVersion`** — full effective-dated history table (rename with retained past
  names). The current model captures `effectiveFrom` / `effectiveTo` on the unit row, which is
  sufficient for closures but not for renames. Add when Payroll needs point-in-time reporting.
- **Bulk import** endpoint (`POST /units/bulk`) — will need it before onboarding tenants with
  10k+ units.
- **Position hierarchy** as a separate aggregate (not to be conflated with unit hierarchy).
