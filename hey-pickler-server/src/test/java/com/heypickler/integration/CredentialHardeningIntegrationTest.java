package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end wiring test for the credentials hardening change. Verifies that
 * the Spring context loads successfully with the integration profile, which
 * implicitly exercises:
 *
 * <ul>
 *   <li>{@code AesUtil} {@code @PostConstruct} validate() — would throw
 *       {@code IllegalStateException} and fail context load if AES_KEY length
 *       were invalid (16-byte {@code PicklerDevAesKey} in application-integration.yml).</li>
 *   <li>{@code AdminBootstrapper} {@code ApplicationRunner.run()} — runs at
 *       context startup. With non-empty {@code admin_user} table (seeded by
 *       {@link IntegrationTestConfig#seedTestAdmin}), it logs "skipping
 *       bootstrap" and returns without calling {@code ExitAction}.</li>
 *   <li>{@code ProfileGuard} {@code ApplicationListener<ApplicationReadyEvent>} —
 *       fires after context ready. With {@code integration} profile (neither
 *       {@code prod} nor {@code PROD_GUARD=true}), logs "skipped strict checks"
 *       and returns without calling {@code ExitAction}.</li>
 * </ul>
 *
 * <p>Fail-fast paths (invalid AES key length, missing INITIAL_ADMIN_PASSWORD on
 * fresh DB, prod profile with dev fallback secrets) are covered by unit tests
 * {@code AesUtilTest}, {@code AdminBootstrapperTest}, {@code ProfileGuardTest}
 * respectively. Those tests bypass Spring context for speed and direct
 * verification of {@code IllegalStateException} / {@code ExitAction.exit(int)}
 * calls.
 */
class CredentialHardeningIntegrationTest extends IntegrationTestConfig {

    @Autowired
    private Environment environment;

    @Test
    void springContextLoadsWithHardenedComponents() {
        // If this assertion runs, context loading succeeded — AesUtil @PostConstruct
        // passed, AdminBootstrapper ApplicationRunner ran, ProfileGuard
        // ApplicationListener fired (all without throwing or calling exitAction).
        assertNotNull(environment, "Spring Environment should be injected");

        // AesUtil's @Value("${hey-pickler.aes.key}") resolves to PicklerDevAesKey
        // (16 bytes) from application-integration.yml. If validation failed,
        // context load would have thrown IllegalStateException before reaching here.
        String aesKey = environment.getProperty("hey-pickler.aes.key");
        assertEquals(16, aesKey.getBytes().length,
            "AES_KEY in integration profile must be exactly 16 bytes (PicklerDevAesKey)");
    }

    @Test
    void adminRowExistsAfterBootstrapperRuns() {
        // AdminBootstrapper ran at context startup. If admin_user table was empty
        // and INITIAL_ADMIN_PASSWORD env var was set, bootstrap would have INSERT'd.
        // If admin_user had existing rows (the case for dev DB), bootstrap skipped.
        // Either way, table must be non-empty for subsequent integration tests.
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_user WHERE username = ?",
            Integer.class, TEST_ADMIN_USERNAME);
        assertNotNull(count);
        assertTrue(count > 0,
            "admin_user should have at least one row after bootstrap or seedTestAdmin; got " + count);
    }

    @Test
    void seedTestAdminPasswordWorksForLogin() {
        // Direct SQL verification that the seeded admin row accepts the test password.
        // This is the contract other integration tests rely on via loginAsSuperAdmin().
        String hash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM admin_user WHERE username = ?",
            String.class, TEST_ADMIN_USERNAME);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"),
            "Seeded admin password_hash should be BCrypt format ($2a$...), got: " + hash);
        assertTrue(org.springframework.security.crypto.bcrypt.BCrypt.checkpw(
                TEST_ADMIN_PASSWORD, hash),
            "Seeded admin row should accept TEST_ADMIN_PASSWORD");
    }
}
