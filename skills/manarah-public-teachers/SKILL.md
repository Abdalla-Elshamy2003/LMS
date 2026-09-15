---
name: manarah-public-teachers
description: Create the proposed public teacher directory at /teachers with distinctive profiles, subject discovery, and honest links to available courses.
---

# Public Teacher Directory

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectPublicTeachers(teachers, query) | Pure selector | Search returned names/subjects and sort deterministically without inventing normalized subject data. |
| mapTeacherPreview(publicDto) | Pure view mapper | Present approved biography, title, course count, and photo fallback. |
| resolveTeacherCourseLink(teacher, catalog) | Navigation policy | Use only supported matching fields; handle duplicate display names explicitly rather than asserting ID joins. |
| useTeacherBiography(teacherId) | Local UI controller when needed | Manage expansion/focus without making an unnecessary private profile request. |

**Architecture boundary.** Use the shared public catalog query lifecycle; do not create a second account API in the public directory. Missing information should remain absent. Teacher-course joins belong in the public query DTO if an exact stable association is later added.

**Behavioral verification.** Verify duplicate names, free-text subjects, missing portraits, no courses, and absence of private account fields in public cards or requests.


Read [the shared design system](../manarah-design-system/SKILL.md). Status: proposed new route `/teachers`. Start with the teacher section in `frontend/src/pages/Landing.jsx` and the teachers array from `GET /api/public/landing`. The public fields are id, name, title, subjects, bio, photoUrl, and courses count.

## Art direction and structure

Use a portrait-led editorial opening with a clear heading, a short explanation of course discovery, and a grid with generous spacing. Each profile presents portrait or initials fallback, full name, title, subject text, concise biography, and course count. A restrained geometric light motif can unite the portrait treatments. Do not fabricate credentials, years of experience, testimonials, availability, or ratings.

Let long biographies expand inline or in an accessible detail drawer. Keep the primary profile information visible before interaction. Public profile content must stay within the public payload; never fetch private staff email or phone data to enrich the page.

## Search and cross-navigation

Provide name/subject search and subject filtering only when the free-text subjects can be interpreted consistently. Avoid constructing a misleading exact taxonomy by splitting arbitrary punctuation. Show match count and Clear all. Sorting by name and returned course count is appropriate over the complete list.

Link to `/courses` with an implemented teacher filter. Because the public course response supplies teacherName rather than teacherId, duplicate names require an honest fallback to broader course discovery or a verified API enhancement. Do not promise exact ID-based matching without the necessary field. Keep registration and login available in shared navigation.

## Motion and acceptance

Use a modest profile-card lift and a short biography reveal with focus retained. Avoid portrait tilt, spinning badges, or decorative count-up on every course count. No tables, charts, or account forms are needed in this public discovery view.

- Portrait load failure, long names, missing bios, and zero courses have deliberate layouts.
- Search and filtering remain usable on mobile and keyboard.
- Course links do not silently associate duplicate teacher names with the wrong person.
- Direct route refresh, active navigation, and login entry work after implementation.
