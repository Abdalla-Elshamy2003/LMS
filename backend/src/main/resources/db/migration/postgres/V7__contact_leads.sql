-- V7: prospective-customer messages from the public "تواصل معنا" contact form

CREATE TABLE contact_leads (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL,
    full_name          TEXT    NOT NULL,
    email              TEXT    NOT NULL,
    phone              TEXT,
    institution_type   TEXT,
    expected_students  TEXT,
    message            TEXT,
    status             TEXT    NOT NULL DEFAULT 'NEW',
    created_at         TEXT    NOT NULL
);

CREATE INDEX idx_contact_leads_tenant ON contact_leads(tenant_id);
