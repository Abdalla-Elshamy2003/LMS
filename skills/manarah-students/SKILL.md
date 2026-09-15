---
name: manarah-students
description: Design the student directory at /app/students with useful academic filters, professional table and card views, and a structured student creation form.
---

# Student Directory

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| normalizeStudentQuery(draft) | Pure query model | Normalize supported search/status/branch/page/size inputs, bound page size, and reset pagination on filter changes. |
| searchStudents(query, signal) | Feature API adapter and student query service | Return rows and totals from the same scoped predicate; ignore stale responses in the consuming hook. |
| validateStudentDraft(draft) | Pure form model | Return field-level errors without assuming optional DTO fields are required. |
| createStudent(command, actor) | Student application service | Authorize referenced branch, validate business fields, generate a unique code, persist and audit in one database transaction. |
| useStudentDirectory() | Feature hook | Own query, view mode, creation state, and targeted refresh without duplicating filtered data. |

**Architecture boundary.** The service already accepts branchId and status in addition to academicStatus; check controller exposure before wiring those filters. Keep input validation and card/table mapping outside the page. StudentService remains the write boundary; repository queries enforce tenant predicates.

**Behavioral verification.** Verify combined filtering beyond the first page, older responses after a new search, invalid branch references, and concurrent code generation. Confirm failed creation cannot leave a partial student/audit workflow.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Students.jsx`; route: `/app/students`. Current `GET /api/students` uses `q`, `academicStatus`, `page`, and `size`, returning `content`, `totalElements`, and `totalPages`. Creation uses `POST /api/students`.

## Layout and data presentation

Lead with the page title, a concise record count, and a permission-aware New student action. Place name/code/phone search above academic-status chips. Offer a professional table for administrative comparison and retain cards as an alternate view. Table columns: student identity with code, grade, academic status, enrollment status, overall percentage, attendance, homework, and a clear profile link. Keep score columns aligned and status badges text-labeled.

Cards should prioritize name, grade, and the most relevant academic indicators; reserve a subtle highlight for students needing attention. Do not make warnings punitive or rely on red alone. Use compact horizontal progress indicators where they improve comparison.

## Filtering and form

Combine search and academic status using the existing server contract. Debounce search, reset to page zero atomically, and prevent stale responses from restoring the wrong page. New grade, branch, or enrollment-status filters require confirmed server support. Keep sensitive search values out of URLs. Query-backed academic status should support dashboard drill-downs.

Organize the creation dialog into Identity (`fullName`), Academic placement (`gradeLevel`, `grade`), and Contact (`phone`, `school`). Use useful controlled options only when their source exists. Full name is required; validate optional phone formatting without assuming every contact has an Egyptian mobile number. Distinguish required business fields from optional inputs using the actual DTO. Preserve entries and surface server errors; refresh the directory only after confirmed save.

## Motion and acceptance

Use restrained card lift, a short filter indicator transition, and at most a small first-screen card stagger. Table changes should be immediate with subtle row highlighting, without a long cascade.

- Searching from a later page cannot briefly restore that later page's results.
- Combined filters and pagination show correct totals for the whole query.
- A failed creation leaves entered data available for correction and retry.
- A long Arabic name, missing metric, empty directory, and no-match query all remain readable.
