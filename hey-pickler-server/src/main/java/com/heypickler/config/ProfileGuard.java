package com.heypickler.config;

import com.heypickler.common.util.ExitAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Defense-in-depth startup check that refuses to start the application when a
 * production context is detected with a known-insecure configuration.
 *
 * <p>"Production context" is defined as either:
 * <ul>
 *   <li>{@code spring.profiles.active} contains {@code prod}, or</li>
 *   <li>{@code PROD_GUARD=true} env var (catches the case where operators
 *       forget to switch the profile)</li>
 * </ul>
 *
 * <p>Fail-fast cases (exit code 2):
 * <ul>
 *   <li>prod profile + {@code JWT_SECRET} or {@code AES_KEY} matches known dev fallback</li>
 *   <li>{@code PROD_GUARD=true} + active {@code dev} profile (likely misconfigured prod env)</li>
 * </ul>
 *
 * <p>Runs on {@link ApplicationReadyEvent} — after all beans are initialized
 * (including {@code AesUtil}'s own length check, which catches shorter keys
 * earlier). Centralizing the dev-secret check here keeps the policy visible
 * in one place and easy to audit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileGuard implements ApplicationListener<ApplicationReadyEvent> {

    // Known dev-only fallback values defined in application-dev.yml. MUST NOT appear in prod.
    private static final Set<String> KNOWN_DEV_JWT_SECRETS = Set.of(
        "HeyPickler2026DevSecretK3y!MustChangeInProd!!"
    );
    private static final Set<String> KNOWN_DEV_AES_KEYS = Set.of(
        "PicklerDevAesKey"
    );

    private final Environment env;
    private final ExitAction exitAction;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Set<String> activeProfiles = Set.copyOf(Arrays.asList(env.getActiveProfiles()));
        boolean isProd = activeProfiles.contains("prod");
        boolean isDev = activeProfiles.contains("dev");
        boolean prodGuard = "true".equalsIgnoreCase(env.getProperty("PROD_GUARD"));

        if (prodGuard && isDev) {
            failFast("PROD_GUARD=true but spring.profiles.active contains 'dev'. " +
                     "This looks like a production environment running with the dev profile — " +
                     "likely a misconfiguration. Set SPRING_PROFILES_ACTIVE=prod and provide " +
                     "unique secrets, or unset PROD_GUARD if this is genuinely a dev box.");
            return;
        }

        if (!isProd) {
            log.info("Profile guard passed (non-prod profile {}, skipped strict checks)", activeProfiles);
            return;
        }

        // WX_DEV_MODE：dev 模式下 /api/app/auth/login 用任意 code 即可伪造任意 userId（无微信校验），
        // 绝不可在 prod 开启。env 层（compose/镜像/configmap）可能继承 dev 兜底 true，此处兜底拦截。
        String devMode = env.getProperty("hey-pickler.wechat.dev-mode");
        if ("true".equalsIgnoreCase(devMode)) {
            failFast("Active profile is 'prod' but hey-pickler.wechat.dev-mode=true. Dev mode bypasses " +
                     "WeChat auth and lets anyone forge any userId via /api/app/auth/login with an arbitrary code. " +
                     "Set WX_DEV_MODE=false in production.");
            return;
        }

        // CORS admin origins：prod 不允许通配 * 或 localhost（防误配导致跨站读取已认证 admin API）
        String adminOrigins = env.getProperty("hey-pickler.cors.admin-origins");
        if (adminOrigins != null && (adminOrigins.contains("*") || adminOrigins.contains("localhost"))) {
            failFast("Active profile is 'prod' but hey-pickler.cors.admin-origins contains '*' or 'localhost' "
                     + "('..." + adminOrigins + "'). Set CORS_ADMIN_ORIGINS to the real admin panel origin(s) in production.");
            return;
        }

        String jwt = env.getProperty("hey-pickler.jwt.secret");
        if (KNOWN_DEV_JWT_SECRETS.contains(jwt)) {
            failFast("Active profile is 'prod' but JWT_SECRET matches the known dev fallback. " +
                     "Generate a unique JWT_SECRET via `openssl rand -base64 48` and set it as an env var.");
            return;
        }

        String aes = env.getProperty("hey-pickler.aes.key");
        if (KNOWN_DEV_AES_KEYS.contains(aes)) {
            failFast("Active profile is 'prod' but AES_KEY matches the known dev fallback. " +
                     "Generate a unique AES_KEY (exactly 16, 24, or 32 bytes) via `openssl rand -base64 32`.");
            return;
        }

        log.info("Profile guard passed (prod profile, secrets verified unique)");
    }

    private void failFast(String reason) {
        log.error("""

            ╔══════════════════════════════════════════════════════════════╗
            ║ FATAL: Profile guard violation.                            ║
            ║                                                            ║
            ║ {}                                       ║
            ║                                                            ║
            ║ Refusing to start in this state. Fix the configuration     ║
            ║ and restart. See docs/CREDENTIALS.md for guidance.         ║
            ╚══════════════════════════════════════════════════════════════╝
            """, reason);
        exitAction.exit(2);
    }
}
