---
name: manarah-course-detail
description: Design /app/courses/:id as a structured curriculum workspace with modules, lessons, materials, roster search, and professional content authoring forms.
---

# Course Detail

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectCurriculum(course, query, materialType) | Pure selector | Retain matching ancestors and stable module/lesson IDs without mutating server content. |
| addModule(courseId, draft) / addLesson(moduleId, draft) | Course application service | Authorize the containing course and validate the requested content before persistence. |
| uploadMaterialFile(file, onProgress) | Storage adapter | Return a file key and transport state; do not report the material as saved yet. |
| saveMaterialBatch(lessonId, rows) | Feature workflow plus material use case | Track each row's upload and metadata-save outcome; retry only unfinished stages with stable row IDs. |
| loadCourseRoster(courseId, signal) | Enrollment query adapter | Return authorized roster DTOs, with explicit loading/error state separate from curriculum. |

**Architecture boundary.** Extract authoring dialogs and per-row upload state from the route. File upload is not atomic with database metadata; keep a file key after upload succeeds and define unused-file reconciliation. Verify all module/lesson/material parent relations on the server, not only the final ID.

**Behavioral verification.** Verify cross-course references, one-file failure in a batch, upload success followed by metadata failure, and stale course/roster responses. A retry must not duplicate successful material rows.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/CourseDetail.jsx`. Read `/api/courses/{id}`, `/api/enrollments/course/{id}`, and the module/lesson/material mutations already called by this page. Uploads use `/api/files/upload` and the existing authenticated file helper.

## Layout and discovery

Use a concise course masthead, a dominant curriculum outline, and an enrollment rail. Render modules as accessible accordions with lesson counts, clearly nested lessons, duration labels, and material-type icons. Keep staff add actions near the level they affect. Students should see learning content without authoring controls.

Add curriculum search that retains matching parent modules and lessons, plus material-type filtering using the existing VIDEO, PDF, PPT, DOC, IMAGE, AUDIO, and LINK types. Keep selected module context through filtering. Add name/code search to the roster. For large rosters, use a compact table with student identity and profile action; no invented completion statistics.

## Authoring forms

Keep Add module focused on title. Add lesson groups title, duration in minutes, and description. Validate supported duration bounds. Add materials needs stable row IDs, type, title, description, and an explicit file-versus-link input mode. Explain accepted formats using real server limits. Display each selected file's name, type, and size.

The current multi-item save is sequential and can partially succeed. Track each saved item and failed item separately so retry does not create duplicate materials or reupload successful files. Preserve unfinished rows, show progress, and never silently skip an incomplete row. Do not imply reorder, delete, autosave, or upload cancellation until their behavior is implemented.

## Motion and acceptance

Use short accordion height transitions with focus continuity, restrained material-row hover, and per-file status transitions. Disable height animation under reduced motion. Do not autoplay lesson media.

- Empty modules, lessons without materials, and unavailable files have distinct recovery paths.
- Upload success followed by material-save failure remains recoverable without repeating completed work.
- Search retains understandable curriculum hierarchy and clearing it restores the outline.
- Role-specific controls, keyboard accordions, and mobile upload forms work correctly.
