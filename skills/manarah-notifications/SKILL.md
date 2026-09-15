---
name: manarah-notifications
description: Design /app/notifications as a readable inbox with filters, reliable read state, accessible feedback, and clear delivery-channel semantics.
---

# Notifications

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadNotifications(query, signal) | Notification query adapter | Return pagination and matching scope without treating 40 fetched messages as complete history. |
| markNotificationRead(id, actor) | Notification application service | Verify recipient ownership and persist a repeat-safe read transition. |
| markAllNotificationsRead(actor) | Notification application service | Apply the actual actor-wide scope independent of visible filters and return sufficient state to reconcile counts. |
| reconcileUnreadState(previous, mutationResult) | Feature state controller | Update inbox and shell count coherently, rollback optimistic changes on failure, and resist stale polling. |
| mapDeliveryState(notification) | Pure model | Keep read state, channel, queued status, and provider-confirmed delivery distinct. |

**Architecture boundary.** Move shared unread state to a deliberate application-owned boundary rather than independent conflicting counters. NotificationService owns recipient scope; provider adapters own delivery translation. Do not infer provider success from a channel icon or an in-app read action.

**Behavioral verification.** Verify another user's notification ID, repeated read, mark-all under active filters, failed optimistic updates, and an older unread poll arriving after a successful mutation.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Notifications.jsx`; route: `/app/notifications`. Current requests are `GET /api/notifications?size=40`, `POST /api/notifications/{id}/read`, and `POST /api/notifications/read-all`. The shell retrieves `/api/notifications/unread-count` separately.

## Inbox layout

Use a calm inbox header with an unread summary, All/Unread controls, channel filter, and Mark all read action. Group items by readable dates where useful. Each item shows title, body preview, channel, timestamp, and explicit unread state. Preserve readable contrast for read items rather than lowering the entire item opacity.

Use a selected-item detail panel for long messages when needed, with mobile full-width treatment. Do not force an operational table on a personal inbox; a compact list better serves reading. If an administrative channel log is introduced, it needs its own supported scope.

## Filtering and state

The current request retrieves at most 40 records. Add pagination or supported load-more and server search/filtering before claiming inbox-wide results. Local unread/channel/text filtering must identify loaded-item scope if server support is absent. Preserve selection and scroll on refetch.

Marking a message read should update that item and reconcile the shared unread badge. If optimistic updates are used, roll back on failure and show retry. Mark all read means the endpoint's actual scope, even with a filter active. Disable meaningless repeat actions while a request is pending.

Channel icons are IN_APP, WHATSAPP, SMS, EMAIL, and PUSH. A channel label or queued record does not prove delivery. Preserve pending/read/delivery distinctions supported by the backend; external senders may be stubs. Do not add reply or broadcast forms unless connected to a real communication workflow.

## Motion and acceptance

Use a subtle unread-dot fade and selected-item background transition. Removing an item from Unread should preserve a sensible keyboard focus target. Do not replay the full feed entrance after every read action.

- Read failure restores correct unread state and count.
- Empty inbox and empty filter results have distinct messages.
- A filtered view cannot misrepresent Mark all read as affecting only visible items.
- Long bodies, mixed-direction content, keyboard reading, and pagination remain usable.
