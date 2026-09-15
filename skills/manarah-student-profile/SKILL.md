---
name: manarah-student-profile
description: Refine /app/students/:id into a clear student overview with grades, timeline, guardian details, risk context, and restrained progress animation.
---

# Student Profile

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadStudentOverview(studentId, signal) | Feature query orchestration | Load profile, grades, and timeline with independent panel outcomes and consistent identity. |
| mapGradeRows(items, filters) | Pure model | Filter the available grade set and calculate display percentages only for valid scores and positive maxima. |
| loadTimelinePage(studentId, query, signal) | Timeline query adapter | Preserve server pagination and supported filters; expose whether all events are loaded. |
| exportStudentReport(studentId, snapshot) | Report adapter | Capture the intended profile, grades, and loaded-history scope using the existing print component. |

**Architecture boundary.** Student access belongs in a server policy checking tenant and viewer relationship before related queries. Keep PDF rendering in the existing report/export boundary, not in grade calculation. Use the same loaded snapshot for the page and its explicitly scoped report.

**Behavioral verification.** Verify parent-child access, cross-tenant IDs, partial panel failure, rapid profile switches, zero/missing grade denominators, and exported identity/history scope.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/StudentProfile.jsx`. Current data comes from `/api/students/{id}`, `/api/students/{id}/timeline?size=40`, and `/api/gradebook/student/{id}`.

## Composition

Use a compact identity masthead with avatar, full name, code, grade/school, and labeled statuses. Follow with a progress strip and a main activity/grades area beside a guardian and enrollment rail. Consider Overview, Grades, and Activity tabs when content becomes long; maintain addressable selection and keyboard tab behavior.

Academic risk belongs in a clearly titled contextual panel with returned reasons. Do not infer new diagnoses from visual thresholds. Restrict guardian and student details to authorized viewers. Keep the back link to the student directory and preserve directory context where available.

## Tables and filters

Build the gradebook as a readable table with title, category, score/max score, and percentage. Preserve decimal precision needed by the data and show missing results distinctly. Category and title filtering can be local if all grade items are returned. Course filtering needs a real course field.

Timeline controls may include event category and date, but the current request only fetches 40 records. Complete server pagination/filter support for full history, or label filtering as applying to loaded events. Group visible events by date, preserve exact timestamps on demand, and avoid labeling a truncated feed as complete.

## Motion, forms, and states

Animate progress rings once with an accessible final numeric label. Verify that each source value is actually a percentage before using a percentage ring; `avgScore` must not acquire an invented unit. Use quiet timeline reveals and keep warnings static. There is no edit form on the current page: use shared form rules if a real edit workflow is introduced, with supported fields and permissions.

Load profile, timeline, and gradebook with independently recoverable panel states where possible. Distinguish a missing student from a forbidden profile and a temporary failure.

## Acceptance scenarios

- Missing guardians, enrollments, risk data, and grade items each have a purposeful state.
- Grade percentages handle zero maximum scores and missing scores without false results.
- A failed timeline request does not erase a successfully loaded student profile.
- Parent and staff viewing contexts preserve the relevant access boundaries.
