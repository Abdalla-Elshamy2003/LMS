---
name: manarah-leaderboard
description: Refine /app/leaderboard with a dignified achievement podium, precise ranking rows, restrained celebration, and honest ranking scope.
---

# Leaderboard

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadLeaderboard(scope, signal) | Ranking query adapter | Fetch only supported scope and expose any server cap or period semantics. |
| deriveRankedRows(response) | Server ranking policy / display mapper | Preserve authoritative ordering/ties; establish rank semantics before deriving them from array position. |
| selectRankedRows(rows, query) | Pure selector | Filter by name/level without renumbering the original ranks. |
| selectCurrentStudentRow(rows, identity) | Pure selector | Highlight only a verified student identity mapping. |

**Architecture boundary.** GamificationService owns point awards and ranking rules. Do not calculate awards from frontend animations or create client-only periods. Keep podium and table fed by the same ranked view model, with explicitly defined full-board versus filtered display.

**Behavioral verification.** Verify ties, fewer than three students, filtered original ranks, capped results, unknown identity, and idempotent point-event handling when that backend path changes.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Leaderboard.jsx`; route: `/app/leaderboard`. Current data comes from `GET /api/dashboard/leaderboard`, with student identity, name, points, and level.

## Composition and ranking

Create a restrained achievements masthead, a three-place podium, and an orderly ranking table. Use a single warm medal accent for first place and quieter treatments for second and third. Keep rank explicitly written; visual podium position must not be the only rank cue. Stack or simplify podium entries on narrow screens and handle fewer than three students naturally.

Table columns are rank, student, level, and points. Keep points aligned and names readable. Preserve authoritative ordering and ties from the backend contract. If the response lacks a rank field, confirm how array order and equal points should be represented before adding derived ranks. Never portray academic outcomes as identical to gamification points.

## Filters and interaction

Offer name search and level filtering only within the returned scope, labeling that scope if the endpoint is capped. Preserve each student's original rank when filtering; a filtered subset must not become a new competition. Weekly/monthly or course-based boards require real server scoring scope and must remain proposed until implemented.

Highlight the current student only when a verified student identity mapping exists. Avoid unnecessary links into student profiles for viewers without access. There is no form workflow on this page.

## Motion and acceptance

Reveal the podium in one short sequence and use a gentle medal highlight. Avoid endless confetti, rotating medals, rank-shuffling animation, or counters that obscure final points. Keep table rows stable and reduced motion fully static. Charts would duplicate the ranking and are unnecessary.

- Zero, one, two, and many ranked students produce balanced layouts.
- Search does not renumber students or change the underlying podium claim.
- Ties and unavailable current-user identity are handled without invented ranks.
- All rank and level meaning remains available without color or animation.
