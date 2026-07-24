package com.heypickler.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunk 3 / Task 3.5 — end-to-end booking lifecycle:
 * <ul>
 *   <li>(a) happy path: app create → my-bookings sees it → admin list sees it → admin complete;</li>
 *   <li>(b) concurrent take: two threads posting the same slot → exactly one wins, one gets SLOT_ALREADY_TAKEN;
 *       {@code booking_slot} table has exactly one row for the contested slot;</li>
 *   <li>(c) cancel cutoff: a booking whose slot_start is within 2h (cancel deadline) → user cancel fails with CANCEL_DEADLINE_PASSED;
 *       admin forceCancel succeeds, slot row released;</li>
 *   <li>(d) release &amp; re-take: after cancel, another user can book the same slot CONFIRMED.</li>
 * </ul>
 *
 * <p>All slot dates are computed dynamically as the next weekday 3-6 days out so the
 * test is not wall-clock-fragile (inside 14-day lead window, past the now+30min lower bound).
 *
 * <p>Methods run in declared order via {@link TestMethodOrder} so concurrent / release
 * tests can clean up before the next one starts.
 */
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class VenueBookingIntegrationTest extends IntegrationTestConfig {

    private Long venueId;
    private Long courtId;
    private final List<Long> bookingIdsToCleanup = new ArrayList<>();

    @AfterEach
    void cleanup() {
        JdbcTemplate jdbc = jdbcTemplate;
        // Booking rows first (FK by id, but booking_slot references court_id which we delete last).
        for (Long bid : bookingIdsToCleanup) {
            jdbc.update("DELETE FROM booking_slot WHERE booking_id = ?", bid);
            jdbc.update("DELETE FROM booking WHERE id = ?", bid);
        }
        bookingIdsToCleanup.clear();
        if (courtId != null) {
            jdbc.update("DELETE FROM booking_slot WHERE court_id = ?", courtId);
            jdbc.update("DELETE FROM court_pricing_band WHERE court_id = ?", courtId);
            jdbc.update("DELETE FROM court WHERE id = ?", courtId);
        }
        if (venueId != null) {
            jdbc.update("DELETE FROM venue_business_hour WHERE venue_id = ?", venueId);
            jdbc.update("DELETE FROM venue_contact WHERE venue_id = ?", venueId);
            jdbc.update("DELETE FROM venue WHERE id = ?", venueId);
        }
        venueId = null;
        courtId = null;
    }

    /** Pick the first weekday 3-6 days out (well inside 14-day lead window,
     *  past now+30min even at midnight). */
    private static LocalDate nextWeekdayInsideWindow() {
        LocalDate d = LocalDate.now().plusDays(3);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Seed venue + court with a 09:00-22:00 weekday band @ 40.00. */
    private void seedVenueAndCourt() {
        JdbcTemplate jdbc = jdbcTemplate;
        jdbc.update("INSERT INTO venue (name, address, status, booking_lead_days) " +
                        "VALUES (?, ?, 'ACTIVE', 14)",
                "IT_Booking_Venue", "中关村大街 99 号");
        venueId = jdbc.queryForObject(
                "SELECT id FROM venue WHERE name = ? ORDER BY id DESC LIMIT 1",
                Long.class, "IT_Booking_Venue");
        assertNotNull(venueId);

        jdbc.update("INSERT INTO court (venue_id, name, court_type, slot_minutes, status, sort_order) " +
                        "VALUES (?, ?, 'INDOOR', 60, 'OPEN', 0)",
                venueId, "IT_Booking_Court");
        courtId = jdbc.queryForObject(
                "SELECT id FROM court WHERE venue_id = ? AND name = ? ORDER BY id DESC LIMIT 1",
                Long.class, venueId, "IT_Booking_Court");
        assertNotNull(courtId);

        LocalDate slotDate = nextWeekdayInsideWindow();
        int schemaDow = slotDate.getDayOfWeek().getValue() % 7;
        jdbc.update("INSERT INTO venue_business_hour (venue_id, day_of_week, open_time, close_time) " +
                        "VALUES (?, ?, ?, ?)",
                venueId, schemaDow, Time.valueOf("09:00:00"), Time.valueOf("22:00:00"));

        jdbc.update("INSERT INTO court_pricing_band (court_id, day_type, start_time, end_time, price) " +
                        "VALUES (?, 'WEEKDAY', ?, ?, ?)",
                courtId, Time.valueOf("09:00:00"), Time.valueOf("22:00:00"), new BigDecimal("40.00"));
    }

    private void seedUser(long userId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO user (id, openid, nickname, status, star_points, party_points, star_tier, party_tier) " +
                        "VALUES (?, ?, ?, 'NORMAL', 0, 0, 'BRONZE', 'BRONZE')",
                userId, "bk_openid_" + userId, "BK_" + userId);
    }

    /** Return JSON body to POST /api/app/bookings. */
    private Map<String, Object> createBody(LocalDateTime slotStart, int slotsCount) {
        Map<String, Object> body = new HashMap<>();
        body.put("courtId", courtId);
        body.put("slotStart", slotStart.toString());
        body.put("slotsCount", slotsCount);
        return body;
    }

    /** POST create and return the raw ResponseEntity. */
    private ResponseEntity<Map> postCreate(long userId, Map<String, Object> body) {
        HttpHeaders headers = appAuthHeaders(userId);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity("/api/app/bookings", req, Map.class);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void happyPath_createListComplete() {
        seedUser(8801L);
        seedUser(8802L);
        seedVenueAndCourt();

        // ---- app create (slot 10:00) ----
        LocalDateTime slotStart = LocalDateTime.of(nextWeekdayInsideWindow(), LocalTime.of(10, 0));
        ResponseEntity<Map> createResp = postCreate(8801L, createBody(slotStart, 1));
        assertEquals(0, resultCode(createResp), "create must succeed");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resultData(createResp);
        String bookingNo = (String) data.get("bookingNo");
        Long bookingId = ((Number) data.get("id")).longValue();
        bookingIdsToCleanup.add(bookingId);
        assertNotNull(bookingNo);
        assertTrue(bookingNo.startsWith("BK"), "booking_no must start with BK, got " + bookingNo);
        assertTrue(bookingNo.matches("BK\\d{8}-\\d{4,}"), "booking_no format: " + bookingNo);

        // ---- app my-bookings upcoming → 1 ----
        HttpEntity<Void> myReq = new HttpEntity<>(appAuthHeaders(8801L));
        ResponseEntity<Map> myResp = restTemplate.exchange(
                "/api/app/bookings/my?group=upcoming&page=1&size=10", HttpMethod.GET, myReq, Map.class);
        assertEquals(0, resultCode(myResp));
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) resultData(myResp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertEquals(1, list.size(), "upcoming list should have 1 booking");
        assertEquals(bookingNo, list.get(0).get("bookingNo"));

        // ---- admin list by keyword → 1 ----
        HttpEntity<Void> adminListReq = new HttpEntity<>(adminAuthHeaders());
        ResponseEntity<Map> adminListResp = restTemplate.exchange(
                "/api/admin/bookings?keyword=" + bookingNo, HttpMethod.GET, adminListReq, Map.class);
        assertEquals(0, resultCode(adminListResp));
        @SuppressWarnings("unchecked")
        Map<String, Object> adminPage = (Map<String, Object>) resultData(adminListResp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> adminList = (List<Map<String, Object>>) adminPage.get("list");
        assertEquals(1, adminList.size());
        assertEquals("CONFIRMED", adminList.get(0).get("status"));

        // ---- admin complete ----
        HttpEntity<Void> completeReq = new HttpEntity<>(adminAuthHeaders());
        ResponseEntity<Map> completeResp = restTemplate.postForEntity(
                "/api/admin/bookings/" + bookingId + "/complete", completeReq, Map.class);
        assertEquals(0, resultCode(completeResp));

        // ---- DB: status = COMPLETED ----
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM booking WHERE id = ?", String.class, bookingId);
        assertEquals("COMPLETED", status);

        // ---- upcoming now empty, history has it ----
        ResponseEntity<Map> upResp = restTemplate.exchange(
                "/api/app/bookings/my?group=upcoming&page=1&size=10", HttpMethod.GET, myReq, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> upList = (List<Map<String, Object>>)
                ((Map<String, Object>) resultData(upResp)).get("list");
        assertEquals(0, upList.size(), "completed booking should leave upcoming");

        ResponseEntity<Map> histResp = restTemplate.exchange(
                "/api/app/bookings/my?group=history&page=1&size=10", HttpMethod.GET, myReq, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> histList = (List<Map<String, Object>>)
                ((Map<String, Object>) resultData(histResp)).get("list");
        assertEquals(1, histList.size(), "history group should hold the COMPLETED booking");
        assertEquals("COMPLETED", histList.get(0).get("status"));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void concurrentTake_sameSlot_oneWinsOneLoses() throws Exception {
        seedUser(8803L);
        seedUser(8804L);
        seedVenueAndCourt();
        LocalDateTime slotStart = LocalDateTime.of(nextWeekdayInsideWindow(), LocalTime.of(11, 0));

        // Two threads race on the same (court, slot_start). One booking insert + one
        // booking_slot insert succeeds, the other thread's transaction rolls back
        // and is mapped to SLOT_ALREADY_TAKEN by GlobalExceptionHandler.
        ExecutorService exec = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, Object> raceBody = createBody(slotStart, 1);
        Future<ResponseEntity<Map>> f1 = exec.submit(() -> {
            start.await();
            return postCreate(8803L, raceBody);
        });
        Future<ResponseEntity<Map>> f2 = exec.submit(() -> {
            start.await();
            return postCreate(8804L, raceBody);
        });
        start.countDown();

        ResponseEntity<Map> r1 = f1.get(30, TimeUnit.SECONDS);
        ResponseEntity<Map> r2 = f2.get(30, TimeUnit.SECONDS);
        exec.shutdown();

        int successCount = 0, conflictCount = 0;
        Long winningBookingId = null;
        for (ResponseEntity<Map> r : new ResponseEntity[]{r1, r2}) {
            int code = resultCode(r);
            if (code == 0) {
                successCount++;
                Map<String, Object> d = (Map<String, Object>) resultData(r);
                winningBookingId = ((Number) d.get("id")).longValue();
            } else if (code == 1012) {
                conflictCount++;
            }
        }
        assertEquals(1, successCount, "exactly one thread must succeed");
        assertEquals(1, conflictCount, "exactly one thread must get SLOT_ALREADY_TAKEN (1012)");
        bookingIdsToCleanup.add(winningBookingId);

        // DB invariant: exactly one booking_slot row for the contested (court, slot_start).
        Integer slotRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_slot WHERE court_id = ? AND slot_start = ?",
                Integer.class, courtId, slotStart);
        assertEquals(1, slotRows.intValue(), "exactly one booking_slot row for the contested slot");

        // The winning booking is CONFIRMED.
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM booking WHERE id = ?", String.class, winningBookingId);
        assertEquals("CONFIRMED", status);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void cancelCutoff_userCancelFails_adminForceCancelSucceeds() {
        seedUser(8805L);
        seedVenueAndCourt();

        // Pick slot start 4 days out at 23:00 — but cancelDeadlineHours=2 means we
        // need slotStart < now + 2h for the user to fail. So we use direct SQL seed
        // to plant a booking with slot_start = now + 30min (just inside MIN_LEAD
        // would normally reject, but we bypass the service for the test seed).
        LocalDateTime slotStart = LocalDateTime.now().plusMinutes(45);
        LocalDateTime slotEnd = slotStart.plusHours(1);

        String testBookingNo = "BK_IT_CUTOFF_" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO booking (booking_no, user_id, venue_id, court_id, slot_date, slot_start, slot_end, slots_count, price_snapshot, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, 'CONFIRMED')",
                testBookingNo, 8805L, venueId, courtId,
                slotStart.toLocalDate(), slotStart, slotEnd, new BigDecimal("40.00"));
        Long bookingId = jdbcTemplate.queryForObject(
                "SELECT id FROM booking WHERE booking_no = ?", Long.class, testBookingNo);
        assertNotNull(bookingId, "seeded booking must be found");
        // Add slot row so admin forceCancel has something to delete.
        jdbcTemplate.update(
                "INSERT INTO booking_slot (booking_id, court_id, slot_start) VALUES (?, ?, ?)",
                bookingId, courtId, slotStart);

        // ---- user cancel → CANCEL_DEADLINE_PASSED (1014) ----
        HttpEntity<Void> userCancelReq = new HttpEntity<>(appAuthHeaders(8805L));
        ResponseEntity<Map> userCancelResp = restTemplate.postForEntity(
                "/api/app/bookings/" + bookingId + "/cancel", userCancelReq, Map.class);
        assertEquals(1014, resultCode(userCancelResp), "user cancel must fail with CANCEL_DEADLINE_PASSED");

        // Capture slot row existence before forceCancel so we can confirm deletion.
        Integer slotRowsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_slot WHERE booking_id = ?", Integer.class, bookingId);
        assertEquals(1, slotRowsBefore.intValue());

        // ---- admin forceCancel → 0 ----
        Map<String, Object> body = new HashMap<>();
        body.put("reason", "user too late");
        HttpEntity<Map<String, Object>> adminCancelReq = new HttpEntity<>(body, adminAuthHeaders());
        ResponseEntity<Map> adminCancelResp = restTemplate.postForEntity(
                "/api/admin/bookings/" + bookingId + "/cancel", adminCancelReq, Map.class);
        assertEquals(0, resultCode(adminCancelResp), "admin forceCancel must succeed");

        // ---- DB: status = CANCELLED, booking_slot row deleted ----
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM booking WHERE id = ?", String.class, bookingId);
        assertEquals("CANCELLED", status);
        Integer remainingSlots = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_slot WHERE booking_id = ?", Integer.class, bookingId);
        assertEquals(0, remainingSlots.intValue(), "forceCancel must delete slot rows");

        // Don't add bookingIdsToCleanup — direct SQL rows cleaned by venueId cleanup.
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void releaseAndRetake_cancelFreesSlot_anotherUserCanBookSameSlot() {
        seedUser(8806L);
        seedUser(8807L);
        seedVenueAndCourt();
        LocalDateTime slotStart = LocalDateTime.of(nextWeekdayInsideWindow(), LocalTime.of(13, 0));

        // 1) user 8806 creates booking for slot 13:00
        ResponseEntity<Map> firstCreate = postCreate(8806L, createBody(slotStart, 1));
        assertEquals(0, resultCode(firstCreate));
        Map<String, Object> firstData = (Map<String, Object>) resultData(firstCreate);
        Long firstBookingId = ((Number) firstData.get("id")).longValue();

        // 2) user 8807 tries the same slot → rejected (SLOT_NOT_BOOKABLE 1011).
        //    BookingServiceImpl pre-checks occupied slots before INSERT, so a
        //    sequential second attempt hits 1011 (not 1012). 1012 is reserved
        //    for the concurrent-race path where the INSERT collides with another
        //    in-flight transaction on the UNIQUE(court_id, slot_start) — see
        //    {@link #concurrentTake_sameSlot_oneWinsOneLoses}.
        ResponseEntity<Map> conflict = postCreate(8807L, createBody(slotStart, 1));
        assertEquals(1011, resultCode(conflict),
                "second user must hit SLOT_NOT_BOOKABLE 1011 (sequential path; concurrent path → 1012)");

        // 3) user 8806 cancels
        HttpEntity<Void> cancelReq = new HttpEntity<>(appAuthHeaders(8806L));
        ResponseEntity<Map> cancelResp = restTemplate.postForEntity(
                "/api/app/bookings/" + firstBookingId + "/cancel", cancelReq, Map.class);
        assertEquals(0, resultCode(cancelResp), "cancel must succeed (slot far enough out)");

        // 4) user 8807 retries the same slot → succeeds
        ResponseEntity<Map> retryCreate = postCreate(8807L, createBody(slotStart, 1));
        assertEquals(0, resultCode(retryCreate), "released slot must be re-bookable");
        Map<String, Object> retryData = (Map<String, Object>) resultData(retryCreate);
        Long retryBookingId = ((Number) retryData.get("id")).longValue();
        bookingIdsToCleanup.add(retryBookingId);

        // 5) DB: slot has exactly one row, owned by 8807
        Integer slotRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_slot WHERE court_id = ? AND slot_start = ?",
                Integer.class, courtId, slotStart);
        assertEquals(1, slotRows.intValue());
        Long bookingIdForSlot = jdbcTemplate.queryForObject(
                "SELECT booking_id FROM booking_slot WHERE court_id = ? AND slot_start = ?",
                Long.class, courtId, slotStart);
        assertEquals(retryBookingId, bookingIdForSlot);
    }
}