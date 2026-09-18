-- Optional promotional discount on a course, driven from the marketing campaigns page.
-- Null/0 means no active discount; the final charged price is always computed server-side
-- from price * (100 - discount_percent) / 100, so a displayed discount always matches what
-- checkout actually charges.
ALTER TABLE courses ADD COLUMN discount_percent INTEGER;
