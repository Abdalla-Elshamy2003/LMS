---
name: manarah-login
description: Refine the Manarah login page at /login with a polished Arabic form, restrained brand motion, and clear authentication states.
---

# Login

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| validateLogin(values) | Pure form model | Return field errors for missing/invalid email and missing password; never trim or transform a password. |
| authenticate(credentials) | Auth API adapter and server auth service | Exchange credentials for the existing accessToken/user response; surface authentication failure without exposing credential details. |
| restoreSession(signal) | Auth provider | Resolve the existing session once per lifecycle; distinguish invalid credentials from a temporary service failure. |
| resolvePostLoginRoute(user, returnTo) | Pure navigation policy | Choose a permitted local destination and prevent external/open redirects. |
| endSession() | Auth provider | Clear session state and actor-scoped cached data, then navigate coherently. |

**Architecture boundary.** Keep authentication in the existing auth boundary rather than duplicating token access in the page. The server owns credential checks and actual roles. A visibility toggle is a local component concern, not a separate application service.

**Behavioral verification.** Verify failed login preserves email, double submit makes one active request, stale session restoration cannot overwrite a new login, and logout removes previous-user data.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Login.jsx`, with `frontend/src/lib/auth.jsx` owning authentication. Route: `/login`. Preserve the existing login contract and authenticated navigation flow.

## Layout and form

Use a quiet split composition: a deep-brand architectural illustration and a short brand statement beside a warm, spacious sign-in surface. On mobile, place the form first after a compact brand header. The form should feel integrated into the page, with one subtle boundary rather than several nested cards.

Keep visible labels for email and password, correct autocomplete, LTR email entry, and a labeled password visibility control that never submits the form. Support Enter submission and password-manager filling. Provide a clear home link and, when the proposed route exists, a registration link. Do not add password reset, social login, or remember-me controls unless their real flows are implemented.

The current page prepopulates demo credentials. For a production-facing redesign, use empty fields and place any explicitly enabled demo shortcuts in a separate, clearly labeled demo section. Never treat a chosen visual role as authority; the authenticated server response determines role access.

## Feedback and motion

Validate missing and malformed input before sending. Keep authentication errors near the form and preserve the email after failure. Disable duplicate requests while keeping button width stable. On failure restore submission; on success follow the actual auth flow. If return-to navigation is added, restrict it to appropriate local routes.

Use a 200-280ms form entrance and 140-180ms button feedback. Show a spinner inside the primary action, with accessible pending text. Avoid shaking fields, moving input labels, or animating password contents. Background art must not steal focus or remain animated under reduced motion.

## Acceptance scenarios

- Keyboard-only login, show/hide password, and error recovery work without focus loss.
- Wrong credentials, server unavailability, and a successful response produce distinct states.
- The form fits a 360px viewport with the on-screen keyboard; no key action is covered.
- Demo shortcuts cannot be mistaken for normal account creation or role selection.
