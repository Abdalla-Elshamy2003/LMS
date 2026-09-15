---
name: manarah-attendance
description: Design the attendance workspace at /app/attendance, including session discovery, a precise roster editor, bulk status changes, and a usable QR display.
---

# Attendance

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectSessionRows(sessions, query) | Pure selector | Filter supported date/course fields with explicit date semantics. |
| applyRosterStatus(draft, targetIds, status, lateMinutes) | Pure roster reducer | Change exactly the chosen rows, preserve UNMARKED, and retain hidden edits. |
| saveAttendance(sessionId, changes, actor) | Attendance application service | Authorize session/roster membership, validate status/late minutes, persist coherently, and emit each intended domain event once. |
| issueSessionQr(sessionId, actor) | Server use case | Issue a signed/validated token with real expiry and permitted session scope. |
| encodeAttendanceQr(payload) / deriveQrExpiry(expiresAt, now) | QR adapter / pure time model | Render a real code and derive countdown from a deadline rather than decrement drift. |

**Architecture boundary.** Keep roster editing in a reducer and QR request/expiry state in a focused hook. Inject time into server expiry validation; the server owns acceptance. Use explicit tenant data for attendance events and preserve the existing downstream metrics/notification path.

**Behavioral verification.** Verify bulk-action scope, duplicate roster IDs, out-of-session students, save failure with retained edits, token expiry, invalid token handling, and repeated check-in policy.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Attendance.jsx`. Existing operations use `/api/attendance/sessions`, `/sessions/{id}/roster`, `/sessions/{id}/mark`, and `/sessions/{id}/qr` under the attendance prefix.

## Session and roster layout

Use a session worklist with readable date/time, course/session title, location when returned, and explicit Open roster and Display QR actions. Offer search and date/course filters only over available fields and a complete list. Show a chosen date range and the matching session count.

Make the roster a generous dialog or dedicated work panel. Use student name/code, labeled attendance choices, and late minutes where supported. Include UNMARKED as a distinct state; it must not silently become absence. Keep a visible unsaved-change count and Save attendance action.

Search the roster by name/code and filter by current attendance status. Bulk actions must state their scope: all roster students, filtered students, or selected students. Preserve changes when filtering. A mark-all action should be reversible locally before save and must not overwrite hidden rows without an explicit scope. Validate late minutes only for the appropriate status and send the established payload.

## QR behavior

The current `QrPattern` is explicitly a decorative QR-like grid. A redesign must replace it with a real scannable encoding using `frontend/src/lib/qr.js` where suitable and the backend's expected token/check-in contract. Do not claim scanning works from an attractive pattern alone.

Display a high-contrast stationary QR with an adequate clear border, session context, expiration countdown, and a labeled refresh action. Read TTL from the server; show an expired state at zero. Claim automatic rotation only if it is implemented. Keep tokens out of decorative captions and ordinary logs.

## Motion and acceptance

Animate selected status backgrounds gently and save feedback once. QR pixels remain static; countdown width remains fixed. Reduced motion should retain exact expiration information.

- Filtered bulk marking touches exactly the stated students; unsaved changes survive search.
- Save failure preserves edits and allows a safe retry.
- A real QR scan decodes the intended payload, and expired tokens are not presented as valid.
- Keyboard and mobile users can distinguish every attendance state without icon guessing.
