---
name: manarah-staff
description: Design /app/staff with a professional teacher directory, useful search and subject filters, and a structured teacher account creation form.
---

# Teachers and Staff

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectTeacherDirectory(teachers, query) | Pure selector | Search supported name/subject fields and retain the actual teacher-only dataset scope. |
| validateTeacherDraft(draft) | Pure form model | Validate identity/contact/account fields without mutating passwords or inventing a subject taxonomy. |
| createTeacherAccount(command, actor) | Identity application service | Authorize assignable role, validate uniqueness and tenant scope, hash credentials, and persist the allowed profile fields. |
| mapPublicTeacherProfile(user) | Public DTO mapper | Expose only approved public profile fields; exclude account credentials and private contact information. |

**Architecture boundary.** Split account creation from presentation/profile cards. Keep password handling in Auth/Identity services and photo preview in a local component. The account form cannot choose arbitrary roles merely by altering a request payload.

**Behavioral verification.** Verify duplicate email behavior, forbidden role injection, invalid optional photo data, failed save with preserved nonsecret fields, and public serialization excluding private account fields.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Staff.jsx`; route: `/app/staff`. The page currently retrieves teachers through `GET /api/users/teachers` and creates a teacher through `POST /api/users`. Its navigation label mentions the team, but the current dataset is teachers, not a complete staff directory.

## Directory composition

Use editorial profile cards with consistent portrait proportions, name, title, subjects, and a concise schedule/bio preview. Provide a compact table view for administrative comparison, with teacher, subjects, contact, and available schedule information. Photos need a graceful initials fallback. Use stable subject accents rather than randomly changing colors after search.

Preserve name/subject search and introduce useful subject filtering only from returned data. Treat the current free-text subjects and schedule as text, not a normalized taxonomy or availability calendar. A role filter requires a broader user endpoint and authorization; do not display empty role choices over teacher-only data.

## New teacher form

Group the actual fields into Identity (`fullName`, `title`, `photoUrl`), Account (`email`, `password`), Contact (`phone`), and Teaching profile (`subjects`, `schedule`, `bio`). Keep bio full width, use a preview for a valid photo URL, and explain optional fields. Use email validation and password visibility with appropriate autocomplete. Match password requirements to the backend; do not invent invitation delivery or expose a default password as the recommended production workflow.

Show duplicate-email errors next to email and preserve other values on failure. Keep role assignment consistent with this teacher-creation workflow. If role editing is later requested, verify assignable roles on the server before adding a selector.

## Motion and acceptance

Use mild card hover and form section entrance; portraits must not zoom enough to crop names or distract. No charts are needed for this directory.

- Long biographies, missing images, and empty schedules do not break card alignment.
- Search and subject filtering compose correctly over the actual teacher list.
- Failed creation retains entered data and duplicate-email correction is straightforward.
- The page never implies a complete staff roster when only teachers are loaded.
