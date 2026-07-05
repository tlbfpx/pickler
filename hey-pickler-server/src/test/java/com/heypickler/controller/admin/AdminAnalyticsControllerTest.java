package com.heypickler.controller.admin;

import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAnalyticsControllerTest {

    @Mock private UserMapper userMapper;
    @Mock private EventMapper eventMapper;
    @Mock private RegistrationMapper registrationMapper;

    @InjectMocks private AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        // selectList intentionally NOT pre-stubbed — tests provide their own fixtures
    }

    @Test
    void overview_Default30Days_ShouldReturn30PointsEach() {
        Map<String, Object> data = controller.overview(30).getData();

        assertEquals(30, data.get("days"));
        List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("newUsers");
        List<Map<String, Object>> regs = (List<Map<String, Object>>) data.get("newRegistrations");
        List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("newEvents");
        List<Map<String, Object>> rate = (List<Map<String, Object>>) data.get("completionRate");

        assertEquals(30, users.size());
        assertEquals(30, regs.size());
        assertEquals(30, events.size());
        assertEquals(30, rate.size());
    }

    @Test
    void overview_ShouldClampDaysToMax90() {
        Map<String, Object> data = controller.overview(999).getData();
        assertEquals(90, data.get("days"));
        List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("newUsers");
        assertEquals(90, users.size());
    }

    @Test
    void overview_ShouldClampDaysToMin1() {
        Map<String, Object> data = controller.overview(0).getData();
        assertEquals(1, data.get("days"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overview_ShouldBucketCountsByDay() {
        LocalDate today = LocalDate.now();
        // 3 users today, 1 yesterday, 2 events today, 1 event yesterday completed
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            User u = new User();
            u.setId((long) i);
            u.setCreatedAt(today.atStartOfDay().plusHours(i + 1));
            users.add(u);
        }
        User yesterdayUser = new User();
        yesterdayUser.setId(100L);
        yesterdayUser.setCreatedAt(today.minusDays(1).atStartOfDay().plusHours(5));
        users.add(yesterdayUser);

        List<Event> events = new ArrayList<>();
        Event todayEvent = new Event();
        todayEvent.setId(1L);
        todayEvent.setStatus("OPEN");
        todayEvent.setCreatedAt(today.atStartOfDay());
        events.add(todayEvent);
        Event todayEvent2 = new Event();
        todayEvent2.setId(2L);
        todayEvent2.setStatus("OPEN");
        todayEvent2.setCreatedAt(today.atStartOfDay().plusHours(1));
        events.add(todayEvent2);
        Event completedYesterday = new Event();
        completedYesterday.setId(3L);
        completedYesterday.setStatus("COMPLETED");
        completedYesterday.setCreatedAt(today.minusDays(1).atStartOfDay());
        events.add(completedYesterday);

        doReturn(users).when(userMapper).selectList(any());
        doReturn(events).when(eventMapper).selectList(any());

        Map<String, Object> data = controller.overview(30).getData();

        List<Map<String, Object>> userPoints = (List<Map<String, Object>>) data.get("newUsers");
        assertEquals(3, ((Number) userPoints.get(userPoints.size() - 1).get("count")).longValue());
        assertEquals(1, ((Number) userPoints.get(userPoints.size() - 2).get("count")).longValue());

        List<Map<String, Object>> eventPoints = (List<Map<String, Object>>) data.get("newEvents");
        assertEquals(2, ((Number) eventPoints.get(eventPoints.size() - 1).get("count")).longValue());
        assertEquals(1, ((Number) eventPoints.get(eventPoints.size() - 2).get("count")).longValue());

        List<Map<String, Object>> ratePoints = (List<Map<String, Object>>) data.get("completionRate");
        // yesterday: 1 completed / 1 non-cancelled = 100%
        assertEquals(100.0, ((Number) ratePoints.get(ratePoints.size() - 2).get("rate")).doubleValue(), 0.01);
        // today cumulative: 1 completed / (1 + 2) non-cancelled = 33.3%
        assertEquals(33.3, ((Number) ratePoints.get(ratePoints.size() - 1).get("rate")).doubleValue(), 0.1);
    }

    @Test
    void overview_OverallCompletionRate_ShouldHandleEmptyData() {
        Map<String, Object> data = controller.overview(30).getData();
        assertEquals(0.0, ((Number) data.get("overallCompletionRate")).doubleValue(), 0.01);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboard_ShouldReturnExpectedShape() {
        Map<String, Object> data = controller.dashboard().getData();

        assertTrue(data.containsKey("totals"));
        Map<String, Object> totals = (Map<String, Object>) data.get("totals");
        assertEquals(0L, totals.get("users"));
        assertEquals(0L, totals.get("events"));
        assertEquals(0L, totals.get("registrations"));
        assertEquals(0.0, ((Number) totals.get("revenue")).doubleValue(), 0.01);

        assertEquals(0.0, ((Number) data.get("completionRate")).doubleValue(), 0.01);
        assertEquals(0.0, ((Number) data.get("registrationPerEvent")).doubleValue(), 0.01);
        assertEquals(0L, data.get("activeUsersLast30d"));

        List<Map<String, Object>> byMonth = (List<Map<String, Object>>) data.get("byMonth");
        assertEquals(12, byMonth.size());
        String firstMonth = (String) byMonth.get(0).get("month");
        String lastMonth = (String) byMonth.get(11).get("month");
        assertTrue(firstMonth.compareTo(lastMonth) < 0, "months should be ascending");

        Map<String, Object> byType = (Map<String, Object>) data.get("byType");
        assertEquals(0L, byType.get("STAR"));
        assertEquals(0L, byType.get("PARTY"));

        Map<String, Object> byStatus = (Map<String, Object>) data.get("byStatus");
        assertNotNull(byStatus, "byStatus should be present");
        // 6 个枚举值都在，未出现的补 0
        assertEquals(6, byStatus.size());
        assertEquals(0L, byStatus.get("DRAFT"));
        assertEquals(0L, byStatus.get("OPEN"));
        assertEquals(0L, byStatus.get("FULL"));
        assertEquals(0L, byStatus.get("IN_PROGRESS"));
        assertEquals(0L, byStatus.get("COMPLETED"));
        assertEquals(0L, byStatus.get("CANCELLED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboard_ShouldComputeTotalsAndRevenue() {
        // totals: 10 users, 20 valid regs, 1 reg with fee=5 → revenue 5.0
        when(userMapper.selectCount(any())).thenReturn(10L);
        when(registrationMapper.selectCount(any())).thenReturn(20L);

        Event feeEvent = new Event();
        feeEvent.setId(1L);
        feeEvent.setFee(new java.math.BigDecimal("5"));
        Registration reg = new Registration();
        reg.setEventId(1L);
        reg.setStatus("REGISTERED");
        when(registrationMapper.selectList(any())).thenReturn(Collections.singletonList(reg));
        when(eventMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(feeEvent));

        Map<String, Object> data = controller.dashboard().getData();

        Map<String, Object> totals = (Map<String, Object>) data.get("totals");
        assertEquals(10L, totals.get("users"));
        assertEquals(20L, totals.get("registrations"));
        assertEquals(5.0, ((Number) totals.get("revenue")).doubleValue(), 0.01);
    }

    @Test
    void dashboard_ShouldComputeCompletionRateFromMocks() {
        // Call order: 1=totalEvents, 2=completed, 3=nonCancelled. Want 4 / 2 / 4 → 50%.
        when(eventMapper.selectCount(any())).thenReturn(4L, 2L, 4L);

        Map<String, Object> data = controller.dashboard().getData();
        assertEquals(50.0, ((Number) data.get("completionRate")).doubleValue(), 0.01);
    }
}