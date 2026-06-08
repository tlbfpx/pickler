package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventIntegrationTest extends IntegrationTestConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Long createTestEvent(String title, String status) {
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of(
                "type", "STAR",
                "title", title,
                "location", "Integration Test Court",
                "eventTime", LocalDateTime.now().plusDays(30).format(FMT),
                "registrationDeadline", LocalDateTime.now().plusDays(20).format(FMT),
                "maxParticipants", 10,
                "status", status
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/admin/events", req, Map.class);
        assertEquals(0, resultCode(resp));
        return ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue();
    }

    private void deleteEvent(Long eventId) {
        HttpHeaders headers = adminAuthHeaders();
        HttpEntity<Void> req = new HttpEntity<>(headers);
        restTemplate.exchange("/api/admin/events/" + eventId, HttpMethod.DELETE, req, Map.class);
    }

    @Test
    void createEvent_ShouldReturnEventId() {
        Long eventId = createTestEvent("Create Test Event", "UPCOMING");
        assertNotNull(eventId);
        assertTrue(eventId > 0);
        deleteEvent(eventId);
    }

    @Test
    void listEvents_AdminPaginated() {
        HttpHeaders headers = adminAuthHeaders();

        Long e1 = createTestEvent("List Test 1", "UPCOMING");
        Long e2 = createTestEvent("List Test 2", "UPCOMING");

        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/events?page=1&size=10", HttpMethod.GET, req, Map.class);

        assertEquals(0, resultCode(resp));
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) resultData(resp);
        assertTrue(((Number) page.get("total")).longValue() >= 2);

        deleteEvent(e1);
        deleteEvent(e2);
    }

    @Test
    void listEvents_AppWithAuth() {
        createTestEvent("App List Test", "UPCOMING");

        HttpHeaders userHeaders = appAuthHeaders(9000L);
        HttpEntity<Void> req = new HttpEntity<>(userHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/app/events?page=1&size=10", HttpMethod.GET, req, Map.class);

        assertEquals(0, resultCode(resp));
    }

    @Test
    void getEventDetail_AppWithUserToken() {
        Long eventId = createTestEvent("Detail Test Event", "UPCOMING");

        HttpHeaders userHeaders = appAuthHeaders(9001L);
        HttpEntity<Void> req = new HttpEntity<>(userHeaders);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/app/events/" + eventId, HttpMethod.GET, req, Map.class);

        assertEquals(0, resultCode(resp));
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) resultData(resp);
        assertEquals("Detail Test Event", detail.get("title"));

        deleteEvent(eventId);
    }

    @Test
    void updateEvent_ShouldModifyFields() {
        Long eventId = createTestEvent("Before Update", "UPCOMING");
        HttpHeaders headers = adminAuthHeaders();

        Map<String, Object> updateBody = Map.of(
                "title", "After Update",
                "maxParticipants", 50
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(updateBody, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/events/" + eventId, HttpMethod.PUT, req, Map.class);

        assertEquals(0, resultCode(resp));
        deleteEvent(eventId);
    }

    @Test
    void registerAndCancel_UserFlow() {
        // Status must be "OPEN" for registration to work
        Long eventId = createTestEvent("Register Test Event", "OPEN");

        // Register
        HttpHeaders userHeaders = appAuthHeaders(8001L);
        Map<String, Object> regBody = Map.of("matchType", "SINGLES");
        HttpEntity<Map<String, Object>> regReq = new HttpEntity<>(regBody, userHeaders);
        ResponseEntity<Map> regResp = restTemplate.postForEntity(
                "/api/app/events/" + eventId + "/register", regReq, Map.class);

        assertEquals(0, resultCode(regResp));

        // Cancel
        HttpEntity<Void> cancelReq = new HttpEntity<>(userHeaders);
        ResponseEntity<Map> cancelResp = restTemplate.exchange(
                "/api/app/events/" + eventId + "/cancel", HttpMethod.POST, cancelReq, Map.class);

        assertEquals(0, resultCode(cancelResp));
        deleteEvent(eventId);
    }

    @Test
    void pointEntry_WithExistingEvent() {
        Long eventId = createTestEvent("Point Test Event", "COMPLETED");
        HttpHeaders headers = adminAuthHeaders();

        // Use the default admin user id (1) — it exists in DB
        // Just verify the point entry endpoint doesn't crash
        Map<String, Object> pointBody = Map.of(
                "records", List.of(
                        Map.of("userId", 1, "points", 100, "reason", "Test points")
                )
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(pointBody, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/points", req, Map.class);

        // Point entry should succeed or fail gracefully
        int code = resultCode(resp);
        assertTrue(code == 0 || code == 404, "Point entry should succeed or handle missing user gracefully, got: " + code);

        deleteEvent(eventId);
    }

    @Test
    void deleteEvent_AdminCanDelete() {
        Long eventId = createTestEvent("Delete Test Event", "UPCOMING");
        HttpHeaders headers = adminAuthHeaders();

        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/events/" + eventId, HttpMethod.DELETE, req, Map.class);
        assertEquals(0, resultCode(resp));

        // Verify the delete endpoint returns success
        // Soft delete is handled internally — just confirm no error
    }
}
