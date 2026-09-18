-- =====================================================================
-- Manarah LMS - baseline schema (V1) - PostgreSQL.
-- All timestamps are ISO-8601 TEXT written by Hibernate (kept as TEXT for parity with the
-- 'sqlite' dev profile and because ISO-8601 strings already sort/compare correctly).
-- Money stored as DECIMAL/NUMERIC -> BigDecimal. IDs are BIGINT GENERATED ALWAYS AS IDENTITY.
-- Real boolean flags use native BOOLEAN; everything else that was INTEGER in the SQLite
-- baseline (counters, positions, ratings) stays INTEGER.
-- Multi-tenant: tenant_id on every business table. Additive migrations only.
-- =====================================================================

-- ---------- Tenancy & organisation ----------
CREATE TABLE tenants (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         TEXT    NOT NULL,
    slug         TEXT    NOT NULL UNIQUE,
    plan         TEXT    NOT NULL DEFAULT 'STANDARD',
    status       TEXT    NOT NULL DEFAULT 'ACTIVE',
    logo_url     TEXT,
    primary_color TEXT,
    created_at   TEXT    NOT NULL
);

CREATE TABLE branches (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    name         TEXT    NOT NULL,
    address      TEXT,
    phone        TEXT,
    status       TEXT    NOT NULL DEFAULT 'ACTIVE',
    created_at   TEXT    NOT NULL
);
CREATE INDEX idx_branches_tenant ON branches(tenant_id);

CREATE TABLE rooms (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    branch_id     BIGINT NOT NULL REFERENCES branches(id),
    name          TEXT    NOT NULL,
    capacity      INTEGER NOT NULL DEFAULT 30,
    has_projector BOOLEAN NOT NULL DEFAULT FALSE,
    has_ac        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TEXT    NOT NULL
);
CREATE INDEX idx_rooms_tenant ON rooms(tenant_id);

-- ---------- Identity & access ----------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    branch_id     BIGINT REFERENCES branches(id),
    full_name     TEXT    NOT NULL,
    email         TEXT    NOT NULL,
    phone         TEXT,
    password_hash TEXT    NOT NULL,
    role          TEXT    NOT NULL,   -- SUPER_ADMIN, BRANCH_ADMIN, ACADEMIC_MANAGER, TEACHER, ASSISTANT, STUDENT, PARENT, ACCOUNTANT, SUPPORT, CONTENT_MANAGER
    status        TEXT    NOT NULL DEFAULT 'ACTIVE',
    avatar_url    TEXT,
    last_login_at TEXT,
    created_at    TEXT    NOT NULL,
    UNIQUE(tenant_id, email)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_role ON users(tenant_id, role);

-- ---------- Students & guardians ----------
CREATE TABLE students (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT NOT NULL REFERENCES tenants(id),
    branch_id        BIGINT NOT NULL REFERENCES branches(id),
    user_id          BIGINT REFERENCES users(id),      -- login account (optional)
    code             TEXT    NOT NULL,                   -- student card id
    full_name        TEXT    NOT NULL,
    national_id      TEXT,
    birth_date       TEXT,
    gender           TEXT,
    grade_level      TEXT,                               -- e.g. Secondary
    grade            TEXT,                               -- e.g. Grade 12
    school           TEXT,
    phone            TEXT,
    status           TEXT    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, SUSPENDED, GRADUATED, DROPPED, TRIAL, PENDING_PAYMENT
    academic_status  TEXT    NOT NULL DEFAULT 'AVERAGE', -- EXCELLENT, GOOD, AVERAGE, NEEDS_ATTENTION, AT_RISK  (materialised)
    avg_score        REAL    NOT NULL DEFAULT 0,
    attendance_rate  REAL    NOT NULL DEFAULT 0,
    homework_rate    REAL    NOT NULL DEFAULT 0,
    overall_percent  REAL    NOT NULL DEFAULT 0,
    notes            TEXT,
    created_at       TEXT    NOT NULL,
    UNIQUE(tenant_id, code)
);
CREATE INDEX idx_students_tenant ON students(tenant_id);
CREATE INDEX idx_students_branch ON students(branch_id);
CREATE INDEX idx_students_status ON students(tenant_id, academic_status);

CREATE TABLE guardians (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    user_id     BIGINT REFERENCES users(id),  -- parent login (optional)
    full_name   TEXT    NOT NULL,
    phone       TEXT,
    email       TEXT,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_guardians_tenant ON guardians(tenant_id);

CREATE TABLE student_guardians (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    guardian_id BIGINT NOT NULL REFERENCES guardians(id),
    relation    TEXT,   -- FATHER, MOTHER, ...
    UNIQUE(student_id, guardian_id)
);

-- ---------- Courses / content ----------
CREATE TABLE courses (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    branch_id   BIGINT NOT NULL REFERENCES branches(id),
    teacher_id  BIGINT REFERENCES users(id),
    title       TEXT    NOT NULL,
    subject     TEXT,
    grade_level TEXT,
    description TEXT,
    price       DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      TEXT    NOT NULL DEFAULT 'ACTIVE',
    cover_url   TEXT,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_courses_tenant ON courses(tenant_id);
CREATE INDEX idx_courses_teacher ON courses(teacher_id);

CREATE TABLE course_modules (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    course_id   BIGINT NOT NULL REFERENCES courses(id),
    title       TEXT    NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_modules_course ON course_modules(course_id);

CREATE TABLE lessons (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    module_id    BIGINT NOT NULL REFERENCES course_modules(id),
    title        TEXT    NOT NULL,
    position     INTEGER NOT NULL DEFAULT 0,
    duration_min INTEGER NOT NULL DEFAULT 0,
    content_text TEXT,
    created_at   TEXT    NOT NULL
);
CREATE INDEX idx_lessons_module ON lessons(module_id);

CREATE TABLE lesson_materials (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    lesson_id    BIGINT NOT NULL REFERENCES lessons(id),
    type         TEXT    NOT NULL,  -- VIDEO, PDF, PPT, DOC, IMAGE, AUDIO, LINK, ASSIGNMENT
    title        TEXT    NOT NULL,
    url          TEXT,              -- external link
    file_key     TEXT,              -- stored file reference
    size_bytes   INTEGER,
    duration_sec INTEGER,
    created_at   TEXT    NOT NULL
);
CREATE INDEX idx_materials_lesson ON lesson_materials(lesson_id);

-- lesson watch / open tracking (insight)
CREATE TABLE lesson_progress (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    lesson_id       BIGINT NOT NULL REFERENCES lessons(id),
    student_id      BIGINT NOT NULL REFERENCES students(id),
    watched_seconds INTEGER NOT NULL DEFAULT 0,
    last_position   INTEGER NOT NULL DEFAULT 0,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    views           INTEGER NOT NULL DEFAULT 0,
    opened_at       TEXT,
    completed_at    TEXT,
    updated_at      TEXT    NOT NULL,
    UNIQUE(lesson_id, student_id)
);
CREATE INDEX idx_progress_student ON lesson_progress(student_id);

-- ---------- Groups, rooms, enrollment ----------
CREATE TABLE study_groups (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    course_id     BIGINT NOT NULL REFERENCES courses(id),
    teacher_id    BIGINT REFERENCES users(id),
    room_id       BIGINT REFERENCES rooms(id),
    name          TEXT    NOT NULL,
    schedule_text TEXT,
    capacity      INTEGER NOT NULL DEFAULT 30,
    created_at    TEXT    NOT NULL
);
CREATE INDEX idx_groups_course ON study_groups(course_id);

CREATE TABLE enrollments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    course_id   BIGINT NOT NULL REFERENCES courses(id),
    group_id    BIGINT REFERENCES study_groups(id),
    status      TEXT    NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TEXT    NOT NULL,
    UNIQUE(student_id, course_id)
);
CREATE INDEX idx_enroll_course ON enrollments(course_id);
CREATE INDEX idx_enroll_student ON enrollments(student_id);

-- ---------- Attendance ----------
CREATE TABLE class_sessions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    course_id       BIGINT NOT NULL REFERENCES courses(id),
    group_id        BIGINT REFERENCES study_groups(id),
    teacher_id      BIGINT REFERENCES users(id),
    room_id         BIGINT REFERENCES rooms(id),
    title           TEXT    NOT NULL,
    scheduled_start TEXT,
    scheduled_end   TEXT,
    qr_token        TEXT,
    qr_expires_at   TEXT,
    status          TEXT    NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, OPEN, CLOSED
    created_at      TEXT    NOT NULL
);
CREATE INDEX idx_sessions_course ON class_sessions(course_id);

CREATE TABLE attendance_records (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    session_id   BIGINT NOT NULL REFERENCES class_sessions(id),
    student_id   BIGINT NOT NULL REFERENCES students(id),
    status       TEXT    NOT NULL,  -- PRESENT, ABSENT, LATE, EXCUSED
    arrival_time TEXT,
    leave_time   TEXT,
    late_minutes INTEGER NOT NULL DEFAULT 0,
    reason       TEXT,
    method       TEXT    NOT NULL DEFAULT 'MANUAL', -- MANUAL, QR
    recorded_by  BIGINT REFERENCES users(id),
    created_at   TEXT    NOT NULL,
    UNIQUE(session_id, student_id)
);
CREATE INDEX idx_attendance_student ON attendance_records(student_id);
CREATE INDEX idx_attendance_session ON attendance_records(session_id);

-- ---------- Question bank & exams ----------
CREATE TABLE questions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    subject            TEXT,
    chapter            TEXT,
    lesson             TEXT,
    difficulty         TEXT    NOT NULL DEFAULT 'MEDIUM', -- EASY, MEDIUM, HARD
    type               TEXT    NOT NULL, -- MCQ, TRUE_FALSE, MULTI_SELECT, FILL_BLANK, SHORT_ANSWER, ESSAY, NUMERIC
    stem               TEXT    NOT NULL,
    points             REAL    NOT NULL DEFAULT 1,
    correct_answer     TEXT,   -- for FILL_BLANK / NUMERIC / SHORT_ANSWER
    learning_objective TEXT,
    tags               TEXT,
    created_by         BIGINT REFERENCES users(id),
    created_at         TEXT    NOT NULL
);
CREATE INDEX idx_questions_tenant ON questions(tenant_id);
CREATE INDEX idx_questions_filter ON questions(tenant_id, subject, difficulty, type);

CREATE TABLE question_options (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    question_id BIGINT NOT NULL REFERENCES questions(id),
    text        TEXT    NOT NULL,
    is_correct  BOOLEAN NOT NULL DEFAULT FALSE,
    position    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_options_question ON question_options(question_id);

CREATE TABLE exams (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT NOT NULL REFERENCES tenants(id),
    course_id        BIGINT REFERENCES courses(id),
    title            TEXT    NOT NULL,
    description      TEXT,
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    total_points     REAL    NOT NULL DEFAULT 0,
    pass_percent     REAL    NOT NULL DEFAULT 50,
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    shuffle_options  BOOLEAN NOT NULL DEFAULT TRUE,
    fullscreen       BOOLEAN NOT NULL DEFAULT FALSE,
    disable_copy     BOOLEAN NOT NULL DEFAULT FALSE,
    detect_tab_switch BOOLEAN NOT NULL DEFAULT FALSE,
    start_at         TEXT,
    end_at           TEXT,
    status           TEXT    NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, CLOSED
    created_by       BIGINT REFERENCES users(id),
    created_at       TEXT    NOT NULL
);
CREATE INDEX idx_exams_course ON exams(course_id);

CREATE TABLE exam_questions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenants(id),
    exam_id        BIGINT NOT NULL REFERENCES exams(id),
    question_id    BIGINT NOT NULL REFERENCES questions(id),
    position       INTEGER NOT NULL DEFAULT 0,
    points_override REAL,
    UNIQUE(exam_id, question_id)
);
CREATE INDEX idx_examq_exam ON exam_questions(exam_id);

CREATE TABLE student_exams (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    exam_id            BIGINT NOT NULL REFERENCES exams(id),
    student_id         BIGINT NOT NULL REFERENCES students(id),
    status             TEXT    NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED, IN_PROGRESS, SUBMITTED, GRADED
    started_at         TEXT,
    submitted_at       TEXT,
    score              REAL    NOT NULL DEFAULT 0,
    max_score          REAL    NOT NULL DEFAULT 0,
    tab_switches       INTEGER NOT NULL DEFAULT 0,
    question_order     TEXT,   -- JSON ordered question ids (randomised per student)
    needs_manual_grade BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TEXT    NOT NULL,
    UNIQUE(exam_id, student_id)
);
CREATE INDEX idx_studentexam_student ON student_exams(student_id);

CREATE TABLE student_answers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    student_exam_id BIGINT NOT NULL REFERENCES student_exams(id),
    question_id     BIGINT NOT NULL REFERENCES questions(id),
    answer_text     TEXT,
    selected_options TEXT,  -- JSON array of option ids
    is_correct      BOOLEAN,
    awarded_points  REAL    NOT NULL DEFAULT 0,
    feedback        TEXT,
    graded_by       BIGINT REFERENCES users(id),
    UNIQUE(student_exam_id, question_id)
);
CREATE INDEX idx_answers_studentexam ON student_answers(student_exam_id);

-- ---------- Homework ----------
CREATE TABLE assignments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    course_id   BIGINT NOT NULL REFERENCES courses(id),
    title       TEXT    NOT NULL,
    description TEXT,
    start_at    TEXT,
    deadline    TEXT,
    max_score   REAL    NOT NULL DEFAULT 100,
    created_by  BIGINT REFERENCES users(id),
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_assignments_course ON assignments(course_id);

CREATE TABLE submissions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    assignment_id BIGINT NOT NULL REFERENCES assignments(id),
    student_id    BIGINT NOT NULL REFERENCES students(id),
    text          TEXT,
    file_key      TEXT,
    status        TEXT    NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED, LATE, MISSING, GRADED
    submitted_at  TEXT,
    score         REAL,
    feedback      TEXT,
    graded_by     BIGINT REFERENCES users(id),
    graded_at     TEXT,
    UNIQUE(assignment_id, student_id)
);
CREATE INDEX idx_submissions_student ON submissions(student_id);

-- ---------- Gradebook ledger ----------
CREATE TABLE grade_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    course_id   BIGINT REFERENCES courses(id),
    title       TEXT    NOT NULL,
    category    TEXT    NOT NULL DEFAULT 'OTHER', -- QUIZ, EXAM, HOMEWORK, MIDTERM, FINAL, OTHER
    score       REAL    NOT NULL DEFAULT 0,
    max_score   REAL    NOT NULL DEFAULT 0,
    weight      REAL    NOT NULL DEFAULT 1,
    source_type TEXT,   -- EXAM, HOMEWORK, MANUAL
    source_id   BIGINT,
    recorded_at TEXT    NOT NULL
);
CREATE INDEX idx_grades_student ON grade_items(student_id);
CREATE INDEX idx_grades_course ON grade_items(course_id);

-- ---------- Payments ----------
CREATE TABLE invoices (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    course_id   BIGINT REFERENCES courses(id),
    title       TEXT    NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status      TEXT    NOT NULL DEFAULT 'PENDING', -- PENDING, PARTIAL, PAID, REFUNDED
    due_date    TEXT,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_invoices_student ON invoices(student_id);

CREATE TABLE installments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    invoice_id  BIGINT NOT NULL REFERENCES invoices(id),
    seq         INTEGER NOT NULL,
    amount      DECIMAL(12,2) NOT NULL DEFAULT 0,
    due_date    TEXT,
    status      TEXT    NOT NULL DEFAULT 'PENDING', -- PENDING, PAID, OVERDUE
    paid_at     TEXT
);
CREATE INDEX idx_installments_invoice ON installments(invoice_id);

CREATE TABLE payments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenants(id),
    invoice_id     BIGINT NOT NULL REFERENCES invoices(id),
    installment_id BIGINT REFERENCES installments(id),
    amount         DECIMAL(12,2) NOT NULL DEFAULT 0,
    method         TEXT    NOT NULL DEFAULT 'CASH', -- CASH, CARD, ONLINE, TRANSFER
    reference      TEXT,
    recorded_by    BIGINT REFERENCES users(id),
    paid_at        TEXT    NOT NULL
);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);

-- ---------- Notifications & rules ----------
CREATE TABLE notifications (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         BIGINT NOT NULL REFERENCES tenants(id),
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    title             TEXT    NOT NULL,
    body              TEXT,
    category          TEXT    NOT NULL DEFAULT 'GENERAL',
    channel           TEXT    NOT NULL DEFAULT 'IN_APP', -- IN_APP, WHATSAPP, SMS, EMAIL, PUSH
    status            TEXT    NOT NULL DEFAULT 'SENT',   -- PENDING, SENT, READ, FAILED
    entity_type       TEXT,
    entity_id         BIGINT,
    read_at           TEXT,
    created_at        TEXT    NOT NULL
);
CREATE INDEX idx_notif_recipient ON notifications(recipient_user_id, status);

CREATE TABLE notification_rules (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    name         TEXT    NOT NULL,
    trigger_type TEXT    NOT NULL, -- STUDENT_ABSENT, LOW_SCORE, HOMEWORK_MISSED, RISK_ESCALATED, INSTALLMENT_DUE
    threshold    REAL,
    channels     TEXT    NOT NULL DEFAULT 'IN_APP', -- CSV of channels
    notify_parent BOOLEAN NOT NULL DEFAULT TRUE,
    notify_teacher BOOLEAN NOT NULL DEFAULT FALSE,
    template     TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TEXT    NOT NULL
);
CREATE INDEX idx_rules_tenant ON notification_rules(tenant_id, trigger_type);

-- ---------- Risk, timeline, audit ----------
CREATE TABLE risk_assessments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    level       TEXT    NOT NULL, -- NONE, LOW, MEDIUM, HIGH, AT_RISK
    score       REAL    NOT NULL DEFAULT 0,
    reasons     TEXT,   -- JSON array of reasons
    assessed_at TEXT    NOT NULL
);
CREATE INDEX idx_risk_student ON risk_assessments(student_id);

CREATE TABLE student_timeline (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    type        TEXT    NOT NULL, -- ATTENDANCE, EXAM, HOMEWORK, PAYMENT, RISK, NOTE, ENROLLMENT
    title       TEXT    NOT NULL,
    detail      TEXT,
    icon        TEXT,
    occurred_at TEXT    NOT NULL
);
CREATE INDEX idx_timeline_student ON student_timeline(student_id, occurred_at);

CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    actor_user_id BIGINT REFERENCES users(id),
    actor_name   TEXT,
    action       TEXT    NOT NULL,
    entity_type  TEXT,
    entity_id    BIGINT,
    old_value    TEXT,
    new_value    TEXT,
    ip_address   TEXT,
    created_at   TEXT    NOT NULL
);
CREATE INDEX idx_audit_tenant ON audit_logs(tenant_id, created_at);

-- ---------- Communication & calendar ----------
CREATE TABLE messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenants(id),
    sender_user_id  BIGINT NOT NULL REFERENCES users(id),
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    subject         TEXT,
    body            TEXT    NOT NULL,
    read_at         TEXT,
    created_at      TEXT    NOT NULL
);
CREATE INDEX idx_messages_recipient ON messages(recipient_user_id);

CREATE TABLE announcements (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    branch_id   BIGINT REFERENCES branches(id),
    audience    TEXT    NOT NULL DEFAULT 'ALL', -- ALL, STUDENTS, TEACHERS, PARENTS, GRADE
    audience_filter TEXT,
    title       TEXT    NOT NULL,
    body        TEXT    NOT NULL,
    created_by  BIGINT REFERENCES users(id),
    created_at  TEXT    NOT NULL
);

CREATE TABLE calendar_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    branch_id   BIGINT REFERENCES branches(id),
    type        TEXT    NOT NULL, -- LECTURE, EXAM, DEADLINE, EVENT, HOLIDAY, MEETING
    title       TEXT    NOT NULL,
    start_at    TEXT    NOT NULL,
    end_at      TEXT,
    related_type TEXT,
    related_id  BIGINT,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_calendar_tenant ON calendar_events(tenant_id, start_at);

-- ---------- Gamification & certificates & evaluation ----------
CREATE TABLE student_points (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    points      INTEGER NOT NULL DEFAULT 0,
    level       TEXT    NOT NULL DEFAULT 'BRONZE',
    updated_at  TEXT    NOT NULL,
    UNIQUE(student_id)
);

CREATE TABLE point_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    points      INTEGER NOT NULL,
    reason      TEXT,
    created_at  TEXT    NOT NULL
);
CREATE INDEX idx_pointevents_student ON point_events(student_id);

CREATE TABLE badges (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    code        TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    icon        TEXT,
    description TEXT,
    UNIQUE(tenant_id, code)
);

CREATE TABLE student_badges (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    badge_id    BIGINT NOT NULL REFERENCES badges(id),
    awarded_at  TEXT    NOT NULL,
    UNIQUE(student_id, badge_id)
);

CREATE TABLE certificates (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    student_id  BIGINT NOT NULL REFERENCES students(id),
    course_id   BIGINT REFERENCES courses(id),
    grade       TEXT,
    serial      TEXT    NOT NULL,
    verify_code TEXT    NOT NULL,
    issued_at   TEXT    NOT NULL,
    UNIQUE(serial)
);

CREATE TABLE teacher_evaluations (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenants(id),
    teacher_id     BIGINT NOT NULL REFERENCES users(id),
    student_id     BIGINT REFERENCES students(id),
    explanation    INTEGER,
    engagement     INTEGER,
    content_quality INTEGER,
    difficulty     INTEGER,
    overall        REAL,
    comment        TEXT,
    anonymous      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TEXT    NOT NULL
);
CREATE INDEX idx_eval_teacher ON teacher_evaluations(teacher_id);
