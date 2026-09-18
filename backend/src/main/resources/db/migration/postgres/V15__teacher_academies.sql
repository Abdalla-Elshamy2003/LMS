ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX idx_users_username ON users(lower(username)) WHERE username IS NOT NULL;
CREATE TABLE teacher_academies (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 tenant_id BIGINT NOT NULL UNIQUE REFERENCES tenants(id),
 manager_tenant_id BIGINT NOT NULL REFERENCES tenants(id),
 teacher_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
 slug TEXT NOT NULL UNIQUE,
 name TEXT NOT NULL,
 tagline TEXT NOT NULL,
 headline TEXT NOT NULL,
 description TEXT NOT NULL,
 about_text TEXT NOT NULL,
 subject TEXT NOT NULL,
 phone TEXT NOT NULL DEFAULT '',
 photo_url TEXT NOT NULL DEFAULT '/images/mohamed-soliman.png',
 photo_data TEXT,
 cover_data TEXT,
 demo_content BOOLEAN NOT NULL DEFAULT TRUE,
 published BOOLEAN NOT NULL DEFAULT TRUE,
 default_home BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX idx_academy_default ON teacher_academies(default_home) WHERE default_home;
