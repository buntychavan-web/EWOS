# ADR-0002: Organization structure engine — level-as-data, append-only history, walk-up inheritance

- **Status:** Accepted
- **Date:** 2026-07-13
- **Sprint:** Sprint 7 — Organization Structure
- **Authors:** Backend team

## Context

Sprint 7 introduces the platform's organization structure. Every downstream module — payroll, leave, attendance, workflows, reporting, hierarchy-based security — resolves *who works where* through this structure, so the shape we pick here is inherited by everything that comes after. The sprint brief was explicit about three constraints that pushed us away from the shortest path:

1. **No hardcoded levels.** Customers must configure their own level names (Business Unit / Division / Region / Branch, or something completely different). We must not bake the vocabulary into the schema.
2. **History is never lost.** Real HR structures merge, split, move, rename, and deactivate. Payroll and statutory filings must be able to reconstruct "what did the org look like on date D?" for any past D.
3. **Node changes do not touch employees.** Moving a node must automatically re-parent every employee referencing that node — updating employee rows on every reorg is unacceptable at scale and would corrupt salary/leave history.

This ADR records the three decisions those constraints forced.

## Decision

### 1. `organization_levels` is data, not an enum

Level definitions live in `organization_levels`, one row per level, keyed by `(tenant_id, code)`. Levels form a parent-child chain via `parent_level_id` and are ordered for display by `display_sequence`. Nothing in the code references a level by name — every reference is by UUID.

- **Alternative rejected:** a Java enum (`Level = { BU, DIVISION, REGION, BRANCH }`). Would have been shorter, but would also have prevented tenants from using a five-level or two-level structure and would have made adding a new level a Flyway migration instead of a POST.

### 2. Effective-dated node master + append-only version log

Two-table pattern:

- **`organization_nodes`** is the *current* master row — `code`, `name`, `parent_node_id`, `level_id`, `effective_from`, `effective_to`, `active`. Employees FK here. Move / rename / deactivate mutate this row.
- **`organization_node_versions`** is an *append-only* audit of structural change. Every service-layer mutation (`CREATED`, `RENAMED`, `MOVED`, `MERGED_INTO`, `SPLIT_FROM`, `DEACTIVATED`, `REACTIVATED`) writes a row snapshotting `code`, `name`, `parent_id`, `level_id` at the time of the change, plus `related_node_id` for merges/splits.

The consequence — and the whole point — is that **moving a node is a two-line change**:

```java
node.setParent(newParent);
writeVersion(node, MOVED, effectiveFrom, ...);
```

Every employee that FKs to that node's id is now under the new parent for free. No employee row is touched. Historical structure reconstruction reads the version log; `WHERE effective_from <= :date AND (effective_to IS NULL OR effective_to >= :date)` returns the snapshot that was live on that date.

- **Alternative rejected:** overwrite the master row and rely on generic audit tables to reconstruct history. Rejected for the same reason as Sprint 6's company profile: audit tables answer "what changed?", not "what was the state?", and queries for the latter become expensive and fragile.
- **Alternative rejected:** materialise a new node id on every structural change (SCD-2-style) and repoint children/employees. Rejected — this exact pattern is what forces payroll/leave to update employee rows during reorgs, which the brief forbids.

### 3. Walk-up inheritance with per-node overrides

Nodes can inherit ten resource classes (holiday calendar, payroll calendar, leave / attendance / shift policies, workflow, cost centre, profit centre, statutory registration, shared service). The resolution algorithm for `(node, kind, asOf)`:

1. Look for a live override on the node itself.
2. Walk up the parent chain, returning the first live override found.
3. If nothing in the chain has an override, return unresolved. The consumer falls through to the company-level assignment established in Sprint 6.

"Live" means `effective_from <= asOf AND (effective_to IS NULL OR effective_to >= asOf)`.

Non-overlap for a given `(node, kind)` is enforced in the service layer (`OrganizationInheritanceService#assertNoOverlap`), same trade-off as Sprint 6: portability today, `EXCLUDE USING gist` later.

- **Alternative rejected:** materialise the full inheritance table (one row per node × kind, populated on every override change). Faster reads, but every node move requires a recompute and every ancestor override change fans out across every descendant. The walk-up approach is O(depth) with tiny constants and correct-by-construction.

## Consequences

**Good**
- A move / merge / split / rename is a one-service-call operation that leaves the employee table completely untouched.
- Historical structure at any date is a single query against the version log.
- Levels are per-tenant configurable; the platform ships zero hardcoded vocabulary.
- Every future domain module (payroll, leave, attendance) resolves its policy by calling `OrganizationInheritanceService.resolve(node, kind, asOf)` — no bespoke walk logic in each module.

**Neutral**
- Two-table read pattern (master + versions) means "show me history" is one JPA call; "show me current state" is another. Acceptable — they are distinct read paths in the UI too.
- The inheritance walk is O(tree-depth). Real HR trees are shallow (rarely more than 5–6 levels), and each step is a keyed lookup on `(node_id, kind)` covered by the index. If a customer builds a pathological 50-deep tree we can memoise per-request in `OrganizationInheritanceService`.

**Bad / debts explicitly accepted**
- Race-time overlap on inheritance overrides is best-effort. Same trade-off as Sprint 6; same follow-up (`EXCLUDE USING gist`, gated on `btree_gist`).
- `override_ref` values are unvalidated at write time. Same reason as company `policy_ref`: the target tables belong to other bounded contexts that don't exist yet.
- Hierarchy-based security (`OrganizationSecurityService.accessibleNodeIds`) currently loads the tree from the DB on every call. Fine at Sprint 7 scale; when we start caring about latency, cache per user per tenant in Redis and invalidate on any `MOVED` / `MERGED_INTO` / `SPLIT_FROM` version write.

## Alternatives considered

- **Materialised path (`/root/bu/region/branch`).** Cheap descendant queries, but every move rewrites every descendant's path. Also duplicates data that already lives in the parent chain. Not worth it at HR-tree scale.
- **Closure table.** Answers "all descendants" in one query but doubles storage and doubles the cost of every move. Reserved for the day we need it (revisit once tree depth × node count × query rate justifies the write cost).
- **Two hierarchies (structural + reporting).** Some HR systems split them. Deferred — real customer feedback should drive this, not speculation. Nothing in Sprint 7 forecloses it.

## Follow-ups

1. Wire `OrganizationInheritanceService.resolve` into the future consumers (payroll cycle resolver, leave-policy resolver, attendance-policy resolver) — the fall-through to Sprint 6's company assignment is the contract they should call.
2. Once employees exist (later sprint), FK `employees.organization_node_id` → `organization_nodes.id` and forbid storing region/department/branch on the employee row.
3. Add `EXCLUDE USING gist` for temporal overlap on `organization_node_inheritance_overrides` when we accept `btree_gist` (same follow-up as ADR-0001 §2).
4. When hierarchy-based security starts firing on hot paths, memoise `accessibleNodeIds` per (tenant, user) with invalidation on any node version write.
