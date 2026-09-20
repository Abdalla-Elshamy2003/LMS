-- Teacher's assistant workspace: tasks the teacher hands to their assistant, and private follow-up notes
-- about a student (parent phoned, absence chased, ...) that only the teacher and their assistants can see.

CREATE TABLE assistant_tasks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id    INTEGER NOT NULL REFERENCES tenants(id),
    title        TEXT    NOT NULL,
    details      TEXT,
    priority     TEXT    NOT NULL DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH
    status       TEXT    NOT NULL DEFAULT 'TODO',    -- TODO, DOING, DONE
    due_date     TEXT,
    student_id   INTEGER REFERENCES students(id),
    course_id    INTEGER REFERENCES courses(id),
    assigned_to  INTEGER REFERENCES users(id),       -- null = any assistant of this teacher
    created_by   INTEGER NOT NULL REFERENCES users(id),
    created_at   TEXT    NOT NULL,
    completed_by INTEGER REFERENCES users(id),
    completed_at TEXT,
    result_note  TEXT
);
CREATE INDEX idx_assistant_tasks_tenant ON assistant_tasks(tenant_id, status);

CREATE TABLE student_notes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id    INTEGER NOT NULL REFERENCES tenants(id),
    student_id   INTEGER NOT NULL REFERENCES students(id),
    author_id    INTEGER NOT NULL REFERENCES users(id),
    kind         TEXT    NOT NULL DEFAULT 'GENERAL', -- CALL_PARENT, ABSENCE, ACADEMIC, BEHAVIOR, GENERAL
    body         TEXT    NOT NULL,
    follow_up_on TEXT,
    status       TEXT    NOT NULL DEFAULT 'OPEN',    -- OPEN, RESOLVED
    created_at   TEXT    NOT NULL,
    resolved_at  TEXT
);
CREATE INDEX idx_student_notes_student ON student_notes(tenant_id, student_id);
CREATE INDEX idx_student_notes_open ON student_notes(tenant_id, status);
