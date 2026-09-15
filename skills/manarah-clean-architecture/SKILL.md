---
name: manarah-clean-architecture
description: Implement or refactor Manarah LMS use cases with feature boundaries, clean React code, Spring application services, tenant authorization, transactional consistency, and meaningful verification. Apply alongside the relevant page skill.
---

# Clean Code and Application Architecture

## Scope and current baseline

Use this skill with the relevant page skill when implementing its functional requirements. The named functions in this collection describe responsibilities and suggested interfaces; they are not a claim that those symbols already exist. Reuse equivalent existing functions before introducing new ones. A specification update does not itself implement or authorize an unrelated application-wide rewrite.

The current frontend uses React JavaScript pages, shared components, an Axios adapter in `frontend/src/lib/api.js`, and authentication in `frontend/src/lib/auth.jsx`. The backend is a Spring modular monolith organized by business feature, with controllers, DTOs, services, JPA domain entities, repositories, and integration listeners. Evolve those seams incrementally. Do not introduce microservices, replace the database, migrate to TypeScript, or add a state framework merely to satisfy an architecture label.

## Dependency boundaries

| Boundary | Owns | Must not own |
| --- | --- | --- |
| React route/page | Route parameters, role-specific composition, page-level fallback | API payload construction, grading rules, money calculations, multi-step persistence |
| Feature components | Display, accessible interaction, local transient state | Authentication storage, direct Axios calls, permissions as the only enforcement |
| Feature hook/controller | Request lifecycle, form state, cancellation, pending/error recovery | Authoritative business rules or duplicated server data in multiple stores |
| Pure model/selectors | Query normalization, view models, derived display values, form validation | React effects, network, localStorage, DOM, mutable module-global state |
| Feature API adapter | URL/DTO mapping, request options, transport errors | Toasts, navigation, JSX, final authorization decisions |
| Spring controller | Parse/bind/validate request, obtain principal, call a use case, return DTO | Repository orchestration, transaction workflow, pricing/grading logic |
| Application service/use case | Authorization, transaction boundary, domain workflow, result mapping | HTTP response objects, presentation formatting, external SDK details |
| Domain policy | Invariants, valid transitions, calculations | Servlet requests, browser concerns, provider APIs |
| Persistence/integration adapter | Scoped queries, storage, provider translation | UI decisions, bypassing use-case checks |

Keep feature dependencies acyclic. A shared module must not import a page or feature module. Cross-feature workflows should call a deliberate application interface or an existing domain event, not another feature's controller. JPA entities currently carry persistence annotations: preserve this practical baseline; extract pure policies where valuable instead of duplicating every entity into a second model without a demonstrated need.

## Suggested organization

These are target patterns, not directories that already exist or folders to generate eagerly:

```text
frontend/src/
  pages/Students.jsx                 route composition
  features/students/
    students.api.js                  transport adapter
    useStudents.js                   query and mutation lifecycle
    students.model.js                pure selectors and validation
    components/StudentForm.jsx       feature UI
  components/ui.jsx                  existing shared primitives
  lib/api.js                        existing shared transport

backend/src/main/java/com/manarah/student/
  StudentController.java            existing HTTP entry point
  StudentDtos.java                  existing request/response contracts
  StudentService.java               existing application service
  domain/                           existing entities; pure policies as needed
  repo/                             existing scoped persistence
```

Split a service or hook when separate reasons to change emerge, such as exam authoring versus attempt submission. Do not create an interface, factory, base class, or repository wrapper for every method. Use dependency inversion for actual external effects or replaceable policies, especially file storage, messaging, time, and randomness. Reuse the existing `FileStorage` and `ExternalMessageSender` boundaries when appropriate.

## Function and code contract

For each important use case, define actor, inputs, output DTO/view model, validation, authorized scope, side effects, failure outcome, and retry semantics. Prefer intent-revealing names such as `submitAttempt` and `recordPayment` over `processData` or `handleEverything`. Functions should perform one coherent operation; multiple lines are acceptable when they express a readable workflow.

Use named options for ambiguous argument groups and explicit units such as `durationMinutes`, `amount`, and `issuedAt`. Define DTO shapes with existing Java records and JavaScript JSDoc where it improves boundary clarity. Avoid changing an existing response wrapper globally as part of one page refactor. Keep enum mappings centralized, preserve unknown values visibly, and distinguish missing, zero, false, and empty values.

Use guard clauses for invalid states, immutable updates for React collections, and small pure transformations for derived values. Avoid nested ternaries that hide state transitions, oversized catch blocks, swallowed exceptions, and comments that simply restate code. Extract shared code after a real common responsibility emerges; do not build a universal CRUD engine around superficially similar pages.

## Frontend state and request discipline

Separate server state, editable drafts, derived values, and ephemeral UI state. Avoid copying filtered rows, totals, and base rows into competing state variables. A query key must include the relevant authenticated actor/tenant scope, resource ID, and applied filters. Clear private caches on logout or scope changes. Do not share authenticated data with public-page caches.

Use cancellation or a request-generation guard so stale results cannot overwrite the current selection. React effects should be repeat-safe, clean up subscriptions/timers, and never create records merely because a component mounted. Event handlers initiate mutations. Model nontrivial flows with explicit states or a reducer, such as draft/uploading/saving/succeeded/failed; do not scatter incompatible booleans across a long page.

Normalize transport failures into validation, unauthorized, forbidden, not-found, conflict, network, and unexpected states while retaining useful server details. Map the existing backend error `details` to fields where applicable. A failure must not become an empty success dataset. Preserve form drafts after failed writes. UI submission locking prevents repeated clicks; server-side concurrency and idempotency remain separate requirements.

## Authorization and tenant isolation

Authenticated writes derive the actor and tenant from trusted server context, not editable request fields. Scope every referenced student, course, invoice, lesson, file, and certificate lookup to the tenant and appropriate resource access. Roles alone do not establish that a parent owns a child record or a teacher owns a course. Verify those relationships in the use-case path, including direct ID requests.

Public routes resolve an allowed institution context explicitly and return a narrow public DTO. Do not reuse a private entity serializer. Keep `TenantContext` request cleanup intact; asynchronous tasks and domain events need explicit tenant/actor data rather than assuming a ThreadLocal survives execution elsewhere. Logs must not contain passwords, bearer tokens, raw answers, guardian contacts, or entire payloads.

## Transactions, retries, and domain events

Place each atomic database command in an application-service transaction. Public registration currently performs several repository writes in the controller: the target is a registration service that validates first and atomically creates the student, guardian link, and optional enrollment. Prevent code/serial collisions using a database-enforced uniqueness rule and a concurrency-safe generator; count-plus-one is not sufficient under concurrent requests.

For payment recording, attempt submission, certificate issuance, and registration, define retry behavior explicitly. When durable idempotency is needed, scope its key by actor/tenant and operation, bind it to a payload fingerprint, and persist the outcome atomically. Replaying the same request returns the same result; reusing a key for a different command produces a conflict. Do not claim idempotency until the backend implements it. Concurrent updates need a strategy supported by the deployed database, verified with an integration scenario rather than assumed from UI locking.

File upload and database save are separate effects. Track committed stages and retain successful file keys; retries should not duplicate completed records. Define cleanup or reconciliation for unused uploads. Do not hold a database transaction open while waiting for a user or a remote messaging provider.

Preserve the existing score ordering in `backend/src/main/java/com/manarah/integration/ScoreListener.java`: gradebook update, then student metrics, then risk assessment. The listener runs after the originating transaction with a new transaction; do not describe both transactions as one atomic commit. If reliable replay becomes necessary, implement an outbox or reconciliation mechanism with idempotent consumers. Never publish the same scoring side effect twice from both the use case and UI.

## Data precision, query behavior, and performance

Keep monetary arithmetic in backend decimal types such as `BigDecimal`, with explicit currency and rounding policy; browser formatting is not financial authority. Grade calculation belongs on the server; frontend validation improves usability only. Store and compare instants deliberately, preserve date-only deadlines as date-only where contracted, and inject a clock into rules that depend on time.

Validate and bound pagination; allowlist sort fields and use a stable tie-breaker. Apply filtering before pagination and use the same predicate for counts and rows. Confirm actual controller support before adding new query parameters. Replace per-row repository calls with scoped bulk queries/projections when measured or evident query growth justifies it. Keep public payloads bounded, use existing lazy loading for heavy 3D/export code, and avoid adding caches without a clear invalidation owner.

## Verification and delivery

Prioritize behavioral evidence for changed boundaries: pure validation/calculation tests for meaningful rules; service tests for allowed transitions; database integration tests for tenant scoping, rollback, concurrency, and uniqueness; component or route checks for stale requests, recoverable forms, and keyboard use. Assert public outcomes rather than private method calls or exact internal hook structure.

The inspected frontend package exposes `dev`, `build`, and `preview`, with no test/lint script; the inspected backend has Spring test dependencies but no `src/test` tree. Recheck before implementation. Add a minimal appropriate test harness only when behavioral changes need it; do not claim nonexistent scripts ran or a Maven run with zero tests proves correctness. For backend changes, use the configured Java/Maven environment, including `.tooling/paths.json` when applicable, and run the relevant tests. For frontend changes, build and verify the affected route; use real test assertions for business-critical interactions when a harness exists or is added.

For documentation-only work, validate skill metadata, links, and coverage. For implementation, report which use cases work, what was verified, and any unsupported extension that remains. A complete slice connects the UI action, validation, authorized use case, persistence, error recovery, and visible result. Preserve unrelated changes in this shared workspace.
