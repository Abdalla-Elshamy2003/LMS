-- A tutoring center (سنتر): its own tenant, run by one CENTER_ADMIN account the head office creates. The center adds
-- its teachers, each teacher's groups (subject, days, time, the fee per session) and the students of every group. A
-- student row is one student in one group, so the QR on their card says exactly which teacher, group and time it is for.
CREATE TABLE centers (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         BIGINT  NOT NULL UNIQUE REFERENCES tenants(id),
    manager_tenant_id BIGINT  NOT NULL REFERENCES tenants(id),
    owner_user_id     BIGINT  NOT NULL REFERENCES users(id),
    name              TEXT    NOT NULL,
    slug              TEXT    NOT NULL UNIQUE,
    phone             TEXT,
    address           TEXT,
    auto_pay          BOOLEAN NOT NULL DEFAULT TRUE, -- a scan also records the session fee as collected
    next_student_code INT     NOT NULL DEFAULT 1001, -- the short number printed under each QR
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL
);
CREATE INDEX idx_centers_manager ON centers(manager_tenant_id);

CREATE TABLE center_teachers (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT  NOT NULL REFERENCES tenants(id),
    name           TEXT    NOT NULL,
    subject        TEXT    NOT NULL,
    phone          TEXT,
    center_percent INT     NOT NULL DEFAULT 0,       -- the center's cut of what the teacher's sessions collect
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TEXT    NOT NULL
);
CREATE INDEX idx_center_teachers_tenant ON center_teachers(tenant_id);

CREATE TABLE center_groups (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL REFERENCES tenants(id),
    teacher_id    BIGINT        NOT NULL REFERENCES center_teachers(id),
    name          TEXT          NOT NULL,
    subject       TEXT          NOT NULL,
    grade         TEXT,
    days          TEXT          NOT NULL,            -- comma list, 0 = Sunday … 6 = Saturday (as schedule_slots)
    start_time    TEXT          NOT NULL,            -- HH:mm, Cairo time
    end_time      TEXT,
    room          TEXT,
    session_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TEXT          NOT NULL
);
CREATE INDEX idx_center_groups_tenant ON center_groups(tenant_id, teacher_id);

CREATE TABLE center_students (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT  NOT NULL REFERENCES tenants(id),
    group_id     BIGINT  NOT NULL REFERENCES center_groups(id),
    code         TEXT    NOT NULL,                   -- typed at the desk when a card won't scan
    token        TEXT    NOT NULL UNIQUE,            -- what the QR carries; a new card replaces it
    name         TEXT    NOT NULL,
    phone        TEXT,
    parent_phone TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    joined_on    TEXT    NOT NULL,
    created_at   TEXT    NOT NULL,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_center_students_group ON center_students(tenant_id, group_id);

-- One row per group per day it actually met. The fee is fixed on the row, so changing a group's price later never
-- rewrites what past sessions cost.
CREATE TABLE center_sessions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES tenants(id),
    group_id     BIGINT        NOT NULL REFERENCES center_groups(id),
    session_date TEXT          NOT NULL,             -- yyyy-MM-dd, Cairo
    price        NUMERIC(10,2) NOT NULL,
    created_at   TEXT          NOT NULL,
    UNIQUE (group_id, session_date)
);
CREATE INDEX idx_center_sessions_date ON center_sessions(tenant_id, session_date);

-- Presence only: a student of the group with no row for a session was absent.
CREATE TABLE center_attendance (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  BIGINT        NOT NULL REFERENCES tenants(id),
    session_id BIGINT        NOT NULL REFERENCES center_sessions(id) ON DELETE CASCADE,
    student_id BIGINT        NOT NULL REFERENCES center_students(id),
    method     TEXT          NOT NULL,               -- QR, CODE, MANUAL
    amount     NUMERIC(10,2) NOT NULL,
    paid       BOOLEAN       NOT NULL DEFAULT FALSE,
    scanned_at TEXT          NOT NULL,
    scanned_by BIGINT REFERENCES users(id),
    UNIQUE (session_id, student_id)
);
CREATE INDEX idx_center_attendance_student ON center_attendance(student_id);

-- Books and notes (ملازم) the center sells, each with the day it comes out. Students reserve from their QR page.
CREATE TABLE center_books (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES tenants(id),
    teacher_id   BIGINT REFERENCES center_teachers(id), -- NULL: for every student of the center
    title        TEXT          NOT NULL,
    description  TEXT,
    grade        TEXT,
    price        NUMERIC(10,2) NOT NULL DEFAULT 0,
    release_date TEXT          NOT NULL,
    stock        INT,                                -- NULL: no limit
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TEXT          NOT NULL
);
CREATE INDEX idx_center_books_tenant ON center_books(tenant_id);

CREATE TABLE center_book_reservations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL REFERENCES tenants(id),
    book_id      BIGINT        NOT NULL REFERENCES center_books(id),
    student_id   BIGINT        NOT NULL REFERENCES center_students(id),
    code         TEXT          NOT NULL UNIQUE,      -- the student shows it to collect the book
    status       TEXT          NOT NULL,             -- RESERVED, DELIVERED, CANCELLED
    price        NUMERIC(10,2) NOT NULL,
    source       TEXT          NOT NULL,             -- STUDENT (their QR page), CENTER
    reserved_at  TEXT          NOT NULL,
    delivered_at TEXT,
    cancelled_at TEXT
);
CREATE UNIQUE INDEX uq_center_reservation_open ON center_book_reservations(book_id, student_id) WHERE status <> 'CANCELLED';
CREATE INDEX idx_center_reservations_tenant ON center_book_reservations(tenant_id, status);
