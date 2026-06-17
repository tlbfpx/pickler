package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理端-首页")
public class AdminDashboardController {

    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;

    @GetMapping
    @Operation(summary = "首页统计数据")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> data = new LinkedHashMap<>();

        // === Core KPIs ===
        long totalUsers = userMapper.selectCount(null);
        long bannedUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, "BANNED"));
        long newUsersWeek = userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, LocalDateTime.now().minusDays(7)));

        long totalEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt));
        long openEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "OPEN"));
        long inProgressEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "IN_PROGRESS"));

        long totalRegistrations = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED"));
        long recentRegistrations = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>()
                        .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                        .ge(Registration::getCreatedAt, LocalDateTime.now().minusDays(7)));

        // === Revenue: sum(event.fee) for valid registrations ===
        List<Registration> validRegs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED"));
        List<Long> validEventIds = validRegs.stream().map(Registration::getEventId).distinct().collect(Collectors.toList());
        Map<Long, Event> feeEventMap = validEventIds.isEmpty() ? Collections.emptyMap()
                : eventMapper.selectBatchIds(validEventIds).stream().collect(Collectors.toMap(Event::getId, e -> e));
        double totalRevenue = validRegs.stream()
                .mapToDouble(r -> { Event e = feeEventMap.get(r.getEventId()); return e != null && e.getFee() != null ? e.getFee().doubleValue() : 0; })
                .sum();

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        double weeklyRevenue = validRegs.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(weekAgo))
                .mapToDouble(r -> { Event e = feeEventMap.get(r.getEventId()); return e != null && e.getFee() != null ? e.getFee().doubleValue() : 0; })
                .sum();

        data.put("totalUsers", totalUsers);
        data.put("bannedUsers", bannedUsers);
        data.put("newUsersWeek", newUsersWeek);
        data.put("totalEvents", totalEvents);
        data.put("openEvents", openEvents);
        data.put("inProgressEvents", inProgressEvents);
        data.put("totalRegistrations", totalRegistrations);
        data.put("recentRegistrationsCount", recentRegistrations);
        data.put("totalRevenue", Math.round(totalRevenue * 100) / 100.0);
        data.put("weeklyRevenue", Math.round(weeklyRevenue * 100) / 100.0);

        // === Tier distribution ===
        List<User> allUsers = userMapper.selectList(null);
        Map<String, Long> starTierDist = allUsers.stream()
                .filter(u -> u.getStarPoints() != null && u.getStarPoints() > 0)
                .collect(Collectors.groupingBy(u -> u.getStarTier() != null ? u.getStarTier() : "SHINING", Collectors.counting()));
        Map<String, Long> partyTierDist = allUsers.stream()
                .filter(u -> u.getPartyPoints() != null && u.getPartyPoints() > 0)
                .collect(Collectors.groupingBy(u -> u.getPartyTier() != null ? u.getPartyTier() : "SHINING", Collectors.counting()));
        data.put("starTierDistribution", starTierDist);
        data.put("partyTierDistribution", partyTierDist);

        // === Event type distribution ===
        long starEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "STAR"));
        long partyEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "PARTY"));
        Map<String, Long> eventTypeDist = new LinkedHashMap<>();
        eventTypeDist.put("STAR", starEvents);
        eventTypeDist.put("PARTY", partyEvents);
        data.put("eventTypes", eventTypeDist);

        // === Daily new users (last 30 days) ===
        List<Map<String, Object>> dailyUsers = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().ge(User::getCreatedAt, start).le(User::getCreatedAt, end));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("count", count);
            dailyUsers.add(point);
        }
        data.put("dailyNewUsers", dailyUsers);

        // === Daily registrations (last 30 days) ===
        List<Map<String, Object>> dailyRegs = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            long count = registrationMapper.selectCount(
                    new LambdaQueryWrapper<Registration>()
                            .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                            .ge(Registration::getCreatedAt, start).le(Registration::getCreatedAt, end));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("count", count);
            dailyRegs.add(point);
        }
        data.put("dailyRegistrations", dailyRegs);

        // === Recent registrations (latest 10) ===
        List<Registration> recentRegs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                        .orderByDesc(Registration::getCreatedAt)
                        .last("LIMIT 10"));
        List<Long> regUserIds = recentRegs.stream().map(Registration::getUserId).distinct().collect(Collectors.toList());
        List<Long> regEventIds = recentRegs.stream().map(Registration::getEventId).distinct().collect(Collectors.toList());

        Map<Long, User> userMap = regUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(regUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Event> eventMap = regEventIds.isEmpty() ? Collections.emptyMap()
                : eventMapper.selectBatchIds(regEventIds).stream().collect(Collectors.toMap(Event::getId, e -> e));

        List<Map<String, Object>> recentRegList = recentRegs.stream()
                .filter(reg -> userMap.containsKey(reg.getUserId()))
                .map(reg -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", reg.getId());
                    User user = userMap.get(reg.getUserId());
                    item.put("nickname", user.getNickname());
                    Event event = eventMap.get(reg.getEventId());
                    item.put("eventTitle", event != null ? event.getTitle() : "未知赛事");
                    item.put("matchType", reg.getMatchType());
                    item.put("status", reg.getStatus());
                    item.put("createdAt", reg.getCreatedAt() != null ? reg.getCreatedAt().toString() : null);
                    return item;
                }).collect(Collectors.toList());
        data.put("recentRegistrations", recentRegList);

        // === Upcoming events (next 5) ===
        List<Event> upcomingEvents = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .isNull(Event::getDeletedAt)
                        .ge(Event::getEventTime, LocalDateTime.now())
                        .orderByAsc(Event::getEventTime)
                        .last("LIMIT 5"));
        List<Map<String, Object>> upcomingList = upcomingEvents.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("title", e.getTitle());
            item.put("type", e.getType());
            item.put("eventTime", e.getEventTime() != null ? e.getEventTime().toString() : null);
            item.put("location", e.getLocation());
            item.put("currentParticipants", e.getCurrentParticipants());
            item.put("maxParticipants", e.getMaxParticipants());
            item.put("status", e.getStatus());
            return item;
        }).collect(Collectors.toList());
        data.put("upcomingEvents", upcomingList);

        return Result.ok(data);
    }
}
