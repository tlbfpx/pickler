package com.heypickler.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end grouping lifecycle through the real HTTP/DB stack:
 * doubles team build+confirm -> SERPENTINE group -> lock (rejects re-group) -> unlock (clears).
 * Also a singles check that grouping keys on user_id.
 */
class GroupingIntegrationTest extends IntegrationTestConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Long createEvent(String title, String format, String status) {
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of(
                "type", "STAR",
                "title", title,
                "location", "Grouping IT Court",
                "eventTime", LocalDateTime.now().plusDays(30).format(FMT),
                "registrationDeadline", LocalDateTime.now().plusDays(20).format(FMT),
                "maxParticipants", 20,
                "status", status);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/admin/events", req, Map.class);
        assertEquals(0, resultCode(resp));
        Long eventId = ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue();
        // Admin create API does not yet accept format (Chunk 4); set it directly.
        jdbcTemplate.update("UPDATE event SET format = ? WHERE id = ?", format, eventId);
        return eventId;
    }

    private void seedUser(long userId, int starPoints) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) "
                        + "VALUES (?, ?, ?, 'NORMAL', ?, ?, 'BRONZE', 'BRONZE')",
                userId, "grp_openid_" + userId, "GrpUser_" + userId, starPoints, starPoints);
    }

    private int register(Long eventId, Long userId, Map<String, Object> body) {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, appAuthHeaders(userId));
        return resultCode(restTemplate.postForEntity(
                "/api/app/events/" + eventId + "/register", req, Map.class));
    }

    private Long myTeamId(Long eventId, Long userId) {
        HttpEntity<Void> req = new HttpEntity<>(appAuthHeaders(userId));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/app/events/" + eventId + "/my-team", HttpMethod.GET, req, Map.class);
        Map<String, Object> team = resultData(resp);
        return team == null ? null : ((Number) team.get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> group(Long eventId, String strategy, int groupCount) {
        HttpHeaders headers = adminAuthHeaders();
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("strategy", strategy, "groupCount", groupCount), headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/grouping", req, Map.class);
        assertEquals(0, resultCode(resp));
        return resultData(resp);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getGroups(Long eventId) {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/events/" + eventId + "/grouping", HttpMethod.GET, req, Map.class);
        assertEquals(0, resultCode(resp));
        return resultData(resp);
    }

    private void cleanup(Long eventId) {
        // child -> parent, then soft-delete the event
        jdbcTemplate.update("DELETE FROM group_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM match_group WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM registration WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM team WHERE event_id = ?", eventId);
        jdbcTemplate.update("UPDATE event SET deleted_at = NOW() WHERE id = ?", eventId);
    }

    @Test
    void doublesTeamFlow_serpentineLockUnlock() {
        for (long id : new long[]{8001L, 8002L, 8003L, 8004L}) {
            seedUser(id, 0);
        }
        Long eventId = createEvent("Grouping Doubles IT", "DOUBLES", "OPEN");
        try {
            // Team 1: 8001 (captain) invites 8002, partner confirms.
            assertEquals(0, register(eventId, 8001L, body("DOUBLES", 8002L, null)));
            Long team1 = myTeamId(eventId, 8001L);
            assertNotNull(team1, "captain should have a PENDING team");
            assertEquals(0, register(eventId, 8002L, body("DOUBLES", null, team1)));

            // Team 2: 8003 invites 8004, partner confirms.
            assertEquals(0, register(eventId, 8003L, body("DOUBLES", 8004L, null)));
            Long team2 = myTeamId(eventId, 8003L);
            assertNotNull(team2);
            assertEquals(0, register(eventId, 8004L, body("DOUBLES", null, team2)));

            // Group the two confirmed teams into 2 groups.
            List<Map<String, Object>> groups = group(eventId, "SERPENTINE", 2);
            assertEquals(2, groups.size());

            List<Map<String, Object>> fetched = getGroups(eventId);
            assertEquals(2, fetched.size());
            // both teams present across the assignments
            assertTrue(containsTeam(fetched, team1));
            assertTrue(containsTeam(fetched, team2));

            // Lock — re-grouping must now be refused.
            assertEquals(0, lock(eventId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT grouping_locked FROM event WHERE id = ?", Integer.class, eventId));
            HttpHeaders adminHeaders = adminAuthHeaders();
            HttpEntity<Map<String, Object>> reGroup = new HttpEntity<>(
                    Map.of("strategy", "SERPENTINE", "groupCount", 2), adminHeaders);
            ResponseEntity<Map> refused = restTemplate.postForEntity(
                    "/api/admin/events/" + eventId + "/grouping", reGroup, Map.class);
            assertNotEquals(0, resultCode(refused));

            // Unlock — groups cleared, roster reopened.
            assertEquals(0, unlock(eventId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT grouping_locked FROM event WHERE id = ?", Integer.class, eventId));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM match_group WHERE event_id = ?", Integer.class, eventId));
        } finally {
            cleanup(eventId);
        }
    }

    @Test
    void singlesFlow_groupsByUserIdIgnoringPartner() {
        seedUser(8001L, 0);
        seedUser(8002L, 0);
        Long eventId = createEvent("Grouping Singles IT", "SINGLES", "OPEN");
        try {
            assertEquals(0, register(eventId, 8001L, Map.of("matchType", "SINGLES")));
            assertEquals(0, register(eventId, 8002L, Map.of("matchType", "SINGLES")));

            List<Map<String, Object>> groups = group(eventId, "SERPENTINE", 2);
            assertEquals(2, groups.size());
            // assignments key on userId, never teamId
            assertTrue(allAssignments(groups).stream().allMatch(a -> a.get("teamId") == null));
            assertTrue(allAssignments(groups).stream().anyMatch(a -> userIdOf(a) == 8001L));
            assertTrue(allAssignments(groups).stream().anyMatch(a -> userIdOf(a) == 8002L));
        } finally {
            cleanup(eventId);
        }
    }

    // ---------- helpers ----------

    private int lock(Long eventId) {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        return resultCode(restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/grouping/lock", req, Map.class));
    }

    private int unlock(Long eventId) {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        return resultCode(restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/grouping/unlock", req, Map.class));
    }

    private Map<String, Object> body(String matchType, Long partnerUserId, Long teamId) {
        Map<String, Object> b = new HashMap<>();
        b.put("matchType", matchType);
        if (partnerUserId != null) b.put("partnerUserId", partnerUserId);
        if (teamId != null) b.put("teamId", teamId);
        return b;
    }

    @SuppressWarnings("unchecked")
    private boolean containsTeam(List<Map<String, Object>> groups, Long teamId) {
        return allAssignments(groups).stream().anyMatch(a -> teamId.equals(((Number) a.get("teamId")).longValue()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allAssignments(List<Map<String, Object>> groups) {
        return groups.stream()
                .flatMap(g -> ((List<Map<String, Object>>) g.get("assignments")).stream())
                .toList();
    }

    private long userIdOf(Map<String, Object> assignment) {
        Object v = assignment.get("userId");
        return v == null ? -1L : ((Number) v).longValue();
    }
}
