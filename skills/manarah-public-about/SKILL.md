---
name: manarah-public-about
description: Create the proposed /about page with a distinctive Manarah brand story, verified institutional facts, readable editorial sections, and clear public navigation.
---

# About Manarah

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| mapInstitutionFacts(publicData, approvedContent) | Pure content mapper | Select tenant identity and approved statements with explicit optional-field handling. |
| selectVerifiedStatistics(publicData) | Pure selector | Return only actual counts with faithful labels and missing states. |
| buildAboutSections(facts) | Pure composition model | Omit unsupported historical/contact sections while preserving a coherent narrative. |
| resolveInstitutionLinks(approvedLinks) | Content/navigation adapter | Validate permitted destinations and avoid invented addresses or contact channels. |

**Architecture boundary.** Keep factual content separate from layout and motion configuration. Reuse the public read model for existing counts rather than building a new about backend or generic CMS without need. Optional copy is editorial input, not inferred institutional history.

**Behavioral verification.** Verify missing organization fields, unknown counts versus zero, malformed optional links, and absence of fabricated accreditation or history in the mapped content.


Read [the shared design system](../manarah-design-system/SKILL.md). Status: proposed route `/about`. Start from the existing about section in `frontend/src/pages/Landing.jsx`. `GET /api/public/landing` provides tenant name/slug/color and student, teacher, and course counts; it does not supply a founding history, address, accreditation, or contact directory.

## Editorial composition

Use a full-width warm opening with a strong Arabic heading and a disciplined typographic composition inspired by a lighthouse beam. Follow with a mission narrative, a practical explanation of the learning journey, and a factual institution snapshot. Use a single contrasting deep-brand band for the core educational approach and a final invitation to meet teachers or discover courses.

Write copy around verified product behavior and user-supplied institutional facts. Do not invent founding dates, named leadership, accreditations, campus photographs, partnerships, testimonials, or success rates. If institution-specific copy is absent, explain what the platform enables and omit unsupported historical sections. Use illustration as illustration rather than implying a generated scene is a real campus.

## Links, content, and motion

Connect to `/teachers`, `/courses`, and `/register`, with login in the shared public header/footer. Provide contact or map links only when real institution details exist. Do not create a dead contact form or fabricated office locations to fill the layout.

Use a short section reveal, a single restrained beam-line transition, and ordinary button feedback. Content should remain legible without scrolling animations. Count-up, if used for returned counts, must settle once and render directly in reduced motion. Counts need factual labels and must not become achievement claims.

This is an editorial page: search, filters, charts, tables, and forms are unnecessary unless future verified content creates a concrete need.

## Acceptance scenarios

- Every institution-specific statement can be traced to supplied content or a real response field.
- Missing optional institution information results in a cohesive page without placeholder claims.
- The page has a distinct composition from home and features while sharing navigation and tokens.
- Long Arabic copy, mobile widths, keyboard links, reduced motion, and direct refresh work.
