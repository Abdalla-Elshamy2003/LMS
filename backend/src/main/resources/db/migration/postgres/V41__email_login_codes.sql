CREATE TABLE email_login_codes (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(12) NOT NULL CHECK (purpose IN ('LOGIN', 'REGISTER')),
    user_id BIGINT REFERENCES users(id),
    code_hash VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    used_at TIMESTAMPTZ,
    proof_hash VARCHAR(64) UNIQUE
);
CREATE INDEX idx_email_login_codes_email_created ON email_login_codes(email, created_at);
