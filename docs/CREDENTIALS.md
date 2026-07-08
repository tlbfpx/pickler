# Credentials Management

> Covers first deployment, upgrade path, key rotation, and emergency response
> for Hey Pickler backend (`hey-pickler-server`). Read this before deploying
> to any environment outside local dev.

## 1. First Deployment

### 1.1 Generate secrets

Run these commands locally and copy outputs into your `.env` (template: `.env.example`):

```bash
# Database password (24 base64 chars ≈ 144 bits entropy)
openssl rand -base64 24

# Redis password
openssl rand -base64 24

# JWT signing secret (48 base64 chars ≈ 288 bits; must be ≥ 32 chars)
openssl rand -base64 48

# AES key (use 32 ASCII chars to be safe; AesUtil enforces exactly 16/24/32 bytes)
# Simplest: type out 32 random letters+numbers manually, OR:
openssl rand -base64 24 | tr -d '/+=' | head -c 32

# Initial admin one-time password (≥ 12 chars)
openssl rand -base64 18
```

### 1.2 Set env vars in deployment order

Set variables in this order so each layer's health check can pass:

1. **Database** — `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Verify connection.
2. **Redis** — `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`. Verify ping.
3. **Crypto** — `JWT_SECRET`, `AES_KEY`. Cannot be changed without data loss / token invalidation (see §3).
4. **Initial admin** — `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`. Only required on first start; safe to unset after.
5. **WeChat** — `WX_APPID`, `WX_SECRET`, `WX_DEV_MODE=false`.
6. **CORS & profile** — `CORS_ADMIN_ORIGINS`, `SPRING_PROFILES_ACTIVE=prod`, `PROD_GUARD=true`.

### 1.3 Start the app

```bash
java -jar hey-pickler-server-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/etc/hey-pickler/application.properties
```

Or via Docker / systemd unit that exports env vars from a secrets manager.

Expected log lines on successful first start:
- `Migrating schema "hey_pickler" to version "8"` (Flyway)
- `Initial admin 'admin' created from env vars (id=1, role=SUPER_ADMIN)` (AdminBootstrapper)
- `Profile guard passed (prod profile, secrets verified unique)` (ProfileGuard)

### 1.4 First login & password change

Login at `/login` with `INITIAL_ADMIN_USERNAME` / `INITIAL_ADMIN_PASSWORD`. Immediately
navigate to `/admins` → own user → change password. The bootstrap password is now stale
but still in `.env` (consider rotating or zeroing after change).

### 1.5 CI CVE scanning (NVD_API_KEY)

`NVD_API_KEY` is a **GitHub Actions secret** (not a runtime env var) that enables
OWASP dependency-check in CI. Since 2024 the NVD rejects anonymous API requests
with HTTP 403, so without a key the CVE scan is skipped and the dependency CVE
posture is **unknown** — `cve-gate.sh` then has no report to inspect and passes
by default. Do not go live in this state.

Apply (free, approval usually within a day): https://nvd.nist.gov/developers/request-an-api-key

Add as a repo secret named `NVD_API_KEY` → Settings → Secrets and variables →
Actions. Once set, CI runs `dependency-check` on every build and
`scripts/cve-gate.sh` hard-blocks the build on any CRITICAL (CVSS≥9) finding
while leaving HIGH (7.0–8.9) advisory in `target/dependency-check-report.html`.

**Pre-production checklist**: confirm at least one CI run with `NVD_API_KEY` set
produces `target/dependency-check-report.html` + `.json` and prints
"CVE gate passed — no CRITICAL vulnerabilities".

---

## 2. Upgrading from pre-hardening version

If you have an existing dev / staging environment that ran a version with the V2
admin seed (`admin` / `admin123` baked into Flyway):

### 2.1 Update Flyway checksum

The V2 migration content changed when admin seed was removed — your dev DB has
the old checksum and the new code will refuse to start with:

```
Migration checksum mismatch for migration version 2
-> Applied to database : -618398713
-> Resolved locally    : 174909195
```

Run this one-time SQL to update the stored checksum to match the new file:

```sql
UPDATE flyway_schema_history SET checksum = 174909195 WHERE version = '2';
```

(Alternative: install `flyway-maven-plugin` and run `mvn flyway:repair`. Either works.)

### 2.2 Existing admin users are preserved

`AdminBootstrapper` checks `SELECT COUNT(*) FROM admin_user` and skips if > 0.
Your existing admin row (whatever password you set it to) stays intact. You can
keep using the old password; no env vars are required for ongoing operation.

### 2.3 If you previously used the `PicklerDevAesKey16` fallback

The old fallback `PicklerDevAesKey16` was actually 18 bytes (not 16) and was
silently zero-padded. The new strict validation rejects it. The new dev fallback
is `PicklerDevAesKey` (exactly 16 bytes).

**Impact on existing data**: any `user.phone` value encrypted with the old key
cannot be decrypted with the new key. To recover:

```sql
-- Verify how many rows are affected
SELECT COUNT(*) FROM user WHERE phone IS NOT NULL AND phone != '';

-- Wipe encrypted phones; users will need to rebind via WeChat auth flow
UPDATE user SET phone = NULL;
```

This is a dev-only concern — there is no production data to lose.

### 2.4 Set .env for the dev environment

Even dev now requires these env vars set (no V2 seed means no default admin):

```bash
# .env (gitignored)
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=devpassword123     # pick whatever you'll remember
JWT_SECRET=HeyPickler2026DevSecretK3y!MustChangeInProd!!
AES_KEY=PicklerDevAesKey
```

After first start with the new code, `AdminBootstrapper` sees the legacy admin
row and skips — so `INITIAL_ADMIN_PASSWORD` is only used when starting against
a fresh DB.

---

## 3. Key Rotation

### 3.1 JWT_SECRET rotation

**Impact**: all existing admin and user sessions become invalid. Every logged-in
admin gets 401 on next request. Every WeChat user with a cached token gets 401
until they trigger the refresh flow (which calls `/api/app/auth/refresh` — also
fails until they re-login via WeChat code).

**Procedure**:

1. Generate new secret: `openssl rand -base64 48`
2. Schedule maintenance window (admins will be kicked out)
3. Update `JWT_SECRET` in your secrets manager / `.env`
4. Restart the app — `ProfileGuard` verifies the new secret is not the dev fallback
5. Notify admins to re-login
6. Monitor for spike in 401 errors over the next 15 minutes (token TTL window)

**Frequency**: rotate annually, or immediately upon suspected leak.

### 3.2 AES_KEY rotation

**Impact**: all `user.phone` values become undecryptable. Read paths that return
phone numbers will fail (or return null depending on error handling).

**Procedure**:

1. Generate new key: `openssl rand -base64 32 | tr -d '/+=' | head -c 32`
2. **Before rotation**, write a one-shot re-encryption script:
   ```sql
   -- For each row with phone != NULL:
   -- 1. Decrypt with OLD key (run via temporary instance with old AES_KEY)
   -- 2. Re-encrypt with NEW key
   -- 3. UPDATE row
   ```
3. Take a database backup
4. Run re-encryption script (typically 1-5 minutes for ≤ 1M rows)
5. Update `AES_KEY` in secrets manager
6. Restart app
7. Verify a sample of phone numbers decrypt correctly via admin UI

**Frequency**: rotate only upon suspected leak. There is no scheduled rotation
recommendation — the operational cost is high.

### 3.3 INITIAL_ADMIN_PASSWORD rotation

Not a key per se, but worth noting: after first deployment, this value can be
removed from `.env`. To reset a forgotten admin password, use the admin UI
(another admin changes it) or direct DB update with a freshly-BCrypt-hashed value.

---

## 4. Emergency Response

### 4.1 Suspected JWT leak

Symptoms: unknown tokens appearing in logs, admin actions you didn't perform,
suspicious login locations.

**Response**:

1. Rotate `JWT_SECRET` immediately (see §3.1) — this invalidates ALL tokens including the attacker's
2. Check `operation_log` table for suspicious admin actions in the past 7 days:
   ```sql
   SELECT * FROM operation_log
   WHERE created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
   ORDER BY created_at DESC;
   ```
3. Check `admin_user` for any new accounts you didn't create:
   ```sql
   SELECT * FROM admin_user WHERE created_at > DATE_SUB(NOW(), INTERVAL 30 DAY);
   ```
4. Force all admins to change passwords
5. Document the incident: timeline, suspected entry point, remediation

### 4.2 Suspected AES_KEY leak

Symptoms: phone numbers appearing in unexpected places, database dump leaked.

**Response**:

1. Treat all PII as compromised — notify affected users per applicable regulations
2. Rotate `AES_KEY` (see §3.2) — old ciphertexts remain decryptable by attacker; new data is safe
3. Audit access logs for any bulk reads of `user.phone`
4. Consider whether DB itself is the leak vector (rotate DB credentials too)

### 4.3 Suspected DB credential leak

Symptoms: unexpected connections from non-app IPs in MySQL slow log, dump file
found outside its expected location.

**Response**:

1. Rotate DB password: `openssl rand -base64 24`
2. Update DB user password in MySQL: `ALTER USER 'hey_pickler_app'@'%' IDENTIFIED BY 'new-password';`
3. Update `DB_PASSWORD` in secrets manager
4. Restart app (will fail briefly between password change and app restart — plan for 30s downtime)
5. Audit binlog / general log for suspicious queries

### 4.4 Admin account lockout

If a single admin account is compromised but the leak is contained to credentials
(not the underlying secrets):

1. `UPDATE admin_user SET status = 'DISABLED' WHERE username = '<affected>';`
2. Invalidate their JWT by waiting for TTL expiry (no built-in blacklist — relies on JWT_SECRET rotation for global invalidation)
3. Investigate how the credential leaked (phishing, password reuse, etc)
4. Re-enable only after password reset and security review

---

## 5. References

- `.env.example` — template with all required variables
- `openspec/changes/secure-credentials-parameterization/proposal.md` — design rationale
- `application-prod.yml` — production profile (no hardcoded fallbacks)
- `config/AdminBootstrapper.java` — initial admin bootstrap logic
- `config/ProfileGuard.java` — startup-time security check
- `common/util/AesUtil.java` — AES key validation
