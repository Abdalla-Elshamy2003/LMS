---
name: manarah-dashboard
description: Design Manarah's role-specific dashboard at /app, including admin analytics, teacher work, student progress, and parent child summaries.
---

# Dashboard

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadRoleDashboard(actor, signal) | Feature query adapter | Dispatch only to the role-appropriate existing endpoint and return a discriminated role view model. |
| mapDashboardMetrics(dto) | Pure model | Preserve units, missing values, category counts, and aggregation scope. |
| buildAcademicDrilldown(category) | Navigation model | Produce a supported students filter without changing its semantic category. |
| exportChildReport(childId, snapshot) | Export adapter and hook | Export the selected authorized child's captured data using existing report components; report failure independently per child. |

**Architecture boundary.** Keep role panels separate and avoid one component containing all request and transformation logic. AnalyticsService owns authoritative aggregations and scoped queries; the browser does not recompute institutional metrics from partial lists. The existing parent export is a distinct effect from fetching the dashboard.

**Behavioral verification.** Verify role dispatch, all-zero versus missing metrics, category totals, child identity during concurrent exports, and filtering context on drill-down.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Dashboard.jsx`; route: `/app`. Current endpoints are `/api/dashboard/admin`, `/teacher`, `/me`, and `/parent` under the dashboard prefix. Preserve the fallback welcome view for other roles.

## Role-specific composition

For admin roles, replace equal-weight KPI clutter with a clear hierarchy: an operational summary strip, a dominant academic-distribution panel, and an adjacent follow-up list. Show student, teacher, and course counts as context; distinguish collected money and outstanding balance from academic performance. Every figure needs an unambiguous label.

For teachers and assistants, lead with assigned courses and student counts, followed by direct links to available attendance, exam, and homework workflows. Do not invent a today's-sessions panel from a count-only response.

For students, lead with personal learning progress and accessible shortcuts. Keep points and rank secondary to academic actions. For parents, provide distinct child summaries and optionally a local child selector; never blend children into an unexplained average. Other roles receive a purposeful welcome with only permitted destinations.

## Charts, filtering, and interactions

Retain the academic category distribution as a compact donut or labeled horizontal bars, with exact category counts and a text/table alternative. Make a category drill-down open the students view with a supported academic-status filter; implement query restoration there before presenting the chart as interactive. Keep ranking and follow-up rows linked to authorized profiles.

Current dashboard requests contain no date or branch filter. A period selector needs corresponding server aggregation; do not add a decorative date dropdown. Label the current scope honestly. Do not fabricate historical trends, previous-period changes, or availability of unrelated roles' data.

Use short section reveals, a single initial chart draw, and gentle link-card hover feedback. Keep chart dimensions stable; settle metrics immediately for reduced motion. Missing metrics display unavailable state, not a false zero or invented percentage.

## Acceptance scenarios

- Check admin, teacher/assistant, student, parent with multiple children, and fallback role views.
- Distribution counts and labels match the response, including an all-zero dataset.
- A drill-down retains its intended filter and does not expose unauthorized records.
- A failed dashboard request produces retry instead of a permanent loader or null-data crash.
