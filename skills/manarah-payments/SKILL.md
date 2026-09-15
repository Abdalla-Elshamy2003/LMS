---
name: manarah-payments
description: Design /app/payments as a precise invoice workspace with aligned monetary tables, combined filters, clear totals, and deliberate reminder actions.
---

# Payments

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| selectInvoiceView(invoices, query) | Pure selector | Return filtered/sorted rows and explicitly scoped display totals. |
| loadInvoiceDetail(invoiceId, actor) | Payment query service | Authorize the invoice before returning ledger/installment details. |
| createInvoice(command, actor) / recordPayment(command, actor) | Payment application service | For the proposed entry UI, validate references, decimal amounts and applicable overpayment policy; atomically update ledger and invoice state. |
| runInstallmentReminders(actor) | Payment/reminder use case | Use the actual global eligible scope, prevent unintended duplicate reminder effects, and report processed/queued outcomes accurately. |
| exportFinancialReport(snapshot, scope) | Report adapter | Use the existing FinancialReportPrint path with an explicit all-invoices or filtered-results label. |

**Architecture boundary.** PaymentService already exposes createInvoice and recordPayment; wiring professional entry forms remains proposed frontend work. Centralize money and status transitions on the server with decimal arithmetic and a database-supported concurrency strategy. Keep PDF capture independent of financial calculation and remote sending outside the ledger transaction.

**Behavioral verification.** Verify 0.01-level precision, concurrent payments, repeated command behavior, foreign-tenant invoices, filtered/exported total reconciliation, and reminders with filters active. Do not assume overpayment rejection is the current rule; define and test it.


Read [the shared design system](../manarah-design-system/SKILL.md). Source: `frontend/src/pages/Payments.jsx`. Current UI reads `/api/payments/invoices`, loads courses, and calls `/api/payments/run-reminders`. It currently provides no payment-entry form. Access is limited by the payment controller to the existing administrative/accounting roles.

## Composition and table

Use a restrained financial overview with collected and remaining balances above a dominant invoice table. Avoid decorative charts built from invented transaction history. The table should include student, invoice description, total, paid, remaining, and status. Preserve monetary precision, use consistent EGP formatting, and align the three monetary columns with tabular numerals.

A remaining balance is not automatically overdue. Use overdue language only when due-date semantics establish lateness. Keep PENDING, PARTIAL, and PAID visible as text. Add invoice detail access only through a real supported workflow.

## Filters and totals

Combine student search, course selection, and status chips over the complete returned invoice list. Add numeric/date filters only when their fields are available. Provide sorting by student, amount, and remaining balance, with a stable tie-breaker. Paginate after local filtering/sorting if the endpoint still returns a full list.

The current totals are calculated before filtering. Either label them All invoices and add a matching-results subtotal, or derive the visible summary from the filtered set. Never let a selected course silently coexist with unlabeled global totals.

## Actions and future forms

The reminders endpoint is a global operation, not a filtered-table action. State the real scope before execution, disable repeat clicks while pending, and distinguish a queued/processed count from confirmed external delivery. Read the server response and channel implementation before choosing success copy.

If invoice creation or payment recording is implemented later, inspect the controller contract first. Use a structured form with student/invoice context, supported amount/method/reference/date fields, and a review summary. Prevent duplicate recording; do not add checkout or payment-provider claims to this ledger view.

## Motion and acceptance

Use brief button pending/success transitions and stable summary surfaces; monetary values should settle directly on filter changes. Table rows need only a subtle hover state.

- Filtered subtotals reconcile with visible invoices; all-data totals are labeled.
- Decimal values, zero balance, missing fields, no matches, and failed fetch remain understandable.
- Reminder scope is explicit even when table filters are active.
- A failed action never displays a success count or clears useful filter context.
