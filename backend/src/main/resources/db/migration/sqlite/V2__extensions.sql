-- V2: additive extensions (SQLite supports ADD COLUMN only)

-- Material description (shown to students)
ALTER TABLE lesson_materials ADD COLUMN description TEXT;

-- Exams can be a PDF upload instead of / in addition to bank questions
ALTER TABLE exams ADD COLUMN pdf_key TEXT;

-- Teacher/staff profile fields (for landing page + "what they teach")
ALTER TABLE users ADD COLUMN subjects TEXT;
ALTER TABLE users ADD COLUMN bio TEXT;
ALTER TABLE users ADD COLUMN photo_url TEXT;
ALTER TABLE users ADD COLUMN schedule TEXT;
ALTER TABLE users ADD COLUMN title TEXT;
