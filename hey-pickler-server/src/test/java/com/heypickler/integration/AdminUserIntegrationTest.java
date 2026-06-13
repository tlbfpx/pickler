package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminUserIntegrationTest extends IntegrationTestConfig {

    @Test
    void adminUserCrud_FullLifecycle() {
        HttpHeaders headers = adminAuthHeaders();

        // Create admin user
        Map<String, Object> createBody = Map.of(
                "username", "testadmin" + System.currentTimeMillis(),
                "password", "test123456",
                "role", "ADMIN"
        );
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(createBody, headers);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                "/api/admin/admin-users", createReq, Map.class);

        assertEquals(0, resultCode(createResp));
        Map<String, Object> createData = resultData(createResp);
        Long newAdminId = ((Number) createData.get("id")).longValue();
        assertNotNull(newAdminId);

        // List admin users
        HttpEntity<Void> listReq = new HttpEntity<>(headers);
        ResponseEntity<Map> listResp = restTemplate.exchange(
                "/api/admin/admin-users?page=1&size=20", HttpMethod.GET, listReq, Map.class);

        assertEquals(0, resultCode(listResp));

        // Get detail
        ResponseEntity<Map> getResp = restTemplate.exchange(
                "/api/admin/admin-users/" + newAdminId, HttpMethod.GET, listReq, Map.class);

        assertEquals(0, resultCode(getResp));
        Map<String, Object> adminData = resultData(getResp);
        assertEquals(createBody.get("username"), adminData.get("username"));

        // Update role
        Map<String, String> updateBody = Map.of("role", "SUPER_ADMIN", "status", "ACTIVE");
        HttpEntity<Map<String, String>> updateReq = new HttpEntity<>(updateBody, headers);
        ResponseEntity<Map> updateResp = restTemplate.exchange(
                "/api/admin/admin-users/" + newAdminId, HttpMethod.PUT, updateReq, Map.class);

        assertEquals(0, resultCode(updateResp));

        // Reset password
        Map<String, String> resetBody = Map.of("newPassword", "newpassword456");
        HttpEntity<Map<String, String>> resetReq = new HttpEntity<>(resetBody, headers);
        ResponseEntity<Map> resetResp = restTemplate.exchange(
                "/api/admin/admin-users/" + newAdminId + "/reset-password",
                HttpMethod.POST, resetReq, Map.class);

        assertEquals(0, resultCode(resetResp));

        // Login with the new admin
        Map<String, String> loginBody = Map.of(
                "username", (String) createBody.get("username"),
                "password", "newpassword456"
        );
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> loginReq = new HttpEntity<>(loginBody, loginHeaders);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                "/api/admin/auth/login", loginReq, Map.class);

        assertEquals(0, resultCode(loginResp));
        Map<String, Object> loginData = resultData(loginResp);
        assertNotNull(loginData.get("token"));
    }

    @Test
    void listUsers_AdminCanView() {
        HttpHeaders headers = adminAuthHeaders();
        HttpEntity<Void> req = new HttpEntity<>(headers);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/users?page=1&size=10", HttpMethod.GET, req, Map.class);

        assertEquals(0, resultCode(resp));
    }

    @Test
    void nonSuperAdmin_CannotAccessAdminManagement() {
        // Login as default admin, create a regular admin
        HttpHeaders headers = adminAuthHeaders();

        String username = "regular" + System.currentTimeMillis();
        Map<String, Object> createBody = Map.of(
                "username", username,
                "password", "test123456",
                "role", "ADMIN"
        );
        HttpEntity<Map<String, Object>> createReq = new HttpEntity<>(createBody, headers);
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
                "/api/admin/admin-users", createReq, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> createData = (Map<String, Object>) resultData(createResp);
        Long newAdminId = ((Number) createData.get("id")).longValue();

        // Login as the regular admin
        Map<String, String> loginBody = Map.of("username", username, "password", "test123456");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> loginReq = new HttpEntity<>(loginBody, loginHeaders);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                "/api/admin/auth/login", loginReq, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = (Map<String, Object>) resultData(loginResp);
        String regularToken = (String) loginData.get("token");

        // Try to access admin-users list (should fail - not SUPER_ADMIN)
        HttpHeaders regularHeaders = authHeaders(regularToken);
        HttpEntity<Void> req = new HttpEntity<>(regularHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/admin-users?page=1&size=10", HttpMethod.GET, req, Map.class);

        assertNotEquals(0, resultCode(resp));
    }
}
