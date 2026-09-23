-- One student, many teachers. Every teacher space keeps its own user + student row for the student, so each
-- space stays isolated exactly as before; a row created when the student joins another teacher points at the
-- account they actually sign in with. Linked rows have no usable credentials of their own.
ALTER TABLE users ADD COLUMN primary_user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_users_primary ON users(primary_user_id) WHERE primary_user_id IS NOT NULL;
