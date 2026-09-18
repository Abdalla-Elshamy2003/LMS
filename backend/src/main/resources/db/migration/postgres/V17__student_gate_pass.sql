-- Personal entry/exit pass: a stable per-student token behind the QR on their profile, plus the
-- log of scans. Distinct from the existing per-session attendance QR (which the teacher rotates
-- and students scan) — this one is the student's own pass that staff scan at the door.
ALTER TABLE students ADD COLUMN pass_token TEXT;
CREATE UNIQUE INDEX idx_students_pass_token ON students(pass_token) WHERE pass_token IS NOT NULL;

CREATE TABLE student_gate_logs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT NOT NULL,
    student_id          BIGINT NOT NULL,
    direction           TEXT    NOT NULL,  -- IN | OUT
    recorded_by_user_id BIGINT,
    at                  TEXT    NOT NULL
);
CREATE INDEX idx_gate_logs_tenant_at ON student_gate_logs(tenant_id, at);
CREATE INDEX idx_gate_logs_student ON student_gate_logs(tenant_id, student_id, at);
