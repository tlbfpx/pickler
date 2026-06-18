package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuthIntegrationTest extends IntegrationTestConfig {

    @Test
    void adminLogin_WithDefaultAdmin_ShouldReturnToken() {
        Map<String, String> body = Map.of(
                "username", TEST_ADMIN_USERNAME,
                "password", TEST_ADMIN_PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/admin/auth/login", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, resultCode(response));

        Map<String, Object> data = resultData(response);
        assertNotNull(data.get("token"));
        assertEquals("SUPER_ADMIN", data.get("role"));
    }

    @Test
    void adminLogin_WithWrongPassword_ShouldFail() {
        Map<String, String> body = Map.of("username", "admin", "password", "wrongpassword");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/admin/auth/login", request, Map.class);

        assertNotEquals(0, resultCode(response));
    }

    @Test
    void adminLogin_WithNonexistentUser_ShouldFail() {
        Map<String, String> body = Map.of("username", "nonexistent", "password", "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/admin/auth/login", request, Map.class);

        assertNotEquals(0, resultCode(response));
    }
}
