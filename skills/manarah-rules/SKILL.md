---
name: manarah-rules
description: Design /app/rules with readable automation summaries, useful rule filters, deliberate enabled states, and a guided notification rule builder.
---

# Notification Rules

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectRules(rules, query) | Pure selector | Combine actual trigger/channel/enabled/name fields and expose result count. |
| validateRuleDraft(draft, triggerPolicy) | Pure form model and server policy | Enforce relevant threshold units/bounds, channels, and recipient choices; remove inapplicable values. |
| describeRule(draft) | Pure preview formatter | Generate a sentence that matches the serialized command exactly. |
| createNotificationRule(command, actor) | Rules application service | Authorize administration, validate supported channels/triggers, and persist the rule. |
| setRuleEnabled(ruleId, enabled, actor) | Target application command | Prefer explicit desired state over a retry-sensitive toggle; adapt the existing toggle endpoint deliberately rather than pretending it already accepts enabled. |

**Architecture boundary.** Keep rule editing separate from rule evaluation and channel sending. NotificationRulesEngine owns trigger interpretation. Inspect sender configuration before labeling a channel available: an installed adapter is not evidence of configured delivery. Serialize channel choices only at the adapter boundary.

**Behavioral verification.** Verify trigger-switch stale thresholds, invalid channels, preview/payload agreement, concurrent toggle conflicts, and retried event evaluation without duplicate notifications.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Rules.jsx`; route: `/app/rules`. Existing operations read/create `/api/notifications/rules` and toggle `/api/notifications/rules/{id}/toggle`.

## Layout and filtering

Use a readable rule list with name, trigger, threshold where meaningful, recipients, channels, and enabled state. A compact When / Then sentence gives each rule immediate meaning without a decorative node graph. For dense rule sets, use the same fields in a table with a labeled toggle and a detail action only if implemented.

Search by name and filter by trigger, enabled state, and channel over the returned complete list. Show active filter chips and count. Keep the toggle's saved state separate from its pending state; rollback if the server rejects the request. Do not place rule execution on a hover action.

## Guided form

Replace the comma-separated channel text input with labeled channel choices while serializing the existing payload. Organize the form into Name, When, Conditions, and Notify. Preserve existing triggers: STUDENT_ABSENT, LOW_SCORE, HOMEWORK_MISSED, RISK_ESCALATED, and INSTALLMENT_DUE.

Display threshold only when its interpretation is confirmed for that trigger by the backend rule evaluation. Include its unit, valid bounds, and plain-language meaning; a generic numeric box is insufficient. Recipients use the supported parent/teacher booleans. Require applicable recipient/channel choices according to the real contract. Show a live sentence preview that accurately reflects the submitted fields.

Expose channel availability clearly. In-app delivery and external stub implementations must not share an unqualified Sent claim. A preview is not a live test; do not invent test-send or successful external delivery. Switching trigger should remove irrelevant stale threshold values from the payload.

## Motion and acceptance

Use a restrained switch transition and short reveals for conditional fields. Keep labels stable, preserve focus, and avoid animating a flowchart unnecessarily.

- Each trigger produces a meaningful form and faithful preview.
- Toggle failure restores the previous state and makes retry clear.
- Channel serialization and recipient values match the current API contract.
- Filters, long rule names, reduced motion, and keyboard toggles work coherently.
