-- Blocking with a reason the blocked person is shown.
-- users:    head office blocks a teacher or a student on the whole platform (on the account they sign in with).
-- students: a teacher blocks one student in their own space only (the student's seat with that teacher).
ALTER TABLE users ADD COLUMN blocked_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN blocked_reason TEXT;
ALTER TABLE users ADD COLUMN blocked_by TEXT;

ALTER TABLE students ADD COLUMN blocked_at TIMESTAMPTZ;
ALTER TABLE students ADD COLUMN blocked_reason TEXT;
ALTER TABLE students ADD COLUMN blocked_by TEXT;
