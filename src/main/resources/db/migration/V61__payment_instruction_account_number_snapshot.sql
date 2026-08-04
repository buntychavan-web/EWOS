-- Codex CTO audit follow-up (Sprint 24L): BankAdviceService populated payment_instructions with
-- only the masked account number, so a generated bank advice file could never actually be used to
-- credit an employee — the bank needs the real number. This column carries an AES-256-GCM
-- encrypted snapshot of the real account number (BankAccountFieldEncryptor, same converter as
-- employee_bank_accounts.account_number) for the export pipeline only; every user-facing API
-- response keeps exposing account_number_masked exclusively (see PaymentInstructionResponse).
ALTER TABLE payment_instructions
    ADD COLUMN account_number_snapshot VARCHAR(255);
