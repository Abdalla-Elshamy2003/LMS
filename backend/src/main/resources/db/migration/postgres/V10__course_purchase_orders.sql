CREATE TABLE course_purchase_orders (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenants(id),
    user_id            BIGINT NOT NULL REFERENCES users(id),
    student_id         BIGINT NOT NULL REFERENCES students(id),
    course_id          BIGINT NOT NULL REFERENCES courses(id),
    reference          TEXT NOT NULL UNIQUE,
    amount             NUMERIC NOT NULL,
    currency           TEXT NOT NULL DEFAULT 'EGP',
    payment_method     TEXT,
    status             TEXT NOT NULL DEFAULT 'PENDING',
    provider_reference TEXT,
    paid_at            TEXT,
    created_at         TEXT NOT NULL
);
CREATE INDEX idx_purchase_orders_tenant ON course_purchase_orders(tenant_id, created_at);
CREATE INDEX idx_purchase_orders_user ON course_purchase_orders(tenant_id, user_id);
