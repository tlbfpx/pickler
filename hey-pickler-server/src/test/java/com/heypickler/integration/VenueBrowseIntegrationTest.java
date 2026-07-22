package com.heypickler.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunk 3 / Task 3.7 — end-to-end browse + role guard for venues/courts.
 *
 * <p>Seeds venue + court + business hour + pricing band via {@link JdbcTemplate},
 * then asserts:
 * <ul>
 *   <li>anonymous {@code GET /api/app/venues} returns {@code code==0} (Task 3.1 bypass);</li>
 *   <li>{@code POST /api/admin/venues} with {@code adminAuthHeaders()} → code 0,
 *       without token → non-zero (401/403 role guard);</li>
 *   <li>an OPEN court with a 09:00-11:00 weekday band returns 2 slots, both
 *       {@code available==true} and {@code price==40}, for a future weekday
 *       within the 14-day booking window.</li>
 * </ul>
 *
 * <p>The slot date is computed dynamically as the next weekday 3-6 days out so the
 * test is not wall-clock-fragile (well inside {@code booking_lead_days=14} and
 * past the {@code now+30min} lower bound at any hour).
 */
class VenueBrowseIntegrationTest extends IntegrationTestConfig {

    private Long venueId;
    private Long courtId;

    @AfterEach
    void cleanup() {
        // Physical deletes — test cleanup only. Order respects FK-by-id references.
        JdbcTemplate jdbc = jdbcTemplate;
        if (courtId != null) {
            jdbc.update("DELETE FROM booking_slot WHERE court_id = ?", courtId);
            jdbc.update("DELETE FROM court_pricing_band WHERE court_id = ?", courtId);
        }
        if (venueId != null) {
            jdbc.update("DELETE FROM venue_business_hour WHERE venue_id = ?", venueId);
            jdbc.update("DELETE FROM venue_contact WHERE venue_id = ?", venueId);
        }
        if (courtId != null) {
            // court uses soft delete; physical-delete here to fully reset the name_key UNIQUE.
            jdbc.update("DELETE FROM court WHERE id = ?", courtId);
        }
        if (venueId != null) {
            jdbc.update("DELETE FROM venue WHERE id = ?", venueId);
        }
        venueId = null;
        courtId = null;
    }

    /** Pick the first weekday that is strictly inside the 14-day booking window
     *  (3-6 days out keeps it past {@code now+30min} even near midnight, and
     *  before {@code now+14d} regardless of wall-clock time-of-day). */
    private static LocalDate nextWeekdayInsideWindow() {
        LocalDate d = LocalDate.now().plusDays(3);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void anonymousBrowseAndSlotLookupWork_andAdminWriteIsGuarded() {
        JdbcTemplate jdbc = jdbcTemplate;

        // ---- seed venue ----
        jdbc.update("INSERT INTO venue (name, address, status, booking_lead_days) " +
                        "VALUES (?, ?, 'ACTIVE', 14)",
                "IT_Venue_Browse", "中关村大街 1 号");
        venueId = jdbc.queryForObject(
                "SELECT id FROM venue WHERE name = ? ORDER BY id DESC LIMIT 1",
                Long.class, "IT_Venue_Browse");
        assertNotNull(venueId, "seed venue must exist");

        // ---- seed court (OPEN, default 60-min slots) ----
        jdbc.update("INSERT INTO court (venue_id, name, court_type, slot_minutes, status, sort_order) " +
                        "VALUES (?, ?, 'INDOOR', 60, 'OPEN', 0)",
                venueId, "IT_Court_1");
        courtId = jdbc.queryForObject(
                "SELECT id FROM court WHERE venue_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
                Long.class, venueId, "IT_Court_1");
        assertNotNull(courtId, "seed court must exist");

        // ---- business hour for the target weekday (schema dow = getValue() % 7) ----
        LocalDate slotDate = nextWeekdayInsideWindow();
        int schemaDow = slotDate.getDayOfWeek().getValue() % 7; // Mon=1..Sat=6, Sun=0
        jdbc.update("INSERT INTO venue_business_hour (venue_id, day_of_week, open_time, close_time) " +
                        "VALUES (?, ?, ?, ?)",
                venueId, schemaDow, Time("09:00:00"), Time("11:00:00"));

        // ---- pricing band: WEEKDAY 09:00-11:00 @ 40.00 ----
        jdbc.update("INSERT INTO court_pricing_band (court_id, day_type, start_time, end_time, price) " +
                        "VALUES (?, 'WEEKDAY', ?, ?, ?)",
                courtId, Time("09:00:00"), Time("11:00:00"), new BigDecimal("40.00"));

        // ============ assertion 1: anonymous GET /api/app/venues = code 0 ============
        ResponseEntity<Map> anonList = restTemplate.getForEntity("/api/app/venues", Map.class);
        assertEquals(0, resultCode(anonList), "anonymous venue browse must be allowed");

        // anonymous GET detail (also bypassed)
        ResponseEntity<Map> anonDetail = restTemplate.getForEntity("/api/app/venues/" + venueId, Map.class);
        assertEquals(0, resultCode(anonDetail), "anonymous venue detail must be allowed");

        // ============ assertion 2: admin write without token → rejected (role guard) ============
        // A no-token admin write returns 401 from AdminAuthFilter. SimpleClientHttpRequestFactory
        // (the TestRestTemplate default) hits a known JDK bug on 401 responses — HttpURLConnection
        // tries to retry on WWW-Authenticate and throws "cannot retry due to server authentication,
        // in streaming mode" (ResourceAccessException). The throw itself is proof of rejection; we
        // additionally fall back to asserting the JSON body's code field if the client manages to
        // surface it. Either branch passes only when the request did NOT reach the controller.
        HttpHeaders bare = new HttpHeaders();
        HttpEntity<Void> noTokenReq = new HttpEntity<>(bare);
        int noTokenCode;
        try {
            ResponseEntity<Map> noTokenResp = restTemplate.exchange(
                    "/api/admin/venues/" + venueId, HttpMethod.DELETE, noTokenReq, Map.class);
            noTokenCode = resultCode(noTokenResp);
        } catch (ResourceAccessException rejected) {
            // JDK HttpURLConnection retry-on-401 bug — the 401 response is what triggered it.
            noTokenCode = -1;
        }
        assertNotEquals(0, noTokenCode, "admin DELETE without token must be rejected");

        // admin POST with token → code 0
        Map<String, Object> okBody = Map.of(
                "name", "IT_Venue_Created",
                "address", "created");
        HttpEntity<Map<String, Object>> authedReq = new HttpEntity<>(okBody, adminAuthHeaders());
        ResponseEntity<Map> authedResp = restTemplate.postForEntity("/api/admin/venues", authedReq, Map.class);
        assertEquals(0, resultCode(authedResp), "admin POST with token must succeed");
        Long createdVenueId = ((Number) ((Map<String, Object>) resultData(authedResp)).get("id")).longValue();
        jdbcTemplate.update("DELETE FROM venue WHERE id = ?", createdVenueId); // clean up the just-created row

        // ============ assertion 3: slots for the seeded OPEN court on slotDate ============
        ResponseEntity<Map> slotsResp = restTemplate.getForEntity(
                "/api/app/courts/" + courtId + "/slots?date=" + slotDate, Map.class);
        assertEquals(0, resultCode(slotsResp), "slot lookup must succeed anonymously");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) resultData(slotsResp);
        assertFalse(slots.isEmpty(), "should return slots for the seeded business hour");
        assertEquals(2, slots.size(), "09:00-11:00 with 60-min slots = 2 slots");
        for (Map<String, Object> slot : slots) {
            assertEquals(Boolean.TRUE, slot.get("available"), "slot should be available");
            // compareTo (not equals) — Jackson may deserialize 40.00 as 40.0.
            assertEquals(0, new BigDecimal("40.00").compareTo(new BigDecimal(slot.get("price").toString())),
                    "slot price should match the seeded band (40.00)");
        }
    }

    private static java.sql.Time Time(String s) { return java.sql.Time.valueOf(LocalTime.parse(s)); }
}
