-- Codex CTO audit P0-2: employee_bank_accounts.account_number and .routing_code were stored as
-- plain VARCHAR with encryption left as "the operator's responsibility" (see the prior revision
-- of EmployeeBankAccount's class javadoc). Application code now encrypts both columns at the JPA
-- layer with AES-256-GCM (see BankAccountFieldEncryptor); this migration only widens the columns
-- to fit the resulting Base64(iv || ciphertext+tag) envelope, which is longer than the raw
-- plaintext it replaces. No migration has ever seeded rows into this table, so no data
-- backfill/re-encryption step is required here.
ALTER TABLE employee_bank_accounts
    ALTER COLUMN account_number TYPE VARCHAR(255),
    ALTER COLUMN routing_code TYPE VARCHAR(255);
