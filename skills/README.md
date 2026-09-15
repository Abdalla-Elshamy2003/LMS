# Manarah Design and Implementation Skills

English guidance for the design, important functions, clean code, and application architecture of an Arabic RTL Manarah LMS. These files specify implementation requirements; they do not themselves implement the application changes. The inventory was checked against `frontend/src/App.jsx` and all 18 files in `frontend/src/pages` on 2026-09-07, including certificate pages added during preparation.

## How to use

Read [the shared design system](manarah-design-system/SKILL.md), [the clean architecture contract](manarah-clean-architecture/SKILL.md), and the skill for the page being changed. Each page includes named functional responsibilities, suggested ownership, important business invariants, and behavioral verification alongside the visual guidance. Read only the relevant page skills. Existing routes are current source facts; new public routes below are proposed additions and need implementation before linking to them in a shipped UI.

These project-local skills are intentionally stored in the requested `skills/` folder. They can be used by referencing their file paths; this folder alone does not guarantee automatic discovery by an agent runtime. Example instruction:

> Use `skills/manarah-design-system/SKILL.md`, `skills/manarah-clean-architecture/SKILL.md`, and `skills/manarah-students/SKILL.md` to implement the student directory. Complete its functional contracts, preserve supported API behavior, and verify the affected flows.

## Functional and architecture guidance

Function names in these skills are suggested contracts, not an inventory of existing code symbols. Reuse an equivalent implementation; create a new boundary only when it has a real responsibility. Treat existing functionality as behavior to preserve/refine, proposed public pages as additions, and new API capabilities as work requiring their own complete server path. An earlier conditional design note does not imply the capability is implemented.

For an implementation task, select the page's relevant use cases and carry each through input validation, role/resource authorization, application service, persistence or external adapter, error recovery, and visible outcome. Keep the current Spring modular monolith and React stack while separating responsibilities incrementally. Do not generate empty layers, generic CRUD frameworks, or unrelated infrastructure to appear architectural.

The architecture skill centralizes dependency direction, frontend feature organization, DTO boundaries, database transactions, tenant isolation, idempotency, score-event ordering, money/time precision, error handling, and appropriate tests. Page skills explain how those principles apply to their actual workflows, including uploads, attendance QR, exam attempts, grade submissions, payment recording, certificate issuance, and public registration.

## Existing page coverage

| Page source | Current route | Skill | Included secondary views |
| --- | --- | --- | --- |
| `Landing.jsx` | `/` | [Public home](manarah-landing/SKILL.md) | Hero, public previews, multi-page extraction |
| `Login.jsx` | `/login` | [Login](manarah-login/SKILL.md) | Auth errors, pending, demo distinction |
| `Dashboard.jsx` | `/app` | [Dashboard](manarah-dashboard/SKILL.md) | Admin, teacher/assistant, student, parent, fallback |
| `Students.jsx` | `/app/students` | [Students](manarah-students/SKILL.md) | Cards/table, search, new student |
| `StudentProfile.jsx` | `/app/students/:id` | [Student profile](manarah-student-profile/SKILL.md) | Timeline, grades, risk, guardians, enrollments |
| `Courses.jsx` | `/app/courses` | [Courses](manarah-courses/SKILL.md) | Discovery, new course |
| `CourseDetail.jsx` | `/app/courses/:id` | [Course detail](manarah-course-detail/SKILL.md) | Modules, lessons, materials, uploads, roster |
| `Attendance.jsx` | `/app/attendance` | [Attendance](manarah-attendance/SKILL.md) | Sessions, roster editor, QR |
| `Exams.jsx` | `/app/exams` | [Exams](manarah-exams/SKILL.md) | Question bank, creation, picker, preview, attempt, results |
| `Homework.jsx` | `/app/homework` | [Homework](manarah-homework/SKILL.md) | Student text/file submission, creation, review, grading, missing work |
| `Payments.jsx` | `/app/payments` | [Payments](manarah-payments/SKILL.md) | Invoice filters, totals, reminders |
| `Staff.jsx` | `/app/staff` | [Staff](manarah-staff/SKILL.md) | Teacher directory and creation |
| `Certificates.jsx` | `/app/certificates` | [Certificates](manarah-certificates/SKILL.md) | Staff issuance, student/parent libraries, printable preview, PDF |
| `VerifyCertificate.jsx` | `/verify/:code` | [Certificate verification](manarah-verify-certificate/SKILL.md) | Public lookup, valid/invalid/error states |
| `Leaderboard.jsx` | `/app/leaderboard` | [Leaderboard](manarah-leaderboard/SKILL.md) | Podium, ranking, scoped filters |
| `Notifications.jsx` | `/app/notifications` | [Notifications](manarah-notifications/SKILL.md) | Inbox, read state, channel filters |
| `Rules.jsx` | `/app/rules` | [Rules](manarah-rules/SKILL.md) | Guided builder, preview, enabled state |
| `Audit.jsx` | `/app/audit` | [Audit](manarah-audit/SKILL.md) | Event table, detail, pagination |

## Proposed public pages

The multi-page public website contains the existing home and login plus these five distinct pages. Public certificate verification remains a separate existing utility route. Each new page requires a real route, independent refresh behavior, shared navigation/footer, and page-specific content. Anchor sections alone do not meet this requirement.

| Proposed route | Skill | Purpose |
| --- | --- | --- |
| `/courses` | [Public courses](manarah-public-courses/SKILL.md) | Search/filter public courses and select registration context |
| `/teachers` | [Public teachers](manarah-public-teachers/SKILL.md) | Discover teachers and available course links |
| `/features` | [Features](manarah-public-features/SKILL.md) | Interactive, fact-based product narrative |
| `/about` | [About](manarah-public-about/SKILL.md) | Editorial institution/platform story |
| `/register` | [Registration](manarah-public-register/SKILL.md) | Professional trial enrollment request |

## Recommended implementation sequence

1. Map the selected feature's existing contracts and ownership; establish shared tokens, buttons, cards, fields, dialogs, table/filter primitives, motion presets, and reduced-motion behavior without a project-wide rewrite.
2. Refine the authenticated shell and public shell, including visible login on desktop and mobile.
3. Implement home and the five public routes, then verify public course-to-registration navigation.
4. Refine login, role dashboards, students, profiles, courses, and curriculum authoring.
5. Refine attendance, exams, homework, payments, and teacher management with their actual mutation/recovery flows.
6. Refine certificates and public verification, notifications, rules, leaderboard, and audit; complete relevant role, responsive, and accessibility verification.

## Completion standard

Professional means coherent hierarchy, working use cases, readable data, recoverable forms, purposeful motion, and clear ownership of logic. A complete implementation also preserves tenant/resource authorization, transaction invariants, and reliable retry semantics where needed. Page-level behavioral checks and visual acceptance scenarios complement the shared requirements. Proposed backend-dependent behavior must be implemented or explicitly kept out of the shipped interaction; do not replace it with decorative controls. Validate documentation changes separately from runtime behavior and report actual tests rather than treating a build as proof of business correctness.

The collection contains **25 skills**: two shared foundations (design and clean architecture), one for each of the **18 existing pages**, and **five proposed public pages**. All 23 page skills include functional contracts and page-specific architecture boundaries. Backend-only modules without frontend routes are outside this page inventory.
