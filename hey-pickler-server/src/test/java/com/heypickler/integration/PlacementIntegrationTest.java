package com.heypickler.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end placement points: admin configures table -> match lifecycle
 * completes event -> point_record rows with source=PLACEMENT are written.
 */
class PlacementIntegrationTest extends IntegrationTestConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Long createEvent(String title) {
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of(
                "type", "STAR",
                "title", title,
                "location", "Placement IT Court",
                "eventTime", LocalDateTime.now().plusDays(30).format(FMT),
                "registrationDeadline", LocalDateTime.now().plusDays(20).format(FMT),
                "maxParticipants", 20,
                "status", "OPEN");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/admin/events", req, Map.class);
        assertEquals(0, resultCode(resp));
        Long eventId = ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue();
        jdbcTemplate.update("UPDATE event SET format = ? WHERE id = ?", "SINGLES", eventId);
        return eventId;
    }

    private void seedUser(long userId, int starPoints) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, ?, 'NORMAL', ?, ?, 'BRONZE', 'BRONZE')",
                userId, "pl_openid_" + userId, "PL_" + userId, starPoints, starPoints);
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

    private int submitScore(Long matchId, Long userId, Object games) {
        HttpEntity<Object> req = new HttpEntity<>(games, appAuthHeaders(userId));
        return resultCode(restTemplate.postForEntity(
                "/api/app/matches/" + matchId + "/score", req, Map.class));
    }

    private int setPlacementPoints(Long eventId, Map<String, Integer> points) {
        HttpHeaders headers = adminAuthHeaders();
        Map<String, Object> body = Map.of("points", points);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        return resultCode(restTemplate.exchange(
                "/api/admin/events/" + eventId + "/placement-points",
                HttpMethod.PUT, req, Map.class));
    }

    private int complete(Long eventId) {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        return resultCode(restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/complete", req, Map.class));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> placementRowsFor(Long eventId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id, points, source, season_code, reason FROM point_record " +
                        "WHERE event_id = ? AND source = 'PLACEMENT' ORDER BY user_id",
                eventId);
    }

    private void cleanup(Long eventId) {
        jdbcTemplate.update("DELETE FROM point_record WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM event_placement_points WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM match_record WHERE event_id = ?", eventId);
        jdbcTemplate.update("DELETE FROM registration WHERE event_id = ?", eventId);
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
    void singlesLifecycle_adminConfiguresPoints_completionWritesPlacementRecords() {
        for (long id : new long[]{8001L, 8002L, 8003L}) seedUser(id, 0);

        Long eventId = createEvent("Placement Singles IT");
        try {
            assertEquals(0, register(eventId, 8001L));
            assertEquals(0, register(eventId, 8002L));
            assertEquals(0, register(eventId, 8003L));

            // Lock + 1 group via direct SQL.
            jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
            jdbcTemplate.update(
                    "INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
            Long groupId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0",
                    Long.class, eventId);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8001L, 1);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8002L, 2);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8003L, 3);

            // Configure placement points: 1st=100, 2nd=60, 3rd=30
            Map<String, Integer> table = new HashMap<>();
            table.put("1", 100);
            table.put("2", 60);
            table.put("3", 30);
            assertEquals(0, setPlacementPoints(eventId, table));

            // Generate + play + score matches.
            assertEquals(0, generateMatches(eventId));
            List<Map<String, Object>> matches = jdbcTemplate.queryForList(
                    "SELECT id, slot_a_user_id, slot_b_user_id FROM match_record " +
                            "WHERE event_id = ? ORDER BY id", eventId);
            assertEquals(3, matches.size());

            Map<String, Object> twoZero = new HashMap<>();
            twoZero.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 15),
                    Map.of("game", 2, "a", 21, "b", 18)));
            Map<String, Object> twoOne = new HashMap<>();
            twoOne.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 19),
                    Map.of("game", 2, "a", 18, "b", 21),
                    Map.of("game", 3, "a", 21, "b", 15)));

            // Find each match by its slot pair (RoundRobin output order is not
            // guaranteed to match registration order). The submitter must be a
            // participant in the match, otherwise submitScore returns 403.
            Long match8001v8002 = findMatchId(matches, 8001L, 8002L);
            Long match8001v8003 = findMatchId(matches, 8001L, 8003L);
            Long match8002v8003 = findMatchId(matches, 8002L, 8003L);

            // 8001 beats 8002 (2-0), 8001 beats 8003 (2-1), 8002 beats 8003 (2-0).
            assertEquals(0, submitScore(match8001v8002, 8001L, twoZero));
            assertEquals(0, submitScore(match8001v8003, 8001L, twoOne));
            assertEquals(0, submitScore(match8002v8003, 8002L, twoZero));

            // Complete — should trigger placement issuance
            assertEquals(0, complete(eventId));

            List<Map<String, Object>> rows = placementRowsFor(eventId);
            assertEquals(3, rows.size());

            // 8001 has 2 wins (rank 1) -> 100 pts
            assertEquals(8001L, ((Number) rows.get(0).get("user_id")).longValue());
            assertEquals(100, rows.get(0).get("points"));
            // 8002 has 1 win (rank 2) -> 60 pts
            assertEquals(8002L, ((Number) rows.get(1).get("user_id")).longValue());
            assertEquals(60, rows.get(1).get("points"));
            // 8003 has 0 wins (rank 3) -> 30 pts
            assertEquals(8003L, ((Number) rows.get(2).get("user_id")).longValue());
            assertEquals(30, rows.get(2).get("points"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            cleanup(eventId);
        }
    }

    @Test
    void defaultPlacementPoints_applyWhenNoOverride() {
        // Don't set per-event override -> falls back to application.yml
        // hey-pickler.placement.defaultPoints (1:100, 2:60, 3:30, 4:15).
        for (long id : new long[]{8001L, 8002L}) seedUser(id, 0);
        Long eventId = createEvent("Default Placement IT");
        try {
            assertEquals(0, register(eventId, 8001L));
            assertEquals(0, register(eventId, 8002L));
            jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
            jdbcTemplate.update(
                    "INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
            Long groupId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0",
                    Long.class, eventId);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8001L, 1);
            jdbcTemplate.update(
                    "INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)",
                    groupId, eventId, 8002L, 2);

            assertEquals(0, generateMatches(eventId));
            Long matchId = jdbcTemplate.queryForObject(
                    "SELECT id FROM match_record WHERE event_id = ?", Long.class, eventId);

            Map<String, Object> twoZero = new HashMap<>();
            twoZero.put("games", List.of(
                    Map.of("game", 1, "a", 21, "b", 15),
                    Map.of("game", 2, "a", 21, "b", 18)));
            assertEquals(0, submitScore(matchId, 8001L, twoZero));
            assertEquals(0, complete(eventId));

            List<Map<String, Object>> rows = placementRowsFor(eventId);
            assertEquals(2, rows.size());
            assertEquals(8001L, ((Number) rows.get(0).get("user_id")).longValue());
            assertEquals(100, rows.get(0).get("points"));
            assertEquals(8002L, ((Number) rows.get(1).get("user_id")).longValue());
            assertEquals(60, rows.get(1).get("points"));
        } finally {
            cleanup(eventId);
        }
    }
}