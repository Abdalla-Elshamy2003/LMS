---
name: manarah-homework
description: Refine /app/homework with a clear assignment worklist, professional creation forms, submission review, grading, and deliberate missing-work actions.
---

# Homework

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadMyAssignments(signal) / loadStaffAssignments(query, signal) | Role-specific query adapters | Keep personal submission fields separate from administrative listing DTOs. |
| createAssignment(command, actor) | Homework application service | Authorize course, validate deadline/maximum score, and persist the assignment. |
| submitHomework(assignmentId, draft, actor) | Submission workflow and service | Resolve the student from actor context, preserve a successful upload key, and enforce the actual resubmission/deadline policy. |
| gradeSubmission(submissionId, score, actor) | Grading application service | Validate ownership/scope and score bounds, persist, and emit the established scoring event. |
| markMissingAssignments(assignmentId, actor) | Homework application service | Evaluate eligible students with the actual deadline and mutation scope; make repeated runs consistent. |

**Architecture boundary.** Use separate submission and grading hooks so one student's upload state cannot affect a teacher's review dialog. Store drafts outside transient row rendering. Keep authoritative grading and missing-work eligibility in HomeworkService, with file effects behind the upload adapter.

**Behavioral verification.** Verify text-only/file-only submission, upload-success/save-failure recovery, disallowed resubmission, grading zero and above maximum, cross-course grading, and repeated missing-work runs.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Homework.jsx`. The current UI includes staff assignment management and a student `MyHomework` view with `SubmitModal`. Student requests use `/api/homework/my`, `/api/files/upload`, and `/api/homework/submit`; staff can create assignments, review submissions, grade them, and mark missing work.

## Worklist and filtering

Use a deadline-oriented worklist with title, course, deadline, maximum score, and available review action. Provide title search and the existing course filter. Due/upcoming filters can use returned deadlines, with the timezone and date-only semantics made clear. Submission-status filters need actual per-student or aggregate status data; an assignment deadline alone does not prove non-submission.

For teachers, emphasize work awaiting review when supported. For students, emphasize instructions, due dates, and their actual submission state. Preserve text answers, file attachments, and the existing submission action. For parents or read-only roles, show only authorized context.

## Forms and grading table

Group assignment creation into course/title, instructions, and deadline/maximum score. Validate required course/title and finite positive maximum score. Match date serialization to the backend and show the due-date interpretation near the input. Preserve form content after failure.

Replace prompt-based grading, if present, with an inline form or review drawer. Show student identity, submission status/time when returned, accessible attachment link, score/max score, and feedback only if supported. Filter by student and available status. Validate scores from zero through the assignment maximum. Save each row with its own pending/error state and retain other unsaved edits.

The student submission form needs clear answer-versus-attachment guidance, existing submission context, file selection metadata, and a stable pending action. Validate that at least one supported answer source is present. Preserve text and the uploaded file key if final submission fails, so retry need not reupload the file. Explain replacement/resubmission behavior from the actual backend contract and do not show success before receipt is confirmed.

Mark missing work is consequential: show the affected assignment and supported scope before applying `/api/homework/assignments/{id}/mark-missing`. Do not imply this affects only the currently filtered rows unless the backend accepts that scope.

## Motion and acceptance

Use quiet worklist entrances, subtle selected-row highlighting, and a brief saved-state transition. Deadline warnings stay static. Submission charts are unnecessary without complete counts.

- Creation errors retain instructions; date display does not shift to the wrong local day.
- Zero is a valid grade; out-of-range grades cannot be submitted.
- Mark-missing scope is explicit and does not masquerade as a filtered bulk action.
- Student submission handles selected file, upload failure, final-save failure, and confirmed receipt visibly.
