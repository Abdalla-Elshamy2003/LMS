-- Assessment professional workflow + protected video sessions (additive only).

-- ---------- Exams ----------
ALTER TABLE questions ADD COLUMN explanation TEXT;
ALTER TABLE exams ADD COLUMN show_results TEXT NOT NULL DEFAULT 'AFTER_SUBMIT'; -- NEVER, AFTER_SUBMIT, AFTER_CLOSE
ALTER TABLE exams ADD COLUMN show_correct_answers BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE exams ADD COLUMN updated_at TEXT;
ALTER TABLE student_exams ADD COLUMN integrity_events TEXT;      -- JSON [{type, at}]
ALTER TABLE student_exams ADD COLUMN fullscreen_exits INTEGER NOT NULL DEFAULT 0;

-- ---------- Homework ----------
ALTER TABLE assignments ADD COLUMN allow_late BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE assignments ADD COLUMN late_penalty_percent REAL NOT NULL DEFAULT 0; -- per day late
ALTER TABLE assignments ADD COLUMN rubric TEXT;                                   -- JSON criteria
ALTER TABLE assignments ADD COLUMN updated_at TEXT;
ALTER TABLE submissions ADD COLUMN raw_score REAL;
ALTER TABLE submissions ADD COLUMN penalty_percent REAL NOT NULL DEFAULT 0;
ALTER TABLE submissions ADD COLUMN rubric_scores TEXT;                            -- JSON {criterionId: points}
ALTER TABLE submissions ADD COLUMN resubmissions INTEGER NOT NULL DEFAULT 0;
ALTER TABLE submissions ADD COLUMN returned_at TEXT;

CREATE TABLE submission_files (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    submission_id BIGINT NOT NULL REFERENCES submissions(id),
    file_key      TEXT    NOT NULL UNIQUE,
    name          TEXT    NOT NULL,
    size_bytes    INTEGER NOT NULL DEFAULT 0,
    uploaded_at   TEXT    NOT NULL
);
CREATE INDEX idx_submission_files_submission ON submission_files(submission_id);

CREATE TABLE grading_comments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenants(id),
    teacher_id BIGINT NOT NULL REFERENCES users(id),
    text       TEXT    NOT NULL,
    uses       INTEGER NOT NULL DEFAULT 0,
    created_at TEXT    NOT NULL
);
CREATE INDEX idx_grading_comments_teacher ON grading_comments(tenant_id, teacher_id);

-- ---------- Protected video sessions ----------
CREATE TABLE video_watch_sessions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    material_id     BIGINT NOT NULL REFERENCES lesson_materials(id),
    lesson_id       BIGINT NOT NULL,
    course_id       BIGINT NOT NULL,
    session_token   TEXT    NOT NULL UNIQUE,
    ip              TEXT,
    user_agent      TEXT,
    started_at      TEXT    NOT NULL,
    last_seen_at    TEXT    NOT NULL,
    ended_at        TEXT,
    end_reason      TEXT,   -- CLOSED, REPLACED, EXPIRED
    watched_seconds INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_watch_sessions_user ON video_watch_sessions(tenant_id, user_id, last_seen_at);
CREATE INDEX idx_watch_sessions_course ON video_watch_sessions(tenant_id, course_id);
