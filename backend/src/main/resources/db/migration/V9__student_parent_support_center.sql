-- Student and guardian inquiries, complaints, and teacher conversations.

CREATE TABLE support_cases (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id            INTEGER NOT NULL REFERENCES tenants(id),
    created_by_user_id   INTEGER NOT NULL REFERENCES users(id),
    student_id           INTEGER REFERENCES students(id),
    course_id            INTEGER REFERENCES courses(id),
    assigned_teacher_id  INTEGER REFERENCES users(id),
    category             TEXT NOT NULL,
    priority             TEXT NOT NULL DEFAULT 'NORMAL',
    subject              TEXT NOT NULL,
    status               TEXT NOT NULL DEFAULT 'OPEN',
    last_message_at      TEXT NOT NULL,
    resolved_at          TEXT,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL
);

CREATE INDEX idx_support_cases_tenant ON support_cases(tenant_id, last_message_at);
CREATE INDEX idx_support_cases_creator ON support_cases(tenant_id, created_by_user_id);
CREATE INDEX idx_support_cases_teacher ON support_cases(tenant_id, assigned_teacher_id);
CREATE INDEX idx_support_cases_student ON support_cases(tenant_id, student_id);

CREATE TABLE support_messages (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id       INTEGER NOT NULL REFERENCES tenants(id),
    case_id         INTEGER NOT NULL REFERENCES support_cases(id),
    author_user_id  INTEGER NOT NULL REFERENCES users(id),
    body            TEXT NOT NULL,
    created_at      TEXT NOT NULL
);

CREATE INDEX idx_support_messages_case ON support_messages(tenant_id, case_id, created_at);
