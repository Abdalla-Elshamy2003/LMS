-- Cover images a teacher uploads for a course or a video lesson. Stored in the database like the
-- academy photo/cover, so they survive redeploys and need no object storage.
CREATE TABLE public_images (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    content_type TEXT   NOT NULL,
    data         TEXT   NOT NULL,
    created_by   BIGINT REFERENCES users(id),
    created_at   TEXT   NOT NULL
);
CREATE INDEX idx_public_images_tenant ON public_images(tenant_id);
