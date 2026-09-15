---
name: manarah-audit
description: Design /app/audit as a precise read-only audit explorer with organized event columns, explicit timestamps, meaningful filters, and reliable pagination.
---

# Audit Log

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| normalizeAuditQuery(draft) | Pure query model | Validate supported date ranges, page bounds, and allowlisted sort/filter fields. |
| searchAuditEvents(query, actor) | Audit query service | Apply tenant/access scope and the same predicates to rows and total count. |
| mapAuditRecord(dto) | Pure display mapper | Preserve exact timestamps, unknown action codes, actor absence, and raw text safely. |
| recordAuditEvent(actor, change) | Audit application service | For existing audited mutations, capture the required event coherently with its command and avoid credential/private-payload logging. |

**Architecture boundary.** Keep this UI read-only. Audit writing belongs to the originating use case, never a client callback after a save. Query filters need verified backend support; a page-level text filter cannot fulfill searchAuditEvents across complete history.

**Behavioral verification.** Verify unauthorized/cross-tenant queries, stable pagination when timestamps tie, failed page requests, safe rendering of markup-like values, and transaction rollback for a command requiring an audit record.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Audit.jsx`; route: `/app/audit`. Current request: `GET /api/dashboard/audit` with `page` and `size: 20`. Existing fields include actorName, action, entityType, entityId, newValue, and createdAt.

## Layout and record detail

Use a restrained administrative header and a dominant semantic table. Columns: timestamp, actor, action, entity type, entity ID, and a concise change preview. Isolate IDs and timestamps for mixed-direction readability. Show the display timezone and make exact timestamps available; relative time alone is inadequate for investigation.

Provide an accessible row detail disclosure for long values. Render content as text, not HTML. Show before/after comparison only if both values exist; the current visible source uses newValue. Preserve unknown action codes beside readable labels. Missing actor information should not be replaced with an invented person.

## Filters and pagination

Target filters are date range, actor, action, entity type, and entity ID. The current page sends only page and size. Inspect the audit controller before implementing full-dataset filters; add matched backend support when in scope. Until then, label any local filtering as applying to the loaded page and keep the global total distinct. Validate date-range order and reset page when real filters change.

Keep a stable default chronological ordering, accurate total/page controls, and a no-matches state separate from an empty audit log. Back navigation should restore supported filters. Do not include raw change contents or sensitive actor searches in shareable URLs.

## Motion and actions

Audit records are read-only. Do not add delete, edit, or bulk mutation controls. Exports need a supported scoped data path; do not label a 20-row client export as complete history. Use a short detail-panel reveal and subtle row hover, with stable ordering and no continuous animation. Charts are unnecessary unless actual aggregates answer a specific audit question.

## Acceptance scenarios

- Direct-route authorization and forbidden states match the administrative access model.
- Long change values, unknown action codes, and missing actors remain readable.
- Pagination boundaries and full-dataset versus loaded-page counts are unambiguous.
- Failed page fetch retains useful context and provides retry without showing stale rows as current.
