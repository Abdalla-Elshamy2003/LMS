-- V8: educational community forum — topics, replies, and likes

CREATE TABLE forum_topics (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT NOT NULL,
    course_id        BIGINT,
    author_user_id   BIGINT NOT NULL,
    author_name      TEXT    NOT NULL,
    title            TEXT    NOT NULL,
    body             TEXT    NOT NULL,
    pinned           BOOLEAN NOT NULL DEFAULT FALSE,
    reply_count      INTEGER NOT NULL DEFAULT 0,
    like_count       INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT    NOT NULL,
    last_activity_at TEXT    NOT NULL
);
CREATE INDEX idx_forum_topics_tenant ON forum_topics(tenant_id);
CREATE INDEX idx_forum_topics_course ON forum_topics(tenant_id, course_id);

CREATE TABLE forum_replies (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    topic_id       BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    author_name    TEXT    NOT NULL,
    body           TEXT    NOT NULL,
    created_at     TEXT    NOT NULL
);
CREATE INDEX idx_forum_replies_topic ON forum_replies(tenant_id, topic_id);

CREATE TABLE forum_likes (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    topic_id  BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    UNIQUE(topic_id, user_id)
);
