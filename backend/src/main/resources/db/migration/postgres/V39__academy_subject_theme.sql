-- The teacher picks which subject their page is dressed for (the method strip, the floating notation, the
-- marquee), and whether the hero headline types itself in word by word. 'math' keeps existing pages as they were.
ALTER TABLE teacher_academies ADD COLUMN subject_theme VARCHAR(20) NOT NULL DEFAULT 'math';
ALTER TABLE teacher_academies ADD COLUMN typing_effect BOOLEAN NOT NULL DEFAULT FALSE;
