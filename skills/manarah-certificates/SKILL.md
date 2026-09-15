---
name: manarah-certificates
description: Refine /app/certificates for staff issuance, student and parent certificate libraries, precise certificate previews, and reliable PDF download feedback.
---

# Certificates

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadCertificatesForViewer(actor, selectedStudentId, signal) | Feature orchestration and scoped query service | Resolve student/linked children/staff scope and reject stale selected-student results. |
| validateIssueCommand(draft) | Pure form model | Validate selected student, optional course, and supported grade values for a clear review summary. |
| issueCertificate(command, actor) | Certificate application service | Authorize issuance, verify tenant/resource references, persist unique serial/verification identity, and define duplicate issuance policy. |
| buildCertificateView(dto, institution) | Pure print model | Map only verified fields into the readable preview and export. |
| exportCertificate(snapshot) | PDF/QR adapter | Wait for fonts and QR assets, capture the correct certificate, and return a download outcome with recoverable failure. |

**Architecture boundary.** Separate immutable certificate data, issuance draft, and export status. Do not regenerate serials or verification codes during rendering. Storage uniqueness and randomness belong on the server; the browser only encodes the received verification URL.

**Behavioral verification.** Verify concurrent issuance policy, cross-tenant student/course IDs, parent-child scoping, selected-student races, missing QR assets, and exported serial/QR consistency.


Read [the shared design system](../manarah-design-system/SKILL.md). Sources: `frontend/src/pages/Certificates.jsx` and `frontend/src/components/CertificateCard.jsx`; route: `/app/certificates`. Inspect the certificate controller and service before changing issuance behavior. Current operations include `/api/certificates/student/{id}` and `/api/certificates/issue`.

## Role-specific layout

For staff, use a searchable student rail beside the selected student's certificate library. Show the student's name/code above the library and keep Issue certificate visible only for authorized issuers. The current issuer roles are SUPER_ADMIN, BRANCH_ADMIN, and ACADEMIC_MANAGER. Search uses the loaded `/api/students/all` list; preserve honest list scope if pagination is introduced.

For students, lead with their own certificates resolved through `/api/students/me`. For parents, group certificates by the linked child or provide a clear child selector. Keep loading/error state per child; a pending child request must not appear as No certificates. Clear stale selected-student content and ignore stale requests when staff switch students quickly.

## Certificate library and issuance form

Use a compact record summary around each printable certificate: student, course if any, grade, issue date, serial, download, and verification link. Name/course/serial search and date sorting can operate over the complete returned student list. For a dense staff library, offer these fields in a table with preview disclosure.

The issuance form needs a fixed student identity, optional course with an explicit General certificate choice, and supported grade options. Include a faithful summary before issuing. Do not imply verified course completion or accreditation solely because a staff member selected a grade. Preserve form selections on failure and prevent duplicate issuance after ambiguous responses.

## Visual treatment, export, and motion

Use dignified typography, restrained deep-brand and warm accents, a readable serial, and a stationary verification QR. Keep the printable certificate composition separate from animated interface controls. Avoid clipping long Arabic names within a fixed aspect ratio; use a responsive preview and a deliberate export layout.

Wait for fonts and QR images before PDF capture through the existing PDF helper. Disable duplicate downloads, show capture errors, and ensure export excludes buttons. Verify the generated artifact visually for Arabic shaping, clipping, and QR readability during implementation. Do not imply the DOM-rasterized PDF contains selectable text.

- Students see only their authorized certificates; parents retain child context.
- Rapid staff selection cannot show one student's certificates under another student's name.
- Empty data, fetch failure, issuance failure, and export failure remain distinct.
- Long-name exports preserve all fields and the QR opens the intended verification route.
