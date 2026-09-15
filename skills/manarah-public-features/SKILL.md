---
name: manarah-public-features
description: Create the proposed /features page as an interactive product narrative showing Manarah learning and administration workflows without fabricated capabilities.
---

# Public Features

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| getRoleFeatureStory(role, content) | Pure content selector | Select supported role-specific narrative sections and fallback for an unknown tab value. |
| filterIllustrativePreview(rows, query) | Pure demo selector | Make preview filtering real while keeping illustrative data isolated from production APIs. |
| buildIllustrativeChart(rows) | Pure demo model | Derive labeled example totals from a single fixed dataset. |
| resolveFeatureCapability(feature, evidence) | Content policy | Distinguish supported, illustrative, and unavailable behavior based on maintained evidence; do not inspect secrets in the browser. |

**Architecture boundary.** Store role narratives and example data in a public content module, with presentational scenes and accessible local tabs. There is no new backend mutation for marketing previews. Production feature availability must not be fabricated from the presence of a frontend button.

**Behavioral verification.** Verify role content changes, accurate illustrative totals, no real message/payment/exam side effects from previews, and no private data fetched to populate the story.


Read [the shared design system](../manarah-design-system/SKILL.md). Status: proposed route `/features`. Reuse factual feature content from `frontend/src/pages/Landing.jsx` and verify behavior against the current application pages and backend. This page is a product narrative, not an authenticated dashboard.

## Narrative composition

Open with a large outcome-led heading and a compositional product preview. Continue through three substantial scenes: Organize learning, Follow progress, and Coordinate the institution. Use an alternating large preview and concise explanation, an annotated workflow strip, and a closing invitation to discover courses or register. Vary scale and section boundaries instead of repeating a six-card feature grid.

Use role tabs for Student, Parent, Teacher, and Administration to adapt explanations and static illustrative previews. Tabs must change actual visible content and remain keyboard-accessible. Use anonymous example names and clearly identify illustrative data; never embed private production screenshots or imply sample metrics are measured outcomes.

## Honest interactive previews

A small table preview can demonstrate search/filter behavior with a fixed illustrative dataset. A progress chart can demonstrate academic categories using explicitly labeled example data. Keep previews within the narrative and avoid making them look like live account access.

Describe implemented course organization, grades, attendance, and invoicing precisely. External messaging stubs are not confirmed delivery integrations. Do not claim secure anti-cheating, certified compliance, live video hosting, scanning readiness, or guaranteed performance without verified implementation. QR scanning requires the real encoding work identified in the attendance skill.

## Motion and conversion

Animate one learning path as its section enters; use a short chart draw and subtle button states for interactive previews. No scroll hijacking or mandatory animation to read text. Disable preview motion under reduced motion while retaining the data and explanation.

Keep `/login` visible and use `/courses` and `/register` for clear next steps. There is no lead form or request-demo submission contract in this specification.

## Acceptance scenarios

- Each role tab changes the story and gives a useful explanation of existing behavior.
- Illustrative filters and chart values work and are clearly identified as examples.
- Unimplemented capabilities are never presented as available actions.
- Mobile, keyboard, reduced motion, direct refresh, and public navigation work after implementation.
