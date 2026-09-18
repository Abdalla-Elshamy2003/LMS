-- Optional scheduled-release date for a lesson: null means visible to enrolled students
-- immediately; a future timestamp hides it from students/parents until that moment (staff
-- always see it, for authoring/preview).
ALTER TABLE lessons ADD COLUMN release_at TEXT;
