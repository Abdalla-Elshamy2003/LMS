-- Head office can delete a teacher. The space is archived, not dropped: payments, attendance, grades and the audit
-- log all point into its tenant, so the rows stay and every listing skips a space whose archived_at is set.
ALTER TABLE teacher_academies ADD COLUMN archived_at TIMESTAMPTZ;
