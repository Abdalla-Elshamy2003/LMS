-- Invoices: every payment a student makes now goes through one, so the amount, the method's fee and the total are
-- fixed by the server and shown the same way everywhere (the invoice page, head office's review, the reports).

-- A method's fee is added on top of the price (Vodafone Cash: 100 → 110). WhatsApp is where manual payers send the
-- screenshot of their transfer.
ALTER TABLE payment_methods ADD COLUMN fee_percent NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE payment_methods ADD COLUMN whatsapp TEXT NOT NULL DEFAULT '';

-- The owner's own numbers and fee (2026-10-02), filled only where head office hasn't set anything yet.
UPDATE payment_methods SET fee_percent = 10 WHERE code = 'VODAFONE_CASH';
UPDATE payment_methods SET account = '01115978493', enabled = TRUE WHERE code IN ('VODAFONE_CASH', 'INSTAPAY') AND account = '';
UPDATE payment_methods SET whatsapp = '01115978493' WHERE code IN ('VODAFONE_CASH', 'INSTAPAY') AND whatsapp = '';
-- Fawry is paid through the Fawaterak gateway (a reference code the student pays at any Fawry outlet); it shows only
-- once the gateway's keys are configured on the server.
UPDATE payment_methods SET enabled = TRUE, account = 'Fawaterak' WHERE code = 'FAWRY' AND account = '';

-- A teacher can sell a year and subject for several lengths, each at its own price: the plan's own months and price
-- stay the first choice, these are the others, as [{"months": 6, "price": 500}, ...].
ALTER TABLE subscription_plans ADD COLUMN extra_options_json TEXT NOT NULL DEFAULT '[]';

CREATE TABLE payment_invoices (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number               TEXT          NOT NULL UNIQUE,
    tenant_id            BIGINT        NOT NULL REFERENCES tenants(id),
    student_id           BIGINT        NOT NULL REFERENCES students(id),
    plan_subscription_id BIGINT        NOT NULL REFERENCES plan_subscriptions(id) ON DELETE CASCADE,
    description          TEXT          NOT NULL,
    months               INT           NOT NULL,
    method_code          TEXT          NOT NULL,
    base_amount          NUMERIC(10,2) NOT NULL,
    fee_percent          NUMERIC(5,2)  NOT NULL DEFAULT 0,
    fee_amount           NUMERIC(10,2) NOT NULL DEFAULT 0,
    total                NUMERIC(10,2) NOT NULL,
    status               TEXT          NOT NULL DEFAULT 'UNPAID', -- UNPAID, AWAITING_REVIEW, PAID, EXPIRED, CANCELLED
    note                 TEXT          NOT NULL DEFAULT '',
    gateway              TEXT,
    gateway_invoice_id   TEXT,
    gateway_invoice_key  TEXT,
    fawry_code           TEXT,
    expires_at           TIMESTAMPTZ,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_invoices_student ON payment_invoices(student_id);
CREATE INDEX idx_payment_invoices_subscription ON payment_invoices(plan_subscription_id);
CREATE INDEX idx_payment_invoices_gateway ON payment_invoices(gateway_invoice_id);

ALTER TABLE payment_submissions ADD COLUMN invoice_id BIGINT REFERENCES payment_invoices(id) ON DELETE SET NULL;
