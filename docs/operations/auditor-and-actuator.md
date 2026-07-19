# Operations — Auditor Behaviour + Actuator Security

Two operational contracts every deploy runbook and every downstream reader needs to know.

---

## 1. `AuditorProvider` — when `created_by` / `updated_by` is NULL

`created_by` and `updated_by` come from the JWT subject via
`com.ewos.common.persistence.AuditorProvider`. The provider returns
`Optional.empty()` — and JPA writes NULL — in exactly three situations:

| Scenario | Example | Fix |
|---|---|---|
| No security context | `IdentityBootstrap` seeds the default admin at first boot; Flyway migrations that insert seed rows; `@Scheduled` jobs (e.g. `PurgeJob`) | Expected. The row was created by the system, not a user. |
| Anonymous / unauthenticated request | A permit-all endpoint that persists (shouldn't exist in this codebase; foundation reserves permit-all for read-only public endpoints) | Should not occur. If observed, audit the offending endpoint. |
| Non-UUID subject in JWT | Service-to-service token flows that carry a symbolic subject rather than a UUID | Foundation ships user-only JWTs, so this branch is defensive. When machine tokens land, revisit the provider to accept `svc:<name>` subjects. |

### Read-side implications

- **Do not assume `created_by` is non-null**. Every consumer that renders "created by" must handle NULL and display a fallback (`System`, `Automated`, or blank).
- **Do not join `created_by → users(id)` with an inner join** — an inner join hides the seed rows. Use a left join or filter for known-user rows only.
- **Compliance / audit reports** that summarise "who touched this?" should include a row-count of `NULL created_by` writes and label them explicitly ("system-initiated"). Failing to do this hides bootstrap and scheduled activity.

### Bootstrap admin — special case

`IdentityBootstrap` runs as `ApplicationRunner` at first boot, before any HTTP traffic. The default admin therefore has `created_by = NULL`. This is intentional — no user "created" the very first user. Every user created *by* the admin thereafter will have `created_by = <admin's uuid>`.

### Extending in a future WP

If we later want NULL-`created_by` writes to be attributable to a placeholder identity (say, a synthetic "system" user), the change is:

1. Create a `SYSTEM_ACTOR` user row in a Flyway migration with a fixed UUID.
2. Change `AuditorProvider.getCurrentAuditor()` to return `Optional.of(SYSTEM_ACTOR_UUID)` in branches 1 and 3 above.
3. Update tests. Nothing else touches this file.

The current design deliberately avoids this — a NULL is honest, a synthetic UUID is data we'd have to explain.

---

## 2. Actuator endpoint security

The Actuator surface is intentionally minimal per profile. Runtime endpoints are exposed via `management.endpoints.web.exposure.include`; the security layer separately decides which of those are public.

### Public paths (no auth)

Defined once in `com.ewos.security.SecurityConfig.PUBLIC_ENDPOINTS`:

- `/actuator/health` — overall UP/DOWN. Safe to expose to load balancers.
- `/actuator/health/**` — includes `/liveness` and `/readiness` sub-paths. Kubernetes probes hit these.
- `/actuator/info` — build info + git commit if wired. No secrets.
- Non-actuator public paths: `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/api/v1/auth/{login,refresh,logout}`.

Anything else requires a valid JWT.

### Endpoints exposed per profile

| Profile | `management.endpoints.web.exposure.include` |
|---|---|
| `dev` (default from `application.yml`) | `health, info, metrics` |
| `test` | `health, info, metrics` (inherited) |
| `prod` (overridden in `application-prod.yml`) | `health, info` only |

**Why the difference:** `/actuator/metrics` in prod would surface counters that a caller can enumerate — useful, but not something an unauthenticated load balancer needs. Prod locks it down; ops accesses metrics via a scraper that authenticates.

### If you want to expose `/actuator/prometheus` in prod

Do all three of the following, in this order — otherwise you are creating an unauthenticated telemetry leak:

1. Add `prometheus` to `management.endpoints.web.exposure.include` in `application-prod.yml`.
2. Add an explicit `.requestMatchers("/actuator/prometheus").hasAuthority('METRICS_READ')` line in `SecurityConfig` **before** the catch-all. Do not add it to `PUBLIC_ENDPOINTS`.
3. Seed a `METRICS_READ` permission in a Flyway migration, grant it to a dedicated `METRICS_SCRAPER` role, and provision a service user for the scraper.

Never add sensitive actuator endpoints (`/env`, `/configprops`, `/beans`, `/mappings`, `/threaddump`, `/heapdump`, `/loggers`) to the exposure list without matching `@PreAuthorize` and an ADR.

### `/actuator/health` details

- `management.endpoint.health.show-details` is `when_authorized` in `application.yml` and `never` in `application-prod.yml` — so anonymous callers get a bare `{"status":"UP"}` and authenticated callers with `SYSTEM_ADMIN` see contributor breakdowns.
- Kubernetes liveness / readiness use `/actuator/health/liveness` and `/actuator/health/readiness` respectively; both are enabled by `management.endpoint.health.probes.enabled=true`.

### Runbook — checking actuator security on a running deployment

```bash
# 1. Public paths respond 200 without auth.
curl -s https://ewos.example.com/actuator/health              # {"status":"UP"}
curl -s https://ewos.example.com/actuator/health/liveness     # {"status":"UP"}
curl -s https://ewos.example.com/actuator/info                # build metadata

# 2. Anything else must be 401 without a token.
curl -s -o /dev/null -w '%{http_code}\n' https://ewos.example.com/actuator/metrics
# → 401

# 3. Sensitive paths must be 404 (not exposed) in prod.
curl -s -o /dev/null -w '%{http_code}\n' https://ewos.example.com/actuator/env
# → 404
```

If item 2 returns anything other than `401` or item 3 returns `200`, roll back and open a ticket.

---

## References

- Source: `com.ewos.common.persistence.AuditorProvider`
- Source: `com.ewos.security.SecurityConfig`
- Config: `src/main/resources/application.yml`, `application-prod.yml`
- Related ADRs: ADR-0004 (CORS) — separate concern, mentioned for cross-reference on the browser-facing surface.
