-- V5: per-course schedule (shown on the teacher/staff cards + attendance filters) and an optional
-- file the teacher attaches to a homework assignment itself (distinct from a student's own
-- submission file) — additive (SQLite: ADD COLUMN only).

ALTER TABLE courses ADD COLUMN schedule TEXT;
ALTER TABLE assignments ADD COLUMN file_key TEXT;
