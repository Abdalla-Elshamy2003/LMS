-- One price for a whole teacher package. Paid the same manual way as a course (InstaPay / Vodafone Cash to head
-- office), confirmed by head office, which then hands the student a one-time package code — or grants the package
-- directly. A subscription joins the student to every teacher in the package and opens all of their courses.
ALTER TABLE teacher_bundles ADD COLUMN price NUMERIC(10,2);
ALTER TABLE teacher_bundles ADD COLUMN instapay_number TEXT NOT NULL DEFAULT '';
ALTER TABLE teacher_bundles ADD COLUMN vodafone_cash_number TEXT NOT NULL DEFAULT '';
ALTER TABLE teacher_bundles ADD COLUMN payment_note TEXT NOT NULL DEFAULT '';

CREATE TABLE bundle_access_codes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bundle_id       BIGINT NOT NULL REFERENCES teacher_bundles(id) ON DELETE CASCADE,
    code            TEXT   NOT NULL UNIQUE,
    status          TEXT   NOT NULL DEFAULT 'UNUSED', -- UNUSED, USED, REVOKED
    created_by      BIGINT NOT NULL REFERENCES users(id),
    created_at      TEXT   NOT NULL,
    used_by_user_id BIGINT REFERENCES users(id),
    used_at         TEXT
);
CREATE INDEX idx_bundle_codes_bundle ON bundle_access_codes(bundle_id);

-- user_id is the account the student signs in with (never a linked seat).
CREATE TABLE bundle_subscriptions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bundle_id   BIGINT NOT NULL REFERENCES teacher_bundles(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    status      TEXT   NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED
    source      TEXT   NOT NULL,                  -- CODE, ADMIN
    code_id     BIGINT REFERENCES bundle_access_codes(id),
    granted_by  BIGINT REFERENCES users(id),
    created_at  TEXT   NOT NULL,
    cancelled_at TEXT,
    UNIQUE (bundle_id, user_id)
);

-- The enrollments a package subscription opened, so cancelling it closes exactly those and never a course the
-- student bought on its own.
CREATE TABLE bundle_enrollments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES bundle_subscriptions(id) ON DELETE CASCADE,
    enrollment_id   BIGINT NOT NULL REFERENCES enrollments(id),
    UNIQUE (subscription_id, enrollment_id)
);
