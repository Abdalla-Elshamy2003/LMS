---
name: manarah-courses
description: Design the authenticated course directory at /app/courses with purposeful course cards, discovery filters, and a polished course creation dialog.
---

# Course Directory

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectCourses(courses, filters, sort) | Pure selector | Combine title, subject, and teacher filters, apply stable sorting, and preserve the complete-list scope. |
| loadCourseOptions(signal) | Feature adapter | Load teacher options with an independently recoverable error state. |
| validateCourseDraft(draft) | Pure form model | Validate title, supported assignment fields, and finite nonnegative price. |
| createCourse(command, actor) | Course application service | Authorize management, verify teacher/tenant references, persist the course, and return the established DTO. |

**Architecture boundary.** Use a course feature hook for catalog lifecycle and mutation refresh. Keep formatted EGP strings out of request DTOs; CourseService owns persistence and assignment invariants. Public discovery must use its separate public adapter.

**Behavioral verification.** Verify combined filters, missing teacher options, unauthorized course creation, foreign-tenant teacher IDs, and exact price serialization.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Courses.jsx`; route: `/app/courses`. Current data uses `GET /api/courses` and `/api/users/by-role/TEACHER`; creation uses `POST /api/courses`. This skill covers the authenticated directory; use the public-courses skill for anonymous discovery.

## Composition and interaction

Use a catalog workspace with a compact search/filter band and a generous course grid. Replace arbitrary per-index gradients with consistent subject artwork or a stable subject accent. Each card should expose title, subject, teacher, student count, and price without hover. Keep the entire primary card link keyboard-accessible and avoid nested interactive elements.

Preserve subject and teacher filters; add title search and title/price sorting over the complete returned list. Show active filters, visible result count, and Clear all. Add a management table toggle if comparing many courses is part of the requested workflow. Use columns for title, subject, teacher, enrollment count, and price. New filters require actual response fields.

Do not label all returned courses as My courses without verified role scoping. Keep management actions aligned with the existing manager/teacher/content-manager checks and backend authorization. Teacher-option failure should be recoverable without hiding the catalog.

## Creation form

Group title, subject, and grade level as course identity; teacher as assignment; price as pricing. Display EGP explicitly. Validate finite nonnegative price and required title, serialize numeric values deliberately, and preserve the backend meaning of an unassigned teacher. Show field-level errors and a useful saving state. Refresh after confirmed creation without unexpectedly clearing directory filters.

## Motion and acceptance

Use a subtle cover reveal and a 2-3px lift on interactive cards; do not animate prices on hover. Filter transitions should preserve card dimensions. No chart is needed for a simple catalog.

- Subject, teacher, and search combine correctly and Clear all restores the catalog.
- Empty catalog, no matches, missing teacher, and failed teacher lookup remain usable.
- Student viewers cannot invoke creation; staff can recover from validation and network errors.
- Long course titles wrap without pushing price and identity out of alignment.
