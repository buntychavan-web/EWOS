# Flyway Migration Rules (WP-002A)

Every schema change in EWOS goes through Flyway. These rules protect against the classes of
failure we've hit (or nearly hit) so far.

## Naming

- **Versioned migrations only** — `V{N}__{snake_case_description}.sql`. No repeatable (`R__`) or
  undo migrations.
- **`N` is monotonically increasing, no gaps, no reuse.** Two engineers who create `V9` on
  different branches will conflict on merge — the second must renumber before merging.
- **Descriptive name.** `V9__add_user_locked_until` beats `V9__auth`.

## Content

- **Additive only.** No `DROP COLUMN`, no `DROP TABLE`, no `RENAME COLUMN` without an ADR.
- **`ddl-auto=none`** — Flyway owns the schema. Do not use Hibernate DDL for anything.
- **Seeds are idempotent.** Use `ON CONFLICT DO NOTHING` on every `INSERT` in a seed migration.
- **Data migrations use `IF NOT EXISTS`** when the target may already have partial state (e.g.
  re-runs against a partly-migrated dev DB).
- **Every new hot-path query gets a matching index** in the same migration. Not "later".
- **Uniqueness across soft-deleted rows** uses partial unique indexes
  (`WHERE deleted_at IS NULL`), not table-level `UNIQUE`.

## Never edit a merged migration

If migration `V9` is in `main`, treat it as **immutable**. Fix a mistake with `V10` — add the
missing column, drop the wrong index, correct the seed row. Flyway's checksum guard exists for
exactly this failure mode; do not run `flyway repair` in production.

## Validation gates

The `FlywayMigrationValidationTest` (WP-002A) boots Flyway against a fresh Testcontainers
Postgres and asserts:

1. Every migration file is picked up by Flyway.
2. `migrate()` succeeds without error against an empty database.
3. The final schema version matches the highest `V{N}` in `db/migration/`.

Runs as part of the CI integration test suite. Failing this test blocks merge.

## Local review checklist (paste into every migration PR body)

- [ ] Naming: `V{N}__{name}.sql`, next unused N, no rename.
- [ ] No edits to previously-merged V-files.
- [ ] Additive only (or ADR referenced).
- [ ] Every new column that must be non-null has a default or a two-step plan.
- [ ] Every seed statement is `ON CONFLICT DO NOTHING`.
- [ ] Every new hot-path query has a matching index in this migration.
- [ ] Uniqueness that must survive soft-delete uses partial indexes.
- [ ] `FlywayMigrationValidationTest` passes.
- [ ] `mvn verify` green on a fresh DB.

## Rollback strategy

Flyway has no built-in undo. Rollback plans are:

1. **Purely additive migration** (new table, new column with default, new index): safe to leave in
   place after a code rollback — the new column is unused, storage cost is negligible.
2. **Data migration** (e.g. back-filling `family_id`): plan a *forward* fix migration; do not
   attempt to reverse the change via SQL in production. Test the forward fix against a snapshot
   first.
3. **Schema break** (drops, renames): ADR + two-step release: `V(N)` adds the new shape, code
   is deployed reading both old + new, `V(N+M)` deletes the old shape after a bake-in window.
