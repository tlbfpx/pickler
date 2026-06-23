package com.heypickler.integration;

import com.heypickler.common.util.JwtUtil;
import com.heypickler.service.ImageUrlValidator;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationTestConfig {

    /**
     * Banner 图片 URL 校验器在 HeadBasedImageUrlValidator 里做真实 HTTP HEAD 请求，
     * 集成测试不应依赖外网。validator 自身的语义在 ImageUrlValidatorTest 里用
     * in-process HttpServer 单测覆盖；这里替换为 no-op，让 banner CRUD 集成测试
     * 专注于业务逻辑而非网络可达性。
     */
    @MockBean
    protected ImageUrlValidator imageUrlValidator;

    /** Test-only admin credentials. Self-seeded in {@link #seedTestAdmin} so the
     *  integration suite does not depend on the legacy V2 admin/admin123 row
     *  (which has been removed from Flyway). Production deployments use
     *  AdminBootstrapper with INITIAL_ADMIN_PASSWORD env var instead. */
    protected static final String TEST_ADMIN_USERNAME = "admin";
    protected static final String TEST_ADMIN_PASSWORD = "integration-test-pw-12";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 测试用 user id（8001/9000/9001）在 dev 库可能不存在，AppAuthFilter
     * 遇到 null user 会返回 USER_BANNED(1002)。这里幂等插入占位用户。
     */
    @BeforeAll
    static void ensureTestUsers(@Autowired JdbcTemplate jdbcTemplate) {
        for (long userId : new long[]{8001L, 9000L, 9001L}) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                            "VALUES (?, ?, ?, 'NORMAL', 0, 0, 'BRONZE', 'BRONZE')",
                    userId, "test_openid_" + userId, "TestUser_" + userId);
        }
    }

    /**
     * Reset the admin row to a known password. Idempotent — runs before every
     * test class. This decouples integration tests from whatever admin password
     * happens to live in the dev DB (legacy admin123, post-bootstrap random, etc).
     */
    @BeforeAll
    static void seedTestAdmin(@Autowired JdbcTemplate jdbcTemplate) {
        String bcryptHash = BCrypt.hashpw(TEST_ADMIN_PASSWORD, BCrypt.gensalt());
        jdbcTemplate.update("DELETE FROM admin_user WHERE username = ?", TEST_ADMIN_USERNAME);
        jdbcTemplate.update(
                "INSERT INTO admin_user (username, password_hash, role, status) VALUES (?, ?, 'SUPER_ADMIN', 'ACTIVE')",
                TEST_ADMIN_USERNAME, bcryptHash);
    }


    @LocalServerPort
    protected int port;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Login as the default super admin and return the Bearer token.
     * This ensures the Redis session is created so subsequent admin requests pass the filter.
     */
    protected String loginAsSuperAdmin() {
        Map<String, String> body = Map.of("username", TEST_ADMIN_USERNAME, "password", TEST_ADMIN_PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/admin/auth/login", req, Map.class);
        Map result = resp.getBody();
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        return (String) data.get("token");
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpHeaders adminAuthHeaders() {
        return authHeaders(loginAsSuperAdmin());
    }

    protected HttpHeaders appAuthHeaders(Long userId) {
        return authHeaders(jwtUtil.generateAppToken(userId));
    }

    protected int resultCode(ResponseEntity<Map> resp) {
        return (int) resp.getBody().get("code");
    }

    @SuppressWarnings("unchecked")
    protected <T> T resultData(ResponseEntity<Map> resp) {
        return (T) resp.getBody().get("data");
    }
}
