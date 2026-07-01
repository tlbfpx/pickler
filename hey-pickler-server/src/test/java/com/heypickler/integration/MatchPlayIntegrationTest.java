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
 * End-to-end match-play lifecycle: generate -> submit -> standings -> complete.
 * Reuses the registration + grouping helpers from earlier specs to lock the event.
 */
class MatchPlayIntegrationTest extends IntegrationTestConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Long createEvent(String title, String format, String status) {
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of(
                "type", "STAR",
                "title", title,
                "location", "Match Play IT Court",
                "eventTime", LocalDateTime.now().plusDays(30).format(FMT),
                "registrationDeadline", LocalDateTime.now().plusDays(20).format(FMT),
                "maxParticipants", 20,
                "status", status);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/admin/events", req, Map.class);
        assertEquals(0, resultCode(resp));
        Long eventId = ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue();
        jdbcTemplate.update("UPDATE event SET format = ? WHERE id = ?", format, eventId);
        return eventId;
    }

    private void seedUser(long userId, int starPoints) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, ?, 'NORMAL', ?, ?, 'BRONZE', 'BRONZE')",
                userId, "mp_openid_" + userId, "MP_" + userId, starPoints, starPoints);
    }

    private int register(Long eventId, Long userId) {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(
                Map.of("matchType", "SINGLES"), appAuthHeaders(userId));
        return resultCode(restTemplate.postForEntity(
                "/api/app/events/" + eventId + "/register", req, Map.class));
    }

    private int generateMatches(Long eventId) {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        return resultCode(restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/matches/generate", req, Map.class));
    }

    private int submitScore(Long matchId, Long userId, boolean isAdmin, Object games) {
        HttpHeaders headers = isAdmin ? adminAuthHeaders() : appAuthHeaders(userId);
        HttpEntity<Object> req = new HttpEntity<>(games, headers);
        return resultCode(restTemplate.postForEntity(
                "/api/app/matches/" + matchId + "/score", req, Map.class));
    }

    private void cleanup(Long eventId) {
        jdbcTemplate.update("DELETE FROM match_record WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM registration WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM team WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM group_assignment WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM match_group WHERE event_id = ?", eventId);
        jdbcTemplate.update("UPDATE event SET deleted_at = NOW() WHERE id = ?", eventId);
    }

    /** Locate the match where (slotA, slotB) == (a, b) in either order. */
    private Long findMatchId(List<Map<String, Object>> matches, long a, long b) {
        for (Map<String, Object> m : matches) {
            long sa = ((Number) m.get("slot_a_user_id")).longValue();
            long sb = ((Number) m.get("slot_b_user_id")).longValue();
            if ((sa == a && sb == b) || (sa == b && sb == a)) {
                return ((Number) m.get("id")).longValue();
            }
        }
        throw new IllegalStateException("No match between " + a + " and " + b);
    }

    @Test
    void generate_submitStandingsComplete_singlesLifecycle() {
        // Seed 3 users (round-robin = 3 matches).
        for (long id : new long[]{8801L, 8802L, 8803L}) seedUser(id, 0);

        Long eventId = createEvent("Match Play Singles IT", "SINGLES", "OPEN");
        try {
            assertEquals(0, register(eventId, 8801L));
            assertEquals(0, register(eventId, 8802L));
            assertEquals(0, register(eventId, 8803L));

            // Lock + group via SQL directly (admin group API requires OPEN + locked path).
            jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
            jdbcTemplate.update(
                    "INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
            Long groupId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0",
                    Long.class, eventId);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8801L, 1);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8802L, 2);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8803L, 3);

            assertEquals(0, generateMatches(eventId));

            // Fetch the 3 generated match IDs.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupMatches = (List<Map<String, Object>>) jdbcTemplate.queryForList(
                    "SELECT id, slot_a_user_id, slot_b_user_id FROM match_record WHERE event_id = ? AND group_id = ?",
                    eventId, groupId);
            assertEquals(3, groupMatches.size());

            // Round-robin emits pairs in a non-deterministic order — look each
            // match up by its slot pair rather than by index.
            Long m12 = findMatchId(groupMatches, 8801L, 8802L);
            Long m13 = findMatchId(groupMatches, 8801L, 8803L);
            Long m23 = findMatchId(groupMatches, 8802L, 8803L);

            Map<String, Object> twoZero = new HashMap<>();
            twoZero.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 15),
                    Map.of("game", 2, "a", 21, "b", 18)));
            Map<String, Object> twoOne = new HashMap<>();
            twoOne.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 19),
                    Map.of("game", 2, "a", 18, "b", 21),
                    Map.of("game", 3, "a", 21, "b", 15)));

            assertEquals(0, submitScore(m12, 8801L, false, twoZero));
            assertEquals(0, submitScore(m13, 8801L, false, twoOne));
            assertEquals(0, submitScore(m23, 8802L, false, twoZero));

            // Non-participant rejected (8803 is a participant in m12 & m13 but not m23 — actually 8803 IS in m23 as slot B).
            // Use a clearly unrelated user (8899) that won't match anyway.
            seedUser(8899L, 0);
            assertNotEquals(0, submitScore(m12, 8899L, false, twoZero));

            // Standings: P1 first (2W), P2 second (1W), P3 third (0W).
            HttpEntity<Void> stReq = new HttpEntity<>(adminAuthHeaders());
            ResponseEntity<Map> stResp = restTemplate.exchange(
                    "/api/app/events/" + eventId + "/standings", HttpMethod.GET, stReq, Map.class);
            assertEquals(0, resultCode(stResp));
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> byGroup = (List<List<Map<String, Object>>>) resultData(stResp);
            assertEquals(1, byGroup.size());
            assertEquals(3, byGroup.get(0).size());
            // P1 should have 2 wins.
            long p1Key = 8801L;
            assertEquals(p1Key, ((Number) byGroup.get(0).get(0).get("participantKey")).longValue());
            assertEquals(2, byGroup.get(0).get(0).get("wins"));

            // Complete
            HttpEntity<Void> compReq = new HttpEntity<>(adminAuthHeaders());
            assertEquals(0, resultCode(restTemplate.postForEntity(
                    "/api/admin/events/" + eventId + "/complete", compReq, Map.class)));

            // Event status now COMPLETED — further score rejected.
            assertNotEquals(0, submitScore(m12, 8801L, false, twoZero));
        } finally {
            cleanup(eventId);
        }
    }

    @Test
    void adminStandings_returnsGroupedStandings() {
        seedUser(8101L, 0);
        seedUser(8102L, 0);
        Long eventId = createEvent("Admin Standings IT", "SINGLES", "OPEN");
        try {
            assertEquals(0, register(eventId, 8101L));
            assertEquals(0, register(eventId, 8102L));
            jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
            jdbcTemplate.update("INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
            Long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0", Long.class, eventId);
            jdbcTemplate.update("INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)", groupId, eventId, 8101L, 1);
            jdbcTemplate.update("INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)", groupId, eventId, 8102L, 2);
            assertEquals(0, generateMatches(eventId));

            // rankGroup only aggregates participants from COMPLETED matches, so
            // submit one score (8101 beats 8102) to populate the standings.
            Long matchId = jdbcTemplate.queryForObject(
                "SELECT id FROM match_record WHERE event_id = ?", Long.class, eventId);
            Map<String, Object> twoZero = new HashMap<>();
            twoZero.put("games", List.of(
                Map.of("game", 1, "a", 21, "b", 15),
                Map.of("game", 2, "a", 21, "b", 18)));
            assertEquals(0, submitScore(matchId, 8101L, false, twoZero));

            HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
            ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/events/" + eventId + "/standings", HttpMethod.GET, req, Map.class);
            assertEquals(0, resultCode(resp));
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> byGroup = (List<List<Map<String, Object>>>) resultData(resp);
            assertEquals(1, byGroup.size(), "应有 1 个组");
            assertEquals(2, byGroup.get(0).size(), "该组应有 2 名参与者");
        } finally {
            cleanup(eventId);
        }
    }

    @Test
    void resetMatch_clearsScoresAndReopened() {
        seedUser(8811L, 0);
        seedUser(8812L, 0);
        Long eventId = createEvent("Match Reset IT", "SINGLES", "OPEN");
        try {
            register(eventId, 8811L);
            register(eventId, 8812L);
            jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
            jdbcTemplate.update(
                    "INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
            Long groupId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0",
                    Long.class, eventId);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8811L, 1);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8812L, 2);

            generateMatches(eventId);
            Long matchId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_record WHERE event_id = ?", Long.class, eventId);

            Map<String, Object> twoZero = new HashMap<>();
            twoZero.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 15),
                    Map.of("game", 2, "a", 21, "b", 18)));
            submitScore(matchId, 8811L, false, twoZero);

            // Admin reset
            HttpEntity<Void> resetReq = new HttpEntity<>(adminAuthHeaders());
            ResponseEntity<Map> resetResp = restTemplate.postForEntity(
                    "/api/admin/matches/" + matchId + "/reset", resetReq, Map.class);
            assertEquals(0, resultCode(resetResp));

            // Status back to SCHEDULED, scores cleared
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM match_record WHERE id = ?", String.class, matchId);
            assertEquals("SCHEDULED", status);
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT games FROM match_record WHERE id = ?", String.class, matchId));
        } finally {
            cleanup(eventId);
        }
    }

    @Test
    void adminEventDetail_returnsEvent() {
        Long eventId = createEvent("Detail IT", "SINGLES", "DRAFT");
        try {
            HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
            ResponseEntity<Map> resp = restTemplate.exchange(
                    "/api/admin/events/" + eventId, HttpMethod.GET, req, Map.class);
            assertEquals(0, resultCode(resp));
            assertEquals(eventId, ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue());
        } finally {
            cleanup(eventId);
        }
    }
}