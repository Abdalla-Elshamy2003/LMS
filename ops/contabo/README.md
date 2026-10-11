# Contabo production deployment

Production runs at https://droos.com.co on `213.199.38.162` in Compose project `lms-contabo`.
The existing server at `135.181.117.16` is independent and is not a deployment target.

## Branches

- `development`: push everyday changes here. GitHub runs backend tests, frontend build/audit, deployment-boundary tests, and the full local Compose smoke test.
- Open a pull request from `development` into `main`. Required checks must pass before merging; no second reviewer is required for the owner's own PR.
- `main`: a successful push/merge deploys the tested commit to Contabo. Manual CI runs on `main` can also deploy; development and PR runs do not deploy.

```bash
git switch development
git pull --ff-only origin development
# Edit, commit, then:
git push origin development
gh pr create --base main --head development
```

The backend test job uses isolated PostgreSQL 18. The Compose smoke test also verifies the repository's existing local PostgreSQL/Redis/MinIO setup without using production data or keys.

## Deployment configuration

GitHub environment `production` permits branch `main` only.

- Environment secret `CONTABO_SSH_KEY`: a dedicated Ed25519 deployment key, not the root password.
- Environment variables `CONTABO_HOST`, `CONTABO_PORT`, `CONTABO_USER`, `CONTABO_KNOWN_HOSTS`: the destination and pinned server host key.
- `/root/.ssh/authorized_keys`: the deployment key uses `restrict` and a forced command `/usr/bin/python3 /opt/lms/ops/deploy-contabo.py`. It accepts only `status` or `deploy <40-character SHA>`; it cannot open an arbitrary shell or forward ports.
- The installed deployment command is managed by the server administrator. Updating `ops/contabo/deploy.py` in Git alone does not replace that trusted server command.

No R2, AI, Fawaterak, database, or JWT credentials are stored in GitHub. They remain in `/opt/lms/shared/.env` with private permissions. No database or uploaded-file archive is shipped with source.

`compose.production.yml` is the source template. The deployment command replaces `__RELEASE_SHA__` in image tags and writes the runnable Compose file into each release. Use the generated `/opt/lms/current/compose.production.yml` for normal server operations.

## Release procedure

1. The runner archives the tested Git commit and sends it through the restricted SSH command. Older CI runs skip deployment when `main` has advanced.
2. The server acquires `/opt/lms/shared/deploy.lock`, validates/extracts the source, and builds backend/frontend images tagged and labelled with the full commit.
3. A private PostgreSQL dump is created under `/opt/lms/backups/` before application containers are replaced. PostgreSQL and Redis are not recreated by the deploy command.
4. Backend and frontend must become healthy. The command verifies public API access, protected-file denial, image revisions, unchanged PostgreSQL mounts and preserved R2/AI/payment/JWT settings.
5. `/opt/lms/current` switches to the verified release. `/opt/lms/shared/last-deployment.json` records its commit and backup. `/release.json` exposes the frontend commit with no caching; Actions checks it through the public HTTPS domain.

New persistent uploads use private R2 storage, with read compatibility for existing local files and database images. Existing shared files and Docker database volumes are retained.

## Student email codes

The student login page uses six-digit email codes when a sender is configured. Staff retain password sign-in. Registration (including course/package code checkout) verifies the submitted address before creating an account. Codes expire after ten minutes, allow at most five guesses, and cannot be reused. Resending invalidates the previous code and is limited to once per minute and five requests per address per hour.

Configure mail only in `/opt/lms/shared/.env`: set `MANARAH_EMAIL_ENABLED=true` and `MANARAH_EMAIL_FROM` to a provider-verified sender. Use either the existing `MANARAH_BREVO_API_KEY` integration or SMTP (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`). For [Resend SMTP](https://resend.com/docs/send-with-smtp), use `smtp.resend.com`, port `587`, username `resend`, and a private sending API key as the SMTP password. SMTP requires STARTTLS.

After testing real delivery to an owned inbox, set `MANARAH_EMAIL_REQUIRE_VERIFICATION=true` and recreate the backend with the current release's Compose file. This also prevents clients bypassing the registration email check. Leave verification disabled while mail is unconfigured so existing registration/password sign-in remains usable. `/api/auth/email-code/status` exposes readiness without credentials; successful provider acceptance alone does not prove inbox delivery.

If a build fails, the current application stays in place. If application health or verification fails after replacement, the previous application images are restored. Database migrations are not automatically undone; the pre-deploy dump is available for an administrator-reviewed restore if a schema change requires one. Failed releases can be retried with the same commit.

```bash
docker compose -f /opt/lms/current/compose.production.yml ps
cat /opt/lms/shared/last-deployment.json
curl -fsS https://droos.com.co/release.json
```

The former Railway deployment script remains as historical tooling; the GitHub workflow no longer invokes it.
