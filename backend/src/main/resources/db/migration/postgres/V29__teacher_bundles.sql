-- Teacher packages (باقات المدرسين): a group of teachers who join the platform together and are shown as one
-- package on the public home page, with a landing page of their own. A member can be listed before their teacher
-- space exists (subject + photo + optional intro video); linking an academy adds that teacher's page, videos
-- and courses to the package page.
CREATE TABLE teacher_bundles (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    manager_tenant_id BIGINT REFERENCES tenants(id),  -- head office that owns it; null until an admin first saves it
    slug              TEXT    NOT NULL UNIQUE,
    name              TEXT    NOT NULL,
    tagline           TEXT    NOT NULL DEFAULT '',
    description       TEXT    NOT NULL DEFAULT '',
    published         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE teacher_bundle_members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bundle_id       BIGINT  NOT NULL REFERENCES teacher_bundles(id) ON DELETE CASCADE,
    position        INTEGER NOT NULL,
    academy_id      BIGINT  REFERENCES teacher_academies(id) ON DELETE SET NULL,
    display_name    TEXT    NOT NULL DEFAULT '',
    subject         TEXT    NOT NULL,
    photo_url       TEXT    NOT NULL DEFAULT '',
    intro_video_url TEXT    NOT NULL DEFAULT ''
);
CREATE INDEX idx_teacher_bundle_members_bundle ON teacher_bundle_members(bundle_id, position);

-- The first package, with the portraits the owner supplied. Names and teacher spaces are added from the admin
-- screen (/app/bundles); until then each member shows its subject only.
INSERT INTO teacher_bundles (slug, name, tagline, description, published, sort_order)
VALUES ('excellence', 'باقة التفوّق', '٥ مدرسين · ٥ مواد · صفحة واحدة',
        'مجموعة مدرسين اشتركوا مع بعض في باقة واحدة. لكل مدرس مساحته الخاصة وكورساته وفيديوهاته، وكلهم هنا في صفحة واحدة تختار منها مدرسك.',
        TRUE, 0);

INSERT INTO teacher_bundle_members (bundle_id, position, subject, photo_url)
SELECT b.id, m.position, m.subject, m.photo_url
FROM teacher_bundles b,
     (VALUES (0, 'اللغة العربية', '/images/bundles/arabic.jpg'),
             (1, 'اللغة الإنجليزية', '/images/bundles/english.jpg'),
             (2, 'الفيزياء', '/images/bundles/physics.jpg'),
             (3, 'العلوم', '/images/bundles/science.jpg'),
             (4, 'الكيمياء', '/images/bundles/chemistry.jpg')) AS m(position, subject, photo_url)
WHERE b.slug = 'excellence';
