-- V6: the specific academic year a course/group targets (e.g. "الصف الأول الثانوي"), distinct
-- from the existing broad grade_level (stage: ابتدائي/إعدادي/ثانوي) — lets teachers/admins filter
-- exams and homework by year. Additive (SQLite: ADD COLUMN only).

ALTER TABLE courses ADD COLUMN grade TEXT;
