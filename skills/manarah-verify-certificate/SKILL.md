---
name: manarah-verify-certificate
description: Refine the public /verify/:code page with an authoritative-looking but truthful certificate result, readable evidence fields, retry states, and clear navigation.
---

# Public Certificate Verification

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| normalizeVerificationCode(routeValue) | Pure input model | Validate the backend-supported code shape and encode path data safely; do not invent an incompatible format. |
| verifyCertificate(code, signal) | Public adapter and verification query service | Return a narrow verification DTO through the public endpoint without depending on an authenticated actor. |
| mapVerificationOutcome(response, error) | Pure state mapper | Distinguish valid, explicitly invalid, temporary failure, and malformed input. |
| useCertificateVerification(code) | Feature hook | Reset previous results, cancel/ignore stale lookups, and provide a scoped retry. |

**Architecture boundary.** The server verifies the opaque code and controls public field exposure. The client must not infer validity from code length, decode private data, or enrich results through student endpoints. Keep verification state independent from login state.

**Behavioral verification.** Verify anonymous access, valid/invalid responses, network failure, rapid code changes, absent optional fields, and no stale certificate details after a new lookup.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/VerifyCertificate.jsx`; existing public route: `/verify/:code`. The page calls `GET /api/public/verify-certificate/{code}` and displays valid, studentName, courseTitle, grade, issuedAt, and serial fields when returned.

## Composition and result semantics

Use a calm, centered verification surface with the Manarah identity, a prominent text result, and a structured definition list. Put student name first, followed by optional course and grade, issue date, and serial. Use clear Arabic labels and isolated LTR serial text. Allow long names to wrap; do not squeeze them against a fixed-width label column.

Use a restrained success icon and border for an explicitly valid response. A verified record confirms what this endpoint checks; do not add invented government accreditation, tamper-proof guarantees, or revocation status. Show only fields returned for public verification and avoid enriching the result from private endpoints.

## States and navigation

Separate loading, valid, explicitly invalid/not found, temporary network/server failure, and malformed code states. The current implementation maps every request failure to invalid; improve that distinction so service unavailability does not discredit a real certificate. On code changes, clear the old result and ignore stale responses.

Keep Home and Login links available. A Retry action should repeat the current lookup without changing the code. An optional manual code-entry form is appropriate only if requested; validate input and navigate to an encoded local verification route. The core QR journey needs no filters, table toolbar, or chart.

## Motion and acceptance

Use a short surface entrance and one subtle confirmed-result icon reveal. Never animate success before the server returns validity. Render final states directly for reduced motion, and announce result changes without repeatedly reading every field.

- A real certificate QR reaches this route anonymously and survives direct refresh.
- Invalid code and server failure show different explanations and appropriate recovery.
- Rapid code changes cannot leave a previous certificate's details visible as the new result.
- Missing optional fields, long Arabic names, keyboard links, and mobile widths remain readable.
