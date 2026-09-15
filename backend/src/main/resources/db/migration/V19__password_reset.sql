-- Self-service password reset. The raw token only ever exists in the message we send; what we
-- store is its SHA-256 hash, so a leaked database still can't be used to take over an account.
-- Rows are kept after use (used_at set) rather than deleted, so a replayed link can be told
-- apart from an expired one and the attempt stays visible in the table.
CREATE TABLE password_reset_tokens (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id   INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    token_hash  TEXT    NOT NULL,
    channel     TEXT,               -- EMAIL | WHATSAPP — where the link was actually sent
    expires_at  TEXT    NOT NULL,
    used_at     TEXT,
    created_at  TEXT    NOT NULL
);
CREATE UNIQUE INDEX idx_password_reset_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id, created_at);
