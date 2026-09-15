---
name: manarah-exams
description: Design /app/exams across exam discovery, question-bank filtering, creation and publishing, student attempts, previews, and results tables.
---

# Exams and Question Bank

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| searchQuestions(query, signal) | Question-bank query adapter | Return paginated results with supported subject/type/difficulty filters and stable selection IDs. |
| createExamDraft(command, actor) / publishExam(examId, actor) | Exam authoring use cases | Validate content mode and publication prerequisites separately; preserve a created draft after publish failure. |
| startOrResumeAttempt(examId, actor) | Attempt application service | Authorize enrollment/availability, enforce attempt policy, and return authoritative timing without exposing answer keys; implement resume support explicitly if absent. |
| validateAnswer(question, draft) | Pure form model | Validate the supported answer shape without deciding the awarded grade. |
| submitAttempt(attemptId, answers, actor) | Attempt application service | Verify attempt ownership, expiry, question membership, and single finalization; grade on the server and publish the score event through one path. |
| loadExamResults(examId, query) | Results query service | Return authorized result rows and real aggregate scope, keeping pending manual grades distinct. |

**Architecture boundary.** Separate authoring, bank, attempt state, and results modules instead of a universal exam hook. Inject clock/randomness for deterministic policy verification. Keep answer keys out of student DTOs. Preserve ScoreListener ordering and distinguish final attempt commit from later metrics processing.

**Behavioral verification.** Verify insufficient generation pools, same draft after publish retry, attempts on another student's ID, timer expiry, duplicate/concurrent submission, invalid option IDs, and score-event replay without duplicated gradebook entries.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Exams.jsx`. This single route contains exam listing, `QuestionBank`, `CreateExamModal`, `QuestionPicker`, `ResultsModal`, `ViewExamModal`, and `TakeExamModal`; cover each mode when changing the page.

## Staff workspace

Use clear Exams and Question bank tabs with a strong content heading and course filter. Exam cards need title, course context, duration, status, and permitted actions. A results table should contain student, attempt status, score, percentage, and clearly explained flags. Keep pending/manual grading distinct from a zero score.

Question-bank search currently sends `q`, `difficulty`, and `size: 50` to `/api/exams/questions`; the picker requests 30. Add real pagination before describing search or selection as exhaustive. Preserve selected question IDs across filters and show selected count. Type/subject filtering needs backend support if results are paginated.

## Creation workflow

Organize Details, Content, and Review into an explicit stepper. Details include title, course, duration, and pass percentage. Content offers automatic generation, manual question selection, or file-backed exam using the current supported modes. Automatic mode needs subject and nonnegative easy/medium/hard counts with a positive total. Manual mode needs a reviewable question list. File mode needs visible upload state and supported file constraints.

Show duration, question count where known, content source, and enabled exam settings in Review. Use an explicit publish action. Existing automatic/file flows create then publish: preserve the created exam ID if publishing fails so recovery does not duplicate the exam. Never advertise anti-cheating guarantees beyond implemented behavior.

## Student attempt

Use a focused reading canvas with stable timer, answer controls, question navigation, and a clear submission summary. Support the actual question types declared in the source. Keep answer drafts through ordinary UI updates. Use the authoritative timing/attempt contract; verify resume behavior instead of restarting time on refresh. Do not imply autosave or offline submission unless supported.

Validate submission and explain unanswered questions before the existing submit operation. Avoid a dismissible small dialog that makes accidental loss easy. Submission failure must retain answers; handle ambiguous completion without blindly creating a new attempt.

## Motion and acceptance

Keep motion in tab indicators, selected answers, and completed actions; no question shuffle animation during an attempt or bouncing timer. Charts are optional and require actual aggregate result data.

- Validate staff versus student access to bank, previews, results, and attempts.
- Test each creation mode, insufficient bank questions, upload errors, and publish failure after creation.
- Test keyboard answers, timer expiry, submit failure, and long Arabic questions.
- Results filters never present a partial fetched subset as the complete exam cohort.
