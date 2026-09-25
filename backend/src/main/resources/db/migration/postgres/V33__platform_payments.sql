-- The platform takes the money: head office sets up how students pay (Vodafone Cash, InstaPay, Fawry) and reviews
-- every payment. A student pays a subscription the manual way, sends the transfer's reference (and a receipt photo),
-- and head office approves it, which starts the subscription. Each method stays off until head office fills it in.
CREATE TABLE payment_methods (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         TEXT    NOT NULL UNIQUE,            -- VODAFONE_CASH, INSTAPAY, FAWRY
    name         TEXT    NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    account      TEXT    NOT NULL DEFAULT '',        -- wallet number, InstaPay address or number, Fawry code
    account_name TEXT    NOT NULL DEFAULT '',        -- the name the student sees when sending, to check it's right
    instructions TEXT    NOT NULL DEFAULT '',
    sort_order   INT     NOT NULL DEFAULT 0,
    updated_at   TEXT    NOT NULL
);
INSERT INTO payment_methods (code, name, sort_order, updated_at) VALUES
    ('VODAFONE_CASH', 'فودافون كاش', 1, now()),
    ('INSTAPAY', 'إنستاباي', 2, now()),
    ('FAWRY', 'فوري', 3, now());

-- A payment a student sent for a subscription request, waiting for head office. The receipt photo is kept here, not
-- in public storage: only head office and the student who sent it can open it.
CREATE TABLE payment_submissions (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenants(id),
    student_id           BIGINT NOT NULL REFERENCES students(id),
    plan_subscription_id BIGINT NOT NULL REFERENCES plan_subscriptions(id) ON DELETE CASCADE,
    method_code          TEXT   NOT NULL,
    amount               NUMERIC(10,2),
    reference            TEXT   NOT NULL DEFAULT '',
    sender               TEXT   NOT NULL DEFAULT '',
    receipt_type         TEXT,
    receipt_data         TEXT,
    status               TEXT   NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED, APPROVED, REJECTED
    note                 TEXT   NOT NULL DEFAULT '',
    reviewed_by          BIGINT REFERENCES users(id),
    reviewed_at          TEXT,
    created_at           TEXT   NOT NULL
);
CREATE INDEX idx_payment_submissions_status ON payment_submissions(status);
CREATE INDEX idx_payment_submissions_sub ON payment_submissions(plan_subscription_id);
