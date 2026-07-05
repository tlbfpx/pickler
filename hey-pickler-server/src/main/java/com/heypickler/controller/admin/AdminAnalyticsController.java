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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端-分析仪表盘 / 趋势图
 *
 * <p>复用既有 mapper 做 Java 侧聚合，与 AdminDashboardController 风格一致。
 * 两个端点：
 * <ul>
 *   <li>{@code GET /api/admin/analytics/overview?days=30} — 30 天趋势数据（KPI 趋势图）</li>
 *   <li>{@code GET /api/admin/analytics/dashboard} — 分析仪表盘总览 + 12 个月趋势</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "管理端-数据分析")
public class AdminAnalyticsController {

    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;

    // ====================== KPI 趋势图 ======================

    @GetMapping("/overview")
    @Operation(summary = "30 天趋势：新增用户/报名/赛事 + 完赛率")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> overview(
            @Parameter(description = "回溯天数，默认 30")
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.max(1, Math.min(days, 90));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);

        LocalDate today = LocalDate.now();

        // 一次性拉回窗口内的全部记录，Java 侧按天聚合——避免 N 次 selectCount
        LocalDateTime windowStart = today.minusDays(safeDays - 1L).atStartOfDay();

        List<User> usersInWindow = userMapper.selectList(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, windowStart));
        List<Registration> regsInWindow = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                        .ge(Registration::getCreatedAt, windowStart));
        List<Event> eventsInWindow = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .isNull(Event::getDeletedAt)
                        .ge(Event::getCreatedAt, windowStart));

        // 完赛率：窗口内每日（截止当日）累计 COMPLETED / (COMPLETED + 非 CANCELLED)
        // 端点返回单日 = 截至当日累计比率的滚动序列，避免当窗口早期无数据时整体偏低
        long allNonCancelled = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).ne(Event::getStatus, "CANCELLED"));
        long allCompleted = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "COMPLETED"));

        // 按天汇总
        long[] userBucket = new long[safeDays];
        long[] regBucket = new long[safeDays];
        long[] eventBucket = new long[safeDays];

        for (User u : usersInWindow) {
            if (u.getCreatedAt() == null) continue;
            int idx = dayIndex(today, u.getCreatedAt().toLocalDate(), safeDays);
            if (idx >= 0) userBucket[idx]++;
        }
        for (Registration r : regsInWindow) {
            if (r.getCreatedAt() == null) continue;
            int idx = dayIndex(today, r.getCreatedAt().toLocalDate(), safeDays);
            if (idx >= 0) regBucket[idx]++;
        }
        for (Event e : eventsInWindow) {
            if (e.getCreatedAt() == null) continue;
            int idx = dayIndex(today, e.getCreatedAt().toLocalDate(), safeDays);
            if (idx >= 0) eventBucket[idx]++;
        }

        // 完赛率序列：累计 COMPLETED / 累计 非 CANCELLED 截至当日
        // 在窗口期内，已存在的赛事按其 status 决定是否计入；这里用窗口内事件做近似：
        // 累计 completed = sum(每日新增且当前 status=COMPLETED 的事件)；窗口内非 cancelled 同理
        long completedByDay = 0;
        long nonCancelledByDay = 0;
        // 先取每日 events 的 status 字典以便累计
        // 为简化：每日新增 events 在 createdAt 当日已确定 status（绝大多数），后续可能由 scheduler 更新。
        // 累计 = sum(每日新增且 status=COMPLETED) / sum(每日新增且 status!=CANCELLED)。
        long[] dailyNewCompleted = new long[safeDays];
        long[] dailyNewNonCancelled = new long[safeDays];
        for (Event e : eventsInWindow) {
            if (e.getCreatedAt() == null) continue;
            int idx = dayIndex(today, e.getCreatedAt().toLocalDate(), safeDays);
            if (idx < 0) continue;
            if (!"CANCELLED".equals(e.getStatus())) dailyNewNonCancelled[idx]++;
            if ("COMPLETED".equals(e.getStatus())) dailyNewCompleted[idx]++;
        }

        List<Map<String, Object>> newUsers = new ArrayList<>(safeDays);
        List<Map<String, Object>> newRegs = new ArrayList<>(safeDays);
        List<Map<String, Object>> newEvents = new ArrayList<>(safeDays);
        List<Map<String, Object>> completionRate = new ArrayList<>(safeDays);

        // dayIndex returns "days ago" (0 = today). Output is oldest → newest,
// so position i in the output list corresponds to (safeDays - 1 - i) days ago.
        for (int i = 0; i < safeDays; i++) {
            int daysAgo = safeDays - 1 - i;
            String date = today.minusDays(daysAgo).toString();
            newUsers.add(point(date, userBucket[daysAgo]));
            newRegs.add(point(date, regBucket[daysAgo]));
            newEvents.add(point(date, eventBucket[daysAgo]));

            completedByDay += dailyNewCompleted[daysAgo];
            nonCancelledByDay += dailyNewNonCancelled[daysAgo];
            double rate = nonCancelledByDay == 0 ? 0d : round1((double) completedByDay * 100d / nonCancelledByDay);
            completionRate.add(ratePoint(date, rate));
        }

        data.put("newUsers", newUsers);
        data.put("newRegistrations", newRegs);
        data.put("newEvents", newEvents);
        data.put("completionRate", completionRate);

        // 顺便附当前总览完赛率供前端 fallback
        data.put("overallCompletionRate", allNonCancelled == 0 ? 0d : round1((double) allCompleted * 100d / allNonCancelled));

        return Result.ok(data);
    }

    // ====================== 分析仪表盘 ======================

    @GetMapping("/dashboard")
    @Operation(summary = "分析仪表盘：总览指标 + 12 个月趋势")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // ---- Totals ----
        long totalUsers = userMapper.selectCount(null);
        long totalEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt));
        long totalRegs = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED"));
        long completed = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "COMPLETED"));
        long nonCancelled = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).ne(Event::getStatus, "CANCELLED"));

        // 收入 = 有效报名 × 赛事 fee（与 dashboard 保持一致口径）
        List<Registration> validRegs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED"));
        Map<Long, Event> eventFeeMap = validRegs.isEmpty()
                ? Map.of()
                : eventMapper.selectBatchIds(validRegs.stream().map(Registration::getEventId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Event::getId, e -> e));
        double totalRevenue = validRegs.stream()
                .mapToDouble(r -> {
                    Event e = eventFeeMap.get(r.getEventId());
                    return e != null && e.getFee() != null ? e.getFee().doubleValue() : 0;
                })
                .sum();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("users", totalUsers);
        totals.put("events", totalEvents);
        totals.put("registrations", totalRegs);
        totals.put("revenue", Math.round(totalRevenue * 100) / 100.0);
        data.put("totals", totals);

        // ---- Ratios ----
        double completionRate = nonCancelled == 0 ? 0d : round1((double) completed * 100d / nonCancelled);
        double regPerEvent = totalEvents == 0 ? 0d : round1((double) totalRegs / totalEvents);
        data.put("completionRate", completionRate);
        data.put("registrationPerEvent", regPerEvent);

        // ---- 活跃用户（30 天）----
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();
        // 报名维度
        List<Registration> recentRegs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                        .ge(Registration::getCreatedAt, thirtyDaysAgo));
        // 赛事创建维度（管理员创建赛事）
        List<Event> recentEvents = eventMapper.selectList(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).ge(Event::getCreatedAt, thirtyDaysAgo));
        long activeUsers = recentRegs.stream().map(Registration::getUserId).distinct().count()
                + recentEvents.stream().map(Event::getCreatedBy).filter(java.util.Objects::nonNull).distinct().count();
        data.put("activeUsersLast30d", activeUsers);

        // ---- 12 个月趋势 ----
        LocalDate monthAnchor = LocalDate.now().withDayOfMonth(1);
        List<Map<String, Object>> byMonth = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            LocalDate monthStart = monthAnchor.minusMonths(i);
            LocalDateTime start = monthStart.atStartOfDay();
            LocalDateTime end = monthStart.plusMonths(1).atStartOfDay();

            long users = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().ge(User::getCreatedAt, start).lt(User::getCreatedAt, end));
            long events = eventMapper.selectCount(
                    new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt)
                            .ge(Event::getCreatedAt, start).lt(Event::getCreatedAt, end));
            long regs = registrationMapper.selectCount(
                    new LambdaQueryWrapper<Registration>()
                            .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                            .ge(Registration::getCreatedAt, start).lt(Registration::getCreatedAt, end));

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", String.format("%d-%02d", monthStart.getYear(), monthStart.getMonthValue()));
            point.put("users", users);
            point.put("events", events);
            point.put("registrations", regs);
            byMonth.add(point);
        }
        data.put("byMonth", byMonth);

        // ---- 赛事类型分布（复用 dashboard 字段）----
        long starEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "STAR"));
        long partyEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "PARTY"));
        Map<String, Long> eventTypes = new LinkedHashMap<>();
        eventTypes.put("STAR", starEvents);
        eventTypes.put("PARTY", partyEvents);
        data.put("eventTypes", eventTypes);

        return Result.ok(data);
    }

    // ====================== helpers ======================

    private static int dayIndex(LocalDate today, LocalDate date, int days) {
        long diff = today.toEpochDay() - date.toEpochDay();
        if (diff < 0 || diff >= days) return -1;
        return (int) diff;
    }

    private static Map<String, Object> point(String date, long count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("count", count);
        return m;
    }

    private static Map<String, Object> ratePoint(String date, double rate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("rate", rate);
        return m;
    }

    private static double round1(double v) {
        return Math.round(v * 10d) / 10d;
    }
}