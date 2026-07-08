-- Baseline migration for EWOS.
-- Sprint 1 introduces no domain tables; only shared PostgreSQL extensions.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
