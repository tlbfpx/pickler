# infrastructure Specification Delta

## ADDED Requirements

### Requirement: Deployment artifacts for Alibaba Cloud ECS
The system SHALL provide deployment artifacts (systemd unit, Nginx reference config, install script) under `deploy/` directory targeting Alibaba Cloud ECS running Ubuntu 22.04+ / Alibaba Cloud Linux 3, enabling the customer operations team to perform first-time deployment without developer assistance.

#### Scenario: First-time deployment via install.sh
- **WHEN** the customer operations team runs `sudo bash deploy/scripts/install.sh` on a clean ECS instance with JDK 17+, MySQL 8, Redis 6, and Nginx pre-installed
- **THEN** the script SHALL create the `heypickler` system user, set up `/opt/heypickler`, `/var/log/heypickler`, `/etc/heypickler` directories with correct ownership, install the systemd unit, write the env file template at `/etc/heypickler/heypickler.env` with `0600` permissions, install logrotate config, and print next-step instructions
- **AND** the script SHALL NOT start the service (requires manual env file edit)
- **AND** the script SHALL be idempotent: re-running it SHALL NOT overwrite an existing `/etc/heypickler/heypickler.env` or recreate an existing user

#### Scenario: systemd unit restart behavior
- **WHEN** the `hey-pickler` service crashes with a non-zero exit code
- **THEN** systemd SHALL restart the service after `RestartSec=10` seconds
- **AND** after 3 restart attempts within 60 seconds (`StartLimitBurst=3`, `StartLimitInterval=60`), systemd SHALL stop restarting and require operator intervention

#### Scenario: Environment variable injection via EnvironmentFile
- **WHEN** the systemd unit starts
- **THEN** all required environment variables (JWT_SECRET, AES_KEY, INITIAL_ADMIN_*, PROD_GUARD, DB_*, REDIS_*, WX_*, CORS_ADMIN_ORIGINS, SPRING_PROFILES_ACTIVE) SHALL be loaded from `/etc/heypickler/heypickler.env`
- **AND** the env file SHALL be owned by `heypickler:heypickler` with mode `0600` to prevent secret leakage

### Requirement: Reverse proxy reference configuration
The system SHALL provide a reference Nginx configuration at `deploy/nginx/heypickler.conf` covering both admin panel (`admin.heypickler.com`) and WeChat mini-program API (`api.heypickler.com`) virtual hosts, with production-grade TLS, security headers, and proxy settings.

#### Scenario: TLS hardening
- **WHEN** the Nginx configuration is deployed to `/etc/nginx/sites-available/`
- **THEN** it SHALL only enable TLS 1.2 and 1.3 (`ssl_protocols TLSv1.2 TLSv1.3`)
- **AND** it SHALL set HSTS header with `max-age=31536000; includeSubDomains`
- **AND** it SHALL add `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` headers to all responses including error responses (`always` modifier)

#### Scenario: File upload support
- **WHEN** the admin panel uploads a banner or event image
- **THEN** Nginx SHALL accept requests up to 10 megabytes (`client_max_body_size 10m`) without truncation
- **AND** SHALL proxy the request to the backend with `proxy_read_timeout 60s`

#### Scenario: Admin SPA routing
- **WHEN** a user navigates to `/events` or any admin panel route directly
- **THEN** Nginx SHALL serve `/var/www/hey-pickler-admin/dist/index.html` via `try_files $uri $uri/ /index.html` fallback
- **AND** SHALL proxy `/api/` requests to `http://127.0.0.1:8080`

### Requirement: Backup and recovery procedures
The system SHALL provide MySQL backup and recovery scripts under `deploy/scripts/` and document the full procedure (OSS bucket setup, cron configuration, restore drill) in `docs/RUNBOOK.md`, targeting Alibaba Cloud OSS as the backup backend.

#### Scenario: Daily automated backup
- **WHEN** the cron job `0 2 * * * heypickler /opt/heypickler/scripts/backup-mysql.sh` runs
- **THEN** the script SHALL produce a gzip-compressed mysqldump with `--single-transaction --master-data=2` (for InnoDB consistency and PITR support)
- **AND** SHALL upload to OSS bucket `${OSS_BUCKET}/mysql/` via `ossutil` with `--acl private`
- **AND** SHALL delete local backups older than 7 days (`RETAIN_LOCAL_DAYS=7`)
- **AND** OSS lifecycle policy SHALL be configured to retain objects for 30 days

#### Scenario: Backup script graceful degradation
- **WHEN** `ossutil` is not installed or OSS credentials are missing
- **THEN** the script SHALL still complete the local backup and log a warning
- **AND** SHALL NOT fail the cron job (exit 0)

#### Scenario: Point-in-time recovery
- **WHEN** operator runs `deploy/scripts/restore-mysql.sh <backup-file>` with a backup file from OSS
- **THEN** the script SHALL download the file from OSS, decompress, restore to a target database, and print the binlog coordinates embedded in the dump header for optional PITR

### Requirement: Operations runbook
The system SHALL provide `docs/RUNBOOK.md` covering 5 operational scenarios that enable customer operations team to manage the deployment without developer assistance: first-time deployment, daily operations, backup & recovery, rate-limit tuning, and common incident response.

#### Scenario: 30-minute first-time deployment
- **WHEN** a customer operator with no developer assistance follows `docs/RUNBOOK.md` §1 from a clean ECS instance
- **THEN** they SHALL be able to complete first-time deployment within 30 minutes
- **AND** the runbook SHALL include exact shell commands, expected output snippets, and troubleshooting for at least 3 common pitfalls (JDK version mismatch, MySQL connection refused, port 8080 in use)

#### Scenario: Incident response playbook
- **WHEN** operator encounters 5xx error spike / DB connection exhaustion / Redis failure / disk full / OOM kill / SSL cert expiry
- **THEN** `docs/RUNBOOK.md` §5 SHALL provide a step-by-step diagnosis checklist for each scenario
- **AND** each checklist SHALL include: log locations to inspect, diagnostic commands, remediation steps, escalation criteria

### Requirement: Deliverables acceptance checklist
The system SHALL provide `docs/DELIVERABLES.md` enumerating all artifacts the customer receives across 6 categories (source code, binary, deployment artifacts, documentation, test reports, database migrations), with acceptance criteria described as observable operator actions rather than file existence checks.

#### Scenario: Customer acceptance walkthrough
- **WHEN** the customer reviews `docs/DELIVERABLES.md` for acceptance
- **THEN** each category SHALL list specific artifacts (e.g., "git tag v1.0.0", "hey-pickler-server-1.0.0.jar")
- **AND** each artifact SHALL have an acceptance criterion (e.g., "customer can rebuild binary from tag in clean environment")
- **AND** each category SHALL have a step-by-step verification procedure

## ADDED Requirements (continued)

### Requirement: Deployment documentation references executable artifacts
The system SHALL maintain `docs/DEPLOYMENT-REQUIREMENTS.md` as a high-level deployment overview that references executable artifacts under `deploy/` rather than embedding inline shell commands, with operational procedures delegated to `docs/RUNBOOK.md`.

#### Scenario: Operator locates executable artifacts
- **WHEN** operator reads `docs/DEPLOYMENT-REQUIREMENTS.md` seeking deployment instructions
- **THEN** the doc SHALL link to `deploy/scripts/install.sh` for first-time deployment, `deploy/systemd/hey-pickler.service` for service management, `deploy/nginx/heypickler.conf` for reverse proxy setup, and `docs/RUNBOOK.md` for operational procedures
- **AND** SHALL NOT embed inline `java -jar` commands (those are now encapsulated in the systemd unit)
