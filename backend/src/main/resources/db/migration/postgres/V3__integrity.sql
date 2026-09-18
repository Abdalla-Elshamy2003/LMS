-- V3: defense-in-depth uniqueness the app already relies on but the schema didn't enforce.
-- verify_code is the public lookup key for certificate verification (§28) — a DB-level
-- guarantee means a random-generation collision fails loudly instead of silently letting
-- two certificates share a verification code.
CREATE UNIQUE INDEX idx_certificates_verify_code ON certificates(verify_code);
