# Database Indexing Review (WP-002A)

Enumerates every hot-path query in the foundation layer, the index that serves it, and any gap
filled by migration `V8__index_review.sql`. Keep this table current when new queries land — a
missing index today is a two-hour postgres CPU spike tomorrow.

## Index inventory

### `users` (V2, V4, V5, V6, V8)

| Query | Index | Source |
|---|---|---|
| Login by username | `ux_users_username_active` (partial UNIQUE) | V2/V5 |
| Login by username (fallback lookup) | `idx_users_username_lower` (partial, WP-002A) | V8 |
| Password reset by email | `ux_users_email_active` (partial UNIQUE) + `idx_users_email_lower` | V2/V5, V8 |
| "Who is currently locked?" | `idx_users_locked_until` (partial) | V6 |
| "Who is close to being locked?" | `idx_users_failed_attempts` (partial) | V8 |
| Soft-delete restriction | `deleted_at IS NULL` — every partial index above filters on this | V5 |

### `roles`, `permissions`, `role_permissions`, `user_roles` (V2, V5)

Small lookup tables. Partial unique indexes cover the natural keys; PK scans are cheap. No new
work.

### `refresh_tokens` (V2, V4, V7, V8)

| Query | Index | Source |
|---|---|---|
| Present-refresh-token lookup | `unique(token_hash)` from the entity's column definition | V2 |
| Revoke by family (WP-001) | `idx_refresh_tokens_family` | V7 |
| Sweep active tokens for a user (logout-all) | `idx_refresh_tokens_user_active` (partial WHERE revoked = false) | V7 |
| Expiry sweep for `PurgeJob` | `idx_refresh_tokens_expires_at` (partial, WP-002A) | V8 |

### `password_history` (V4, V8)

| Query | Index | Source |
|---|---|---|
| Last N passwords for a user | `idx_password_history_user_created` | V4 |
| "Was this hash ever used by this user?" | `idx_password_history_user_hash` (WP-002A) | V8 |

### `login_history` (V4, V8)

| Query | Index | Source |
|---|---|---|
| History for a user, newest first | `idx_login_history_user_occurred` | V4 |
| Time-range reports across all users | `idx_login_history_occurred` | V4 |
| Attempts for an attempted username (brute-force detection) | `idx_login_history_username_occurred` (WP-002A) | V8 |
| Failures-only reports | `idx_login_history_failures` (partial, WP-002A) | V8 |

## Rules for future migrations

1. **Every equality-in-WHERE column that's expected to be selective needs an index.** No exceptions.
2. **Prefer partial indexes** over full-column indexes when the query always filters on the same predicate (`WHERE deleted_at IS NULL`, `WHERE revoked = FALSE`). Smaller, faster, and doesn't lie during EXPLAIN.
3. **Case-insensitive lookups** need `LOWER(column)` functional indexes. Bare `citext` is fine too but doesn't survive schema audits as cleanly.
4. **Composite indexes** must match the query's leftmost-prefix. `(user_id, occurred_at DESC)` serves "history for a user" but not "recent activity across all users" — that needs its own index.
5. **Never index a column that's already covered** by a composite's leftmost prefix.
6. **Every new query that appears in a service** should be verified with `EXPLAIN (ANALYZE, BUFFERS)` against realistic data before shipping.

## Verifying indexes on a running database

```sql
-- List every index on every foundation table
SELECT schemaname, tablename, indexname, indexdef
FROM   pg_indexes
WHERE  tablename IN ('users', 'roles', 'permissions', 'user_roles', 'role_permissions',
                     'refresh_tokens', 'password_history', 'login_history')
ORDER  BY tablename, indexname;

-- Find unused indexes (last 30d)
SELECT relname, indexrelname, idx_scan
FROM   pg_stat_user_indexes
WHERE  idx_scan = 0
  AND  schemaname = 'public'
ORDER  BY relname;
```

Unused indexes are a cost — they still get written on every INSERT / UPDATE. Prune quarterly.
