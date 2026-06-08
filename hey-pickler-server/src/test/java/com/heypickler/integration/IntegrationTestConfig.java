package com.heypickler.integration;

import com.heypickler.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class IntegrationTestConfig {

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
        Map<String, String> body = Map.of("username", "admin", "password", "admin123");
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
