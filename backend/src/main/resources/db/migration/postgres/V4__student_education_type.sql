-- V4: student education track (نظام التعليم: عادي / لغات / تجريبي) — additive (SQLite: ADD COLUMN only)

ALTER TABLE students ADD COLUMN education_type TEXT NOT NULL DEFAULT 'عادي';
