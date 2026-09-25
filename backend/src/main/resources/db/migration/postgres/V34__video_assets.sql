-- Lesson videos uploaded straight from the teacher's browser to object storage (Cloudflare R2), in parts, without
-- passing through the backend — or to the server's own disk, the same way, until R2 is set up. A video is playable
-- as soon as its upload completes; once turned into HLS (several qualities, AES-128 encrypted segments) it streams
-- that way instead. The encryption key is only ever handed to a live, single-device watch session.
CREATE TABLE video_assets (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    lesson_id    BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    uploaded_by  BIGINT NOT NULL REFERENCES users(id),
    title        TEXT   NOT NULL DEFAULT '',
    storage      TEXT   NOT NULL,               -- LOCAL, R2
    object_key   TEXT   NOT NULL UNIQUE,        -- the original upload
    file_name    TEXT   NOT NULL DEFAULT '',
    content_type TEXT   NOT NULL,
    size_bytes   BIGINT NOT NULL,
    upload_id    TEXT,                          -- the multipart upload while it runs
    part_size    BIGINT NOT NULL,
    parts        INT    NOT NULL,
    status       TEXT   NOT NULL,               -- UPLOADING, READY, QUEUED, PROCESSING, STREAMING, ABORTED
    hls_prefix   TEXT,
    hls_key      TEXT,                          -- AES-128 key (hex); never leaves the server except to a live session
    duration_sec INT,
    error        TEXT   NOT NULL DEFAULT '',
    created_at   TEXT   NOT NULL,
    completed_at TEXT,
    processed_at TEXT
);
CREATE INDEX idx_video_assets_status ON video_assets(status);

ALTER TABLE lesson_materials ADD COLUMN video_asset_id BIGINT REFERENCES video_assets(id) ON DELETE SET NULL;
