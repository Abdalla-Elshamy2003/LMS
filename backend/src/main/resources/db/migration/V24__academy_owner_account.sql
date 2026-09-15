-- Lets a teacher who already has a school account open their own teacher page with that same login.
-- Each academy still lives in its own tenant (its students stay isolated from the school roster);
-- this column only records which existing user is allowed to manage it from outside that tenant.
ALTER TABLE teacher_academies ADD COLUMN owner_user_id INTEGER REFERENCES users(id);
CREATE INDEX idx_teacher_academies_owner ON teacher_academies(owner_user_id);
