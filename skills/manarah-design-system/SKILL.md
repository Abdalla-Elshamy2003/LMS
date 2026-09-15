---
name: manarah-design-system
description: Design or refine shared Manarah LMS UI foundations, Arabic RTL layouts, motion, tables, filters, forms, charts, and public navigation. Load alongside the relevant Manarah page skill.
---

# Manarah Design System

## Shared functional responsibilities

Apply [the clean architecture contract](../manarah-clean-architecture/SKILL.md) together with page-specific functional sections. The following are suggested responsibilities, not a requirement to create a new abstraction for every primitive.

| Function / capability | Owner | Required contract |
| --- | --- | --- |
| `getVisibleNavigation(actor, routeConfig)` | Pure shell policy | Derive navigation from one role/capability map; server authorization remains authoritative. |
| `normalizeListQuery(draft, supportedFields)` | Shared pure query utility | Bound pagination, preserve stable sorting, and serialize only allowlisted non-sensitive query state. |
| `mapApiError(error)` | Shared transport boundary | Normalize the existing response into usable field/page errors without losing HTTP semantics or exposing raw internal failures. |
| `useAccessibleDialog(open, triggerRef)` | Shared interaction hook | Own focus trap/return, Escape, and cleanup; feature forms own unsaved-change decisions. |
| `resolveMotionPreferences(userPreference, systemPreference)` | Shared motion policy | Feed CSS, Framer Motion, counters, charts, and 3D from a consistent reduced-motion decision. |
| `buildChartView(data, metricDefinition)` | Pure presentation adapter | Map known units and missing states, produce accessible labels, and never invent missing historical series. |
| `useGlobalSearch(query, actorScope)` | Search feature hook | Debounce/cancel requests, clear results on scope change, and return only authorized destination types. |

Keep Button, Card, DataTable, FilterBar, FormField, and chart wrappers controlled through explicit props. Their implementations may be extracted from the existing shared UI file when cohesion warrants it. A DataTable renders rows and reports sort/page intent; the owning feature decides whether filtering is server-side or local. A FormField renders labels/errors; the form model owns validation. A chart wrapper formats data; it does not fetch analytics. Avoid a single shared component with flags for every business page.

Verify navigation consistency for direct routes, shared focus behavior, stale global-search results after logout, and query normalization with unsupported keys. Shared primitives should be exercised through meaningful representative page flows instead of snapshots that only mirror their markup.

## Purpose and source of truth

Create a distinctive education platform with an editorial public website and a calm, precise application workspace. These are implementation instructions, not evidence that the redesign has shipped. Keep skill documentation in English and product copy in Arabic with RTL layout.

Inspect `frontend/src/App.jsx`, `components/Layout.jsx`, `components/ui.jsx`, `index.css`, `tailwind.config.js`, and the target page before changing UI. Paths beginning with `frontend/` or `backend/` in this collection are relative to the project root. Reuse the installed React, Tailwind, Framer Motion, Recharts, and Lucide stack. Treat backend controllers and DTOs as the authority for data, permissions, and operations.

## Art direction: the learning observatory

Use Manarah's lighthouse identity through an architectural light beam, curved learning paths, large Arabic typography, and quiet grid lines. Compose public pages with asymmetric proportions, full-width narrative bands, and carefully cropped subject imagery. Give operational pages a strong title, a compact context strip, and a dominant work area. Avoid giving every element an identical floating card or putting gradients behind every heading.

Proposed shared tokens, to be mapped into the existing theme during implementation:

| Token | Value | Purpose |
| --- | --- | --- |
| Canvas | `#F6F7FB` | Application background |
| Surface | `#FFFFFF` | Forms and data surfaces |
| Public canvas | `#F8F5EF` | Warm editorial sections |
| Ink | `#0F172A` | Primary text |
| Muted ink | `#475569` | Secondary text |
| Brand | `#0369A1` | Primary actions and links |
| Deep brand | `#082F49` | Navigation and public feature bands |
| Accent | `#B45309` | Restrained highlights on light surfaces |
| Success / warning / danger | `#047857` / `#B45309` / `#BE123C` | Semantic states with text labels |
| Border | `#E2E8F0` | Decorative separation; strengthen for input boundaries |

Use Cairo with the existing fallback stack; keep Arabic body text around 16px and comfortably spaced. Use 14px for dense table content, with readable supporting labels. Public hero type can scale from 36px to 72px; application headings from 24px to 32px. Use an 8px spacing rhythm with 4px detail spacing, 12px control radii, and 16-24px major surface radii. Keep public content near 1280px wide; data pages can use the available workspace width.

## Shared shell and navigation

Keep the authenticated sidebar on the RTL start edge, with role-aware groups, a visible active indicator, breadcrumbs on detail pages, and a mobile drawer. Preserve `GlobalSearch`, the notification badge, and logout. Hiding navigation is not authorization: inspect endpoint restrictions and gate unavailable operations and direct routes appropriately.

Public navigation must connect real routes: `/`, `/courses`, `/teachers`, `/features`, `/about`, `/register`, and `/login`. Of these navigation destinations, `/` and `/login` currently exist; the other five are proposed in this collection. The existing public verification route `/verify/:code` serves certificate QR links and does not need a primary navigation item. Keep a text-labeled login action visible on desktop and mobile. Use real links, active states, shared header/footer, page titles, and focus placement after route changes. Preserve useful old section links when extracting the landing page.

## Motion contract

| Element | Target behavior | Limits |
| --- | --- | --- |
| Buttons | 140-180ms color/shadow transition; press scale near 0.98 | Fixed label width while submitting; no hover-only meaning |
| Clickable cards | 180-240ms border/shadow change and lift up to 3px | No tilt on data cards; static cards should not imply clickability |
| Page sections | 220-320ms opacity and 8-12px translation | One entrance; total initial stagger budget below 400ms |
| Tabs and filters | 160-220ms active indicator and panel transition | Preserve focus and scroll; no shifting controls |
| Dialogs and drawers | 180-240ms restrained entrance/exit | Focus management must not wait on animation |
| Charts | 450-650ms first meaningful draw; 200-300ms data updates | Keep axes and card heights stable; never interpolate misleading values |
| Success feedback | 180-250ms icon or status transition | Persistent text confirmation where the result matters |

Centralize motion presets. Respect `prefers-reduced-motion` in CSS, Framer Motion, Recharts, number counters, SVG progress, and any 3D scene. In reduced motion, render final values directly and disable translation, parallax, looping art, and count-up. Do not stagger long tables. Pause decorative work offscreen; provide a static fallback for `Hero3D` when graphics are unavailable. Never animate QR modules or timer digits with layout changes.

## Tables and filtering

Use semantic tables with captions, column headers, clear row separators, 48-56px comfortable rows, a distinct header, and aligned numeric columns with tabular figures. Keep names readable and use an isolated LTR span for mixed-direction codes, money, and phone numbers. Right-align Arabic labels. Allow column headers to wrap deliberately. Put secondary row actions in a labeled menu; do not nest buttons inside a clickable row.

Place search, the two most useful filters, result count, and clear-all above the data. Advanced filters belong in a drawer with an explicit apply action. Show removable active filter chips. Persist non-sensitive filters, sorting, and pagination in query parameters; keep phone numbers and other sensitive search text out of URLs. Debounce remote search around 300ms, ignore stale responses, and reset pagination when the query changes. Back navigation should restore the view.

Filter and sort the full dataset before pagination. For paginated APIs, implement supported server query parameters and matching totals; never filter one fetched page and label it a global result. If only loaded-page filtering is possible, label its scope honestly. Local filtering is suitable for endpoints returning complete lists. Do not display nonfunctional filters for missing fields. Label all-data versus filtered summaries explicitly. Provide pagination, loading placeholders matching the layout, empty-data and no-matches states, clear errors, and retry without losing filters.

On narrow screens, keep the entire page within the viewport. Use a labeled horizontal table scroll region with a visible scroll cue or deliberately designed record cards. Keep essential identity, status, value, and action fields visible. Never shrink a desktop table until its labels become unreadable.

## Forms and dialogs

Group related inputs with concise section titles. Use one column on mobile and at most two for related short fields on desktop. Full-width names, descriptions, and long selections improve scanning. Every field needs a persistent associated label; include format hints, optional/required state, and field-level errors. Preserve values after failures. Validate on submit and on blur after interaction, focus the first invalid field, and connect errors through accessible descriptions. Trim incidental whitespace without altering names or passwords.

Use appropriate input types, autocomplete, and input modes. Keep numeric constraints aligned with backend rules; normalize Arabic digits deliberately where numeric input requires it. Searchable selects should preserve keyboard interaction and display names rather than raw IDs. Show a summary before consequential submissions. Use one primary action, a clear cancel/back action, pending state, and a success state based on the response. Prevent duplicate submission; do not invent successful saves or silently retry a non-idempotent operation after an ambiguous failure.

Dialogs need a title, accessible dialog semantics, focus trap, Escape behavior, background inertness, and focus return to the trigger. Long forms need a visible action area without covering fields on mobile. Ask about discarding changes when closing would lose meaningful work. Uploads need a file picker as well as optional drag-and-drop, allowed formats from backend constraints, per-file progress/error states, and recovery from partial success.

## Charts and readable states

Choose charts from available data: category comparison uses bars, part-to-whole uses a donut only when helpful, and time trends require actual timestamped series. Do not invent sparklines or percent changes from single aggregate values. Show units, range/scope, understandable legends, an accessible text summary or data table, and keyboard-accessible equivalents of meaningful hover details. Keep missing data distinct from zero. Use labels or patterns alongside color. Provide explicit empty, error, permission-denied, and unavailable-feature states.

## Delivery and verification

Implement shared primitives before repeating page patterns. Preserve contracts and role distinctions while improving presentation. For new functionality described by a page skill, verify its backend support and complete the data path or mark the feature as proposed; a styled inactive button is not completion.

Verify the affected routes at 360px, 768px, and 1440px widths, Arabic RTL, long names, keyboard navigation, reduced motion, slow/failing requests, empty results, and relevant roles. Check readable contrast, visible focus, touch targets around 44px, and no unintended horizontal page overflow. Run `npm run build` from `frontend` for implementation changes. For documentation-only changes, validate the skills and their references instead of claiming a UI build proves the specification.
