-- Image-backed questions + in-video checkpoint quizzes (additive only).

-- A question can carry one image (diagram, passage photo, graph) shown above the stem.
ALTER TABLE questions ADD COLUMN image_key TEXT;

-- A bank question pinned to a point in a lesson video. at_seconds NULL = end-of-lesson test.
CREATE TABLE lesson_checkpoints (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id   INTEGER NOT NULL REFERENCES tenants(id),
    lesson_id   INTEGER NOT NULL REFERENCES lessons(id),
    question_id INTEGER NOT NULL REFERENCES questions(id),
    at_seconds  INTEGER,
    position    INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_lesson_checkpoints_lesson ON lesson_checkpoints(tenant_id, lesson_id, position);

-- One row per student per checkpoint; the latest answer wins so a re-watch can be re-answered.
CREATE TABLE lesson_checkpoint_answers (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id     INTEGER NOT NULL REFERENCES tenants(id),
    checkpoint_id INTEGER NOT NULL REFERENCES lesson_checkpoints(id),
    student_id    INTEGER NOT NULL REFERENCES students(id),
    correct       INTEGER NOT NULL DEFAULT 0,
    answer_text   TEXT,
    answered_at   TEXT    NOT NULL
);
CREATE INDEX idx_checkpoint_answers ON lesson_checkpoint_answers(tenant_id, checkpoint_id, student_id);
