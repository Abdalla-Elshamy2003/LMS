-- A teacher sells their teaching by school year and subject ("أولى ثانوي فيزياء"), for a number of months they set:
-- one subscription opens everything the teacher has for that year and subject, including courses added later, until
-- the period ends. Paid the manual way (InstaPay / Vodafone Cash to the teacher), opened by the teacher or a code.
CREATE TABLE subscription_plans (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT  NOT NULL REFERENCES tenants(id),
    year_key         TEXT    NOT NULL,
    year_label       TEXT    NOT NULL,
    subject_key      TEXT    NOT NULL,
    subject          TEXT    NOT NULL,
    price            NUMERIC(10,2),               -- NULL: the teacher hasn't priced it yet ("السعر عند المدرس")
    discount_percent INT     NOT NULL DEFAULT 0,
    months           INT     NOT NULL DEFAULT 2,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TEXT    NOT NULL,
    updated_at       TEXT    NOT NULL,
    UNIQUE (tenant_id, year_key, subject_key)
);

-- One row per period. A renewal is a new row that starts when the current one ends; months and price are fixed on
-- the row when it starts, so a later price change never rewrites what a student already paid for.
CREATE TABLE plan_subscriptions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    plan_id      BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    student_id   BIGINT NOT NULL REFERENCES students(id),
    status       TEXT   NOT NULL,                 -- PENDING, ACTIVE, ENDED, CANCELLED
    months       INT    NOT NULL,
    price        NUMERIC(10,2),
    starts_at    TEXT,
    ends_at      TEXT,
    source       TEXT   NOT NULL,                 -- REQUEST, CODE, TEACHER
    code_id      BIGINT,
    requested_at TEXT   NOT NULL,
    activated_at TEXT,
    activated_by BIGINT REFERENCES users(id),
    reminded_at  TEXT,
    ended_at     TEXT
);
CREATE INDEX idx_plan_subs_plan_student ON plan_subscriptions(plan_id, student_id);
CREATE INDEX idx_plan_subs_status ON plan_subscriptions(status);

CREATE TABLE plan_access_codes (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    plan_id            BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    code               TEXT   NOT NULL UNIQUE,
    status             TEXT   NOT NULL DEFAULT 'UNUSED', -- UNUSED, USED, REVOKED
    created_by         BIGINT NOT NULL REFERENCES users(id),
    created_at         TEXT   NOT NULL,
    used_by_student_id BIGINT REFERENCES students(id),
    used_at            TEXT
);
CREATE INDEX idx_plan_codes_plan ON plan_access_codes(plan_id);

-- The enrollments a plan opened for a student, so the plan running out closes exactly those — never a course the
-- student has from a package, a course code or for free.
CREATE TABLE plan_enrollments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id       BIGINT NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    student_id    BIGINT NOT NULL REFERENCES students(id),
    enrollment_id BIGINT NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    UNIQUE (plan_id, enrollment_id)
);
CREATE INDEX idx_plan_enrollments_student ON plan_enrollments(plan_id, student_id);
CREATE INDEX idx_plan_enrollments_enrollment ON plan_enrollments(enrollment_id);
