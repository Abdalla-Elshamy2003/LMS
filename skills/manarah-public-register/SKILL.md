---
name: manarah-public-register
description: Create the proposed /register page with an exceptionally clear Arabic enrollment request form, course context, validation, and truthful registration confirmation.
---

# Public Registration

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| resolveRegistrationContext(tenantSlug, courseId, catalog) | Pure model and server context resolver | Confirm institution and optional course membership; reject invalid selections before writes. |
| validateRegistrationDraft(draft) | Pure form model plus server validation | Return field errors for meaningful required values and accurately map guardian contact/email semantics. |
| registerTrialStudent(command) | Public registration application service | Atomically create trial student, guardian relation, and optional enrollment with concurrency-safe student code allocation. |
| submitRegistration(draft, context) | Feature mutation controller | Send the established DTO, preserve input after rejection, and distinguish unknown completion from confirmed failure. |
| mapRegistrationReceipt(response) | Pure view model | Show returned student code and actual next step without creating an authentication claim. |

**Architecture boundary.** Move the current multi-repository PublicController workflow into a service with validation and a transaction. Reject an invalid requested course rather than silently returning a different enrollment outcome. Add durable duplicate-request handling when implementing safe retry; current frontend locking alone is insufficient. Keep receipt and login account creation as distinct use cases.

**Behavioral verification.** Verify rollback if guardian/enrollment creation fails, simultaneous code generation, foreign-tenant course input, ambiguous response after commit, duplicate request policy, and a receipt that does not imply credentials were created.


Read [the shared design system](../manarah-design-system/SKILL.md). Status: proposed route `/register`, extracted from `RegisterSection` in `frontend/src/pages/Landing.jsx`. Inspect `backend/src/main/java/com/manarah/web/PublicController.java` before changing fields or confirmation copy.

The existing `POST /api/public/register` creates a trial student, guardian relationship, and optional course enrollment. It does not create login credentials. Current DTO fields are fullName, phone, email, grade, guardianName, guardianPhone, courseId, and tenantSlug. The existing form uses a subset; add optional DTO fields only when useful and label their recipient/purpose accurately. In particular, the controller stores email on the guardian.

## Layout and form flow

Use a reassuring split page: a compact course/request summary and a spacious form, with the form first on mobile. Provide three clear sections rather than a needlessly long wizard: Student details, Guardian contact, and Course preference. Use a separate review step only if the form grows enough to benefit from it.

Keep full name and contact fields visibly labeled. Show grade choices from a maintained source or a clearly understood input. Phone fields need suitable input mode, readable LTR values, and clear local/international format hints. Explain when guardian contact is optional and when the backend falls back to the student's phone. Do not add a password field or imply immediate account activation.

Use a searchable public course selector with an optional no-course preference. Accept `courseId` from `/courses`, validate it against the current public list, and show title, teacher, and price as context. The page must not imply that submission takes payment. Preserve tenant context between landing data and registration payload.

## Submission and confirmation

Validate meaningful required fields and supported formats before submission, then rely on actual server validation. Provide inline errors, preserve values, focus the first invalid field, and prevent repeated clicks. Do not automatically retry after an ambiguous network failure: this endpoint creates records and may already have succeeded.

On confirmed success, show the returned student code/message where provided, explain the next contact step, and offer course discovery or login for existing account holders. Do not invent a password, delivery confirmation, or guaranteed response time. Clear sensitive form values after a completed flow without putting them into query parameters.

## Motion and acceptance

Use quiet field focus transitions, a stable-width submit button, and one brief confirmation icon animation. No distracting 3D artwork beside active inputs. The form is the primary product on this page.

- Course-prefilled, no-course, removed-course, and tenant-specific flows are understandable.
- Invalid input, duplicate clicks, server rejection, and uncertain network outcomes preserve a safe recovery path.
- Success communicates trial registration accurately without suggesting a login account was created.
- Mobile keyboard, autocomplete, keyboard submission, and reduced motion remain usable.
