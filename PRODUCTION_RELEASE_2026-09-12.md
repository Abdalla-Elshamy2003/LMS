# Production hardening release — 12 September 2026

Live site: [Manarah](https://frontend-production-a64a1.up.railway.app).

## Release decision

**Fixes deployed and verified; unrestricted production launch is NOT approved yet.** The known demo administrator password still works. Credential rotation awaits the owner's answer. Backups could not be enabled with the connected Railway credentials. Payment and outbound email are not configured as live services. Passing the checks below is not a full penetration test or a security guarantee.

This report supersedes the failed-case status in `PRODUCTION_QA_REPORT_2026-09-12.md`, which remains the historical pre-fix record.

## Deployed

- Backend deployment: `c931daea-30de-4570-bc04-34c1ecb4621b` — SUCCESS.
- Frontend deployment: `21bf903d-8d5a-4c0f-b2d8-a20c6b4b880d` — SUCCESS.
- Public asset verified: `/assets/index-Cd2FwELp.js`.
- Existing database remained in place; deployment logs confirm no migration was necessary.

## Fixes

1. Students can list published exams in their enrolled courses, without question-bank access or answer keys. The UI now displays loading failures with retry instead of remaining incomplete.
2. Exam timers use the original server start time. Automatic submission uses current answers, guards duplicate requests and preserves retry after failure. Multiple-answer questions support multiple selections.
3. Root administrators see the full academy course library (57 courses). Course links carry explicit academy scope; entering a course validates the academy against the administrator's permitted list. Existing teacher/student cross-tenant restrictions remain in place.
4. The return-to-root button performs an actual navigation and clears the selected academy.
5. Sensitive academy/student-access and password changes create audit events without storing passwords in audit details. Coverage is targeted, not a claim that every mutation is audited.
6. Sandbox payment confirmation is disabled by default, independently of whether a real provider is configured. Paid checkout cannot silently fall back to sandbox. Free enrollments and authorized staff enrollment remain distinct workflows.
7. Unknown authenticated API paths return 404 and malformed requests return 400 instead of a generic 500.
8. Updated frontend dependencies, including Axios, jsPDF, React Router, Vite and PostCSS. Runtime image updated to nginx 1.30.4 and build image to Node 22. Version reference: [official nginx releases](https://nginx.org/en/download.html). jsPDF security context: [official releases](https://github.com/parallax/jsPDF/releases).

## Verification performed

| Check | Result |
|---|---|
| Local Spring/SQLite integration tests | 18 passed, 0 failures, 0 errors |
| Five-academy live API checks | 20/20 passed |
| Live security/role/file probes | 20/20 passed; former student-exams 403 is now 200 |
| Additional live release checks | 5/5 passed: root counts, scoped course metadata, deadline persistence, audit event, unknown-route 404 |
| Frontend production build | Passed; large-bundle warning remains |
| Final npm audit | 0 reported vulnerabilities; this is dependency-advisory coverage, not whole-system assurance |
| PDF library smoke test | PNG embedded and valid PDF produced, 429288 bytes; browser download/layout not revalidated |
| Student manual exam flow | Published QA exam visible, answer selected, submitted, score 10/10 shown |
| Admin return-to-root | Browser reached `/app/academy`; scoped-return button disappeared |
| Admin academy course navigation | Clicked `/app/courses/53?academy=2` from the root library; full navigation selected academy 2, opened course 53 and displayed lesson materials and two learners without a 403/404 |

The final test suite adds exam metadata/answer-key/duplicate-submit regressions, sandbox refusal, safe client errors, cross-academy library checks and audit evidence. Existing role/isolation tests continue to pass.

The additional QA homework named `QA release regression — deadline 2026-09-20` is technical test data. Its deadline round-tripped correctly. The earlier manual date-entry issue was not reproduced as a backend defect. QA student access was saved with the same course assignment to verify auditing; no intended enrollment was removed. Existing five teachers, five QA courses and ten QA students remain explicitly test data.

## Remaining launch blockers and limitations

- **Critical: legacy administrator/demo credentials.** The administrator still accepts the known demo password. Disabling the seeder does not rotate existing accounts. Owner password rotation is awaiting approval; the other legacy accounts also require review before admitting real users. Secrets must not be stored in release reports or deployment contexts.
- **Backups: blocked by Railway authorization.** The backend volume is 500 MB, about 50.5 MB used, mounted at `/app/data`. Backup and schedule lists are empty. `volumeInstanceBackupCreate` returned `Not Authorized`; a follow-up read confirmed neither a backup nor schedule was created. SSH fallback could not proceed because no SSH identity is configured. No restore, deletion or data replacement was attempted. Owner must enable backup access/plan or provide an authorized backup route. An isolated restore drill is still required. [Railway backup documentation](https://docs.railway.com/volumes/backups) describes volume/SQLite coverage, scheduling and incremental storage charges.
- **Real payments and email:** no provider activation or actual payment/email delivery was verified. Keep paid self-service checkout unavailable until gateway configuration and signed webhook testing are completed. Password-reset email needs a real configured sender and delivery test.
- **Private educational videos:** QA uses a public technical sample, not protected video hosting. Authenticated local file access was tested; external public video URLs are not DRM or copy protection. Replace QA material with approved educational content and an appropriate private media setup before selling it.
- **Operational/performance:** no realistic load/stress test, HA/failover, browser/device matrix, hardware-reader integration or full accessibility audit was performed. Bundles still trigger a size warning. Railway-level deployment healthcheck metadata is unset; container healthchecks exist but are not equivalent to a verified Railway readiness gate.
- **Other workflows:** the question bank still lacks a manual question creation form; test questions were authored through the API. Live AI generation is not configured. Parent workflows were covered locally, not manually on production in this release.

## Reproduce

Run backend Maven tests with the JDK/Maven paths in `.tooling/paths.json`; run `npm run build` and `npm audit` in `frontend`.

Live scripts: `scripts/production-qa.ps1 -VerifyOnly`, `scripts/production-security-probes.ps1`, and `scripts/production-release-checks.ps1`. Supply administrator credentials securely when requested. QA credentials remain only in `.tooling/production-qa/accounts.json`, outside both service upload directories.
