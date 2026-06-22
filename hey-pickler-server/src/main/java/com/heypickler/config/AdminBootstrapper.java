package com.heypickler.config;

import com.heypickler.common.util.ExitAction;
import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the initial SUPER_ADMIN account from environment variables when
 * the {@code admin_user} table is empty. Replaces the previous Flyway-managed
 * admin seed (V2__init_data.sql) so credentials are never baked into source.
 *
 * <p>Env vars (resolved via Spring {@link Environment}, so JVM -D flags and
 * application.yml placeholders also work):
 * <ul>
 *   <li>{@code INITIAL_ADMIN_USERNAME} — defaults to "admin"</li>
 *   <li>{@code INITIAL_ADMIN_PASSWORD} — required on fresh DB; no default. Fail-fast exit(1) if missing.</li>
 * </ul>
 *
 * <p>Existing deployments are untouched: a non-empty {@code admin_user} table
 * causes bootstrap to skip silently.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapper implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final String ENV_USERNAME = "INITIAL_ADMIN_USERNAME";
    private static final String ENV_PASSWORD = "INITIAL_ADMIN_PASSWORD";

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final ExitAction exitAction;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        long count = adminUserMapper.selectCount(null);
        if (count > 0) {
            log.debug("admin_user table has {} rows, skipping bootstrap", count);
            return;
        }

        String username = resolve(ENV_USERNAME, "admin");
        String password = resolve(ENV_PASSWORD, null);

        if (password == null || password.isEmpty()) {
            log.error("""

                ╔══════════════════════════════════════════════════════════════╗
                ║ FATAL: No admin user exists and INITIAL_ADMIN_PASSWORD     ║
                ║        env var is not set. Cannot bootstrap.              ║
                ║                                                            ║
                ║ Set INITIAL_ADMIN_PASSWORD=<strong-password> and restart.   ║
                ║ See docs/CREDENTIALS.md for first-deployment guidance.     ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
            exitAction.exit(1);
            return; // defensive; unreachable in production wiring
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("INITIAL_ADMIN_PASSWORD is only {} chars; recommend at least {}",
                password.length(), MIN_PASSWORD_LENGTH);
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("SUPER_ADMIN");
        admin.setStatus("ACTIVE");
        adminUserMapper.insert(admin);
        log.info("Initial admin '{}' created from env vars (id={}, role=SUPER_ADMIN)",
            username, admin.getId());
    }

    private String resolve(String key, String fallback) {
        String v = environment.getProperty(key);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
