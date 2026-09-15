---
name: manarah-landing
description: Redesign Manarah's public home page at / into a distinctive editorial learning experience and connect its multi-page public website.
---

# Public Home

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadPublicLanding(tenantSlug, signal) | Public API adapter | Return only the public tenant, stats, course, and teacher DTO; support cancellation and distinguish unavailable data from an empty list. |
| selectHomeSections(publicData, editorialConfig) | Pure presentation model | Choose course/teacher previews deterministically without mutating the catalog or inventing ranking claims. |
| buildPublicDestination(route, context) | Navigation model | Preserve supported tenant/course context using encoded local links; reject private-field propagation. |
| usePublicLanding(tenantSlug) | Feature hook | Own loading, error, retry, and shared public-data lifecycle; invalidate when institution context changes. |

**Architecture boundary.** Keep hero art and section components presentational. Extract the landing read aggregation from PublicController into a focused public query service when that backend path is changed. Reuse a narrow public DTO across public pages; avoid repeated per-teacher course queries.

**Behavioral verification.** Verify institution switching with responses arriving out of order, empty public lists, and navigation context. Prove anonymous rendering never requires a private endpoint.


Read [the shared design system](../manarah-design-system/SKILL.md). Current source: `frontend/src/pages/Landing.jsx`; route: `/`. The existing page combines teacher search, course cards, features, about content, and registration in anchor sections. `GET /api/public/landing` provides tenant identity, counts, teachers, and courses.

## Composition

Build an asymmetric opening: a large, carefully broken Arabic headline on the start side, a short useful explanation, and a sculptural learning-path illustration on the opposite side. Use the lighthouse beam as a directional device that guides the eye toward the primary action. An offset course preview can overlap the illustration without overlapping text or focus targets. Keep illustration optional and the headline/CTA usable before it loads.

Follow with a compact factual counts strip, a curated course shelf, an editorial teacher spotlight, a three-step learning journey, and a deep-brand closing invitation. Vary section proportions and backgrounds. Use generous whitespace, directional rules, and real content rather than repeated icon grids, generic gradient blobs, rotating carousels, or fabricated testimonials. Counts must come from the response; describe sample environments honestly.

## Navigation and conversion

Use the shared public navigation with real links to `/courses`, `/teachers`, `/features`, `/about`, `/register`, and `/login`. Provide clear login access in the header, mobile navigation, and closing section. Make course discovery the primary hero action and registration a clear subsequent step. Link preview shelves to full public directories. Preserve old useful anchors during extraction, including a registration teaser or redirect-compatible section.

Treat the five additional public routes as new work, guided by their individual skills. Multiple sections in `/` do not satisfy the multi-page requirement. Do not send anonymous visitors into protected `/app` course pages as their default discovery flow.

## Motion and states

Stage headline, supporting copy, and illustration in a short sequence. Use one gentle light-beam reveal; hover cards can lift within the shared motion budget. Keep the hero static for reduced motion and constrained devices. Reuse or simplify `Hero3D` only when it serves this composition. Static artwork should reserve the same dimensions.

Keep public navigation usable if landing data fails; show retry within the affected content region. Empty course and teacher shelves should carry an honest message and a useful route. Never put private student names, results, or payment records into public preview graphics.

## Acceptance scenarios

- A mobile visitor can see login immediately and reach course discovery without opening a heavy animation.
- Every public navigation destination loads independently and survives a direct refresh once implemented.
- API failure does not remove the page headline, navigation, or registration entry point.
- Reduced motion retains the complete composition and final metric values.
