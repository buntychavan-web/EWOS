# ESS/MSS Caching and Connection Pooling Conventions

Sprint 27A item 10 (PRD §11 Security Architecture, §15 Performance Targets). Documentation only —
no code in this sprint changes HikariCP or Redis configuration; this records the target for the
widget endpoints Sprint 27E builds, so that work starts from an agreed number rather than an
ad hoc one.

## Current state (verified against `pom.xml` / `application.yml`, 2026-08-08)

- **HikariCP:** no explicit `spring.datasource.hikari.*` block exists anywhere in `application*.yml`
  today. The application runs on Spring Boot's HikariCP auto-configuration defaults —
  `maximum-pool-size: 10`, `minimum-idle` equal to the pool size, `connection-timeout: 30000ms`.
  This has been sufficient through Sprint 26A; it has never been sized for the 100K-employee/10K
  concurrent-login scenario the PRD's §15 Performance Targets describes, because nothing in the
  platform has needed that yet.
- **Redis:** already configured (`com.ewos.shared.config.RedisConfig`, `@EnableCaching`, gated by
  `app.redis.enabled`, default on) and already used for Spring's cache abstraction. No ESS/MSS code
  uses it yet — Sprint 27A introduces no new cache usage; this is the target for 27E's widget
  endpoints (PRD §5.1).

## HikariCP pool-sizing target (for review before Sprint 27E, not applied now)

The widget endpoints (§5.1) are short, single-module, mostly-cached reads — they should not, on
their own, be the reason the pool needs to grow. The number to watch is **total concurrent
in-flight DB-hitting requests at peak** (a 100K-employee tenant's 9:00 AM login spike), not ESS/MSS
specifically. Per the standard HikariCP sizing formula (`connections = ((core_count * 2) + effective_spindle_count)`,
Postgres-appropriate) and this application's actual workload (I/O-bound web requests, not
CPU-bound batch jobs), the practical target is:

- **Do not raise the pool size speculatively in Sprint 27A or 27E.** Cache-hit widget reads should
  reduce DB load, not add to it — if the pool needs to grow, that should be driven by a real load
  test against the widget endpoints once they exist (Sprint 27E acceptance criteria, PRD §15),
  not a guess made now.
- **When that load test happens**, size against `maximum-pool-size = (peak concurrent DB-hitting
  requests) / (average query duration in seconds) × safety margin`, cross-checked against the
  Postgres server's own `max_connections` (every application instance's pool, times instance
  count, must stay under it) — not against ESS/MSS traffic in isolation, since the pool is shared
  platform-wide.
- **Statement caching / query timeout** (audit finding 6.4's other half): also not configured
  explicitly today (Hikari + Postgres JDBC driver defaults). Same recommendation — set explicit
  `connection-timeout` and a query timeout once real p95/p99 latencies exist to size against,
  rather than picking a number now.

## Redis TTL conventions for widget endpoints (target for Sprint 27E)

Per PRD §5.1/§15, each widget is cached independently, cache-aside, with its own TTL rather than
one blanket value — a fast-changing widget (approvals count) needs a short TTL to stay useful; a
slow-changing one (payroll summary) can tolerate a longer one without anyone noticing staleness.

| Widget | Target TTL | Why |
|---|---|---|
| `approvals` (manager pending-approval count) | 60s | Changes whenever a task is assigned/completed — one of the two data sources of the event-driven eviction path (§5.1's cache-invalidation mechanism); TTL here is purely the safety net for a missed event |
| `notifications` (unread count) | 60s | Same reasoning — changes on read as well as write, so TTL matters more here than for the other widgets |
| `leave` (balance + pending count) | 120s | Changes only on submit/approve/cancel |
| `goals` (counts by status) | 180s | Changes infrequently relative to a single session |
| `performance` (current appraisal status) | 180s | Same reasoning as `goals` |
| `payroll` (latest payslip summary) | 300s | Changes at most once per pay cycle; longest safe TTL of the set |

These are starting targets for whoever implements Sprint 27E, not values enforced by any code in
this sprint — Sprint 27A adds no widget endpoints and no cache usage. Revisit against real access
patterns once the widgets exist, the same way the HikariCP number above should be revisited against
a real load test rather than trusted as final on day one.

## Why this is a documentation-only Sprint 27A deliverable

Configuring HikariCP or Redis TTLs now, before any endpoint exists to generate real traffic
patterns, would be tuning against a guess. The PRD's own position (§15) is explicit that read
replicas and CDN delivery are deferred until real traffic data justifies them; the same discipline
applies here at a smaller scale — the target numbers above exist so Sprint 27E starts from an
agreed baseline, not so Sprint 27A can claim the tuning is "done."
