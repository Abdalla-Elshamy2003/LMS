-- Manual payment instructions per teacher page, plus one-time codes that unlock a paid course
-- after the teacher confirms payment received outside the system (InstaPay / Vodafone Cash).

ALTER TABLE teacher_academies ADD COLUMN instapay_number TEXT;
ALTER TABLE teacher_academies ADD COLUMN vodafone_cash_number TEXT;
ALTER TABLE teacher_academies ADD COLUMN payment_note TEXT;

CREATE TABLE course_access_codes (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenants(id),
    course_id      BIGINT NOT NULL REFERENCES courses(id),
    code           TEXT    NOT NULL UNIQUE,
    status         TEXT    NOT NULL DEFAULT 'UNUSED', -- UNUSED, USED, REVOKED
    created_by     BIGINT NOT NULL REFERENCES users(id),
    created_at     TEXT    NOT NULL,
    used_by_student_id BIGINT REFERENCES students(id),
    used_at        TEXT
);
CREATE INDEX idx_access_codes_course ON course_access_codes(tenant_id, course_id);
