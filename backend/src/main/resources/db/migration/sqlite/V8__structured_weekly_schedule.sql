CREATE TABLE schedule_slots (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id     INTEGER NOT NULL REFERENCES tenants(id),
    course_id     INTEGER NOT NULL REFERENCES courses(id),
    group_id      INTEGER REFERENCES study_groups(id),
    teacher_id    INTEGER REFERENCES users(id),
    room_id       INTEGER REFERENCES rooms(id),
    title         TEXT NOT NULL,
    day_of_week   INTEGER NOT NULL CHECK(day_of_week BETWEEN 0 AND 6),
    start_time    TEXT NOT NULL,
    end_time      TEXT NOT NULL,
    delivery_mode TEXT NOT NULL DEFAULT 'IN_PERSON',
    meeting_url   TEXT,
    color         TEXT NOT NULL DEFAULT '#0f766e',
    active        INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT NOT NULL
);
CREATE INDEX idx_schedule_tenant_day ON schedule_slots(tenant_id, day_of_week, start_time);
CREATE INDEX idx_schedule_course ON schedule_slots(course_id);
CREATE INDEX idx_schedule_teacher ON schedule_slots(teacher_id, day_of_week, start_time);
CREATE INDEX idx_schedule_room ON schedule_slots(room_id, day_of_week, start_time);
