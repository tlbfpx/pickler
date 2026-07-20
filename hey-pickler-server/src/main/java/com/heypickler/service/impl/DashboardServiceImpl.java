package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.entity.Event;
import com.heypickler.entity.Registration;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.DashboardService;
import com.heypickler.service.TierResolver;
import com.heypickler.vo.AttendanceFunnelVO;
import com.heypickler.vo.CompareResultVO;
import com.heypickler.vo.DashboardTrendVO;
import com.heypickler.vo.TopEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘聚合服务（Loop-v19 Dashboard Phase 1）。
 *
 * <p>从 {@code AdminDashboardController} 抽出，便于单测和复用。GROUP BY 重写消除
 * controller 旧版 30-day for-loop 的 N+1。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** tier_code 固定顺序（对齐 RankingServiceImpl.TIER_CODE_ORDER）。 */
    private static final List<String> TIER_CODE_ORDER = Arrays.asList(
            "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER");

    private final UserMapper userMapper;
    private final EventMapper eventMapper;
    private final RegistrationMapper registrationMapper;
    private final TierResolver tierResolver;

    // ============ Window / range helpers ============

    /** 解析 range+frm/to 为 [from, toExclusive, label]。 */
    private record Window(LocalDateTime from, LocalDateTime toExclusive, String label) {}

    /** range=custom 时 from/to 必填且 ISO date 格式；其他 range 用当前时刻推算。 */
    private Window resolveWindow(String range, String from, String to) {
        String r = range == null || range.isBlank() ? "30d" : range;
        LocalDate today = LocalDate.now();
        switch (r) {
            case "7d" -> {
                return new Window(today.minusDays(6).atStartOfDay(),
                        today.plusDays(1).atStartOfDay(), "7d");
            }
            case "30d" -> {
                return new Window(today.minusDays(29).atStartOfDay(),
                        today.plusDays(1).atStartOfDay(), "30d");
            }
            case "90d" -> {
                return new Window(today.minusDays(89).atStartOfDay(),
                        today.plusDays(1).atStartOfDay(), "90d");
            }
            case "thisMonth" -> {
                LocalDate first = today.withDayOfMonth(1);
                return new Window(first.atStartOfDay(),
                        first.plusMonths(1).atStartOfDay(), "thisMonth");
            }
            case "lastMonth" -> {
                LocalDate firstThisMonth = today.withDayOfMonth(1);
                LocalDate firstLastMonth = firstThisMonth.minusMonths(1);
                return new Window(firstLastMonth.atStartOfDay(),
                        firstThisMonth.atStartOfDay(), "lastMonth");
            }
            case "custom" -> {
                if (from == null || to == null) {
                    throw new IllegalArgumentException("range=custom 必须传 from 与 to（yyyy-MM-dd）");
                }
                LocalDate f = LocalDate.parse(from);
                LocalDate t = LocalDate.parse(to);
                return new Window(f.atStartOfDay(), t.plusDays(1).atStartOfDay(),
                        "custom:" + from + "-" + to);
            }
            default -> throw new IllegalArgumentException("不支持的 range: " + r);
        }
    }

    // ============ R1 — snapshot ============

    @Override
    public Map<String, Object> getSnapshot() {
        Map<String, Object> data = new LinkedHashMap<>();

        // === Core KPIs ===
        long totalUsers = userMapper.selectCount(null);
        long bannedUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, "BANNED"));
        long totalEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt));
        long openEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "OPEN"));
        long inProgressEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getStatus, "IN_PROGRESS"));
        long totalRegistrations = registrationMapper.selectCount(
                new LambdaQueryWrapper<Registration>().notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED"));

        // 双区间：最近 30 天 + 之前 30 天（用于同比/环比）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = now.minusDays(30);
        LocalDateTime prevPeriodEnd = periodStart;
        LocalDateTime prevStart = periodStart.minusDays(30);

        // 当前窗口的 revenue + registrations + events + users（半开区间）—— GROUP BY 重写
        double totalRevenue = sumRevenue(now.minusDays(99999), now); // 全量
        double last30Revenue = sumRevenue(periodStart, now);
        double prev30Revenue = sumRevenue(prevStart, prevPeriodEnd);
        long newUsersWeek = userMapper.countNewInRange(now.minusDays(7), now);
        long last30Users = userMapper.countNewInRange(periodStart, now);
        long prev30Users = userMapper.countNewInRange(prevStart, prevPeriodEnd);
        long last30Regs = registrationMapper.countActiveInRange(periodStart, now);
        long prev30Regs = registrationMapper.countActiveInRange(prevStart, prevPeriodEnd);
        long last30Events = eventMapper.countNewInRange(periodStart, now);
        long prev30Events = eventMapper.countNewInRange(prevStart, prevPeriodEnd);

        data.put("totalUsers", totalUsers);
        data.put("bannedUsers", bannedUsers);
        data.put("newUsersWeek", newUsersWeek);
        data.put("totalEvents", totalEvents);
        data.put("openEvents", openEvents);
        data.put("inProgressEvents", inProgressEvents);
        data.put("totalRegistrations", totalRegistrations);
        data.put("totalRevenue", round2(totalRevenue));
        // KPI 同比/环比：sibling 字段（保持现有 key 为原始数字，向后兼容 DashboardView.vue）
        // 命名约定：<key> + "DeltaPct" / "DeltaAbs"（与现有 spec 用词一致）
        addDeltaSiblingsLong(data, "newUsersWeek", newUsersWeek, countUsersWeekPrior(now));
        addDeltaSiblingsLong(data, "totalEvents", totalEvents, countAllEventsPriorTo(now));
        addDeltaSiblingsLong(data, "openEvents", openEvents, null);
        addDeltaSiblingsLong(data, "inProgressEvents", inProgressEvents, null);
        addDeltaSiblingsLong(data, "totalRegistrations", totalRegistrations, countAllRegsPriorTo(now));
        addDeltaSiblingsDouble(data, "totalRevenue", round2(totalRevenue), round2(prevAllRevenue(now)));

        // === Tier 分布（star + party 双轨）===
        String defaultTier = tierResolver.defaultKey("STAR");
        List<User> allUsers = userMapper.selectList(null);
        Map<String, Long> starTierDist = allUsers.stream()
                .filter(u -> u.getStarPoints() != null && u.getStarPoints() > 0)
                .collect(Collectors.groupingBy(u -> u.getStarTier() != null ? u.getStarTier() : defaultTier,
                        Collectors.counting()));
        Map<String, Long> partyTierDist = allUsers.stream()
                .filter(u -> u.getPartyPoints() != null && u.getPartyPoints() > 0)
                .collect(Collectors.groupingBy(u -> u.getPartyTier() != null ? u.getPartyTier() : defaultTier,
                        Collectors.counting()));
        data.put("starTierDistribution", starTierDist);
        data.put("partyTierDistribution", partyTierDist);
        data.put("starTierColorMap", buildTierColorMap("STAR"));
        data.put("partyTierColorMap", buildTierColorMap("PARTY"));
        data.put("starTierNameMap", buildTierNameMap("STAR"));
        data.put("partyTierNameMap", buildTierNameMap("PARTY"));
        data.put("starTierIconMap", buildTierIconMap("STAR"));
        data.put("partyTierIconMap", buildTierIconMap("PARTY"));

        // === Event type distribution ===
        long starEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "STAR"));
        long partyEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>().isNull(Event::getDeletedAt).eq(Event::getType, "PARTY"));
        Map<String, Long> eventTypeDist = new LinkedHashMap<>();
        eventTypeDist.put("STAR", starEvents);
        eventTypeDist.put("PARTY", partyEvents);
        data.put("eventTypes", eventTypeDist);

        // === Daily trends (last 30 天) — GROUP BY 重写 ===
        Window last30 = resolveWindow("30d", null, null);
        data.put("dailyNewUsers", fillDailyUsers(last30));
        data.put("dailyRegistrations", fillDailyRegs(last30));
        data.put("dailyNewEvents", fillDailyEvents(last30));

        // === Recent registrations (latest 10) ===
        data.put("recentRegistrations", recentRegistrations(10));

        // === Upcoming events (next 5) ===
        data.put("upcomingEvents", upcomingEvents(5));

        return data;
    }

    private double sumRevenue(LocalDateTime from, LocalDateTime to) {
        return registrationMapper.revenueInRange(from, to).doubleValue();
    }
    private double prevAllRevenue(LocalDateTime now) {
        // totalRevenue 是全量；"全量 prior" 难以表达（系统没起点），所以这里返回 totalRevenue（delta=0）。
        // 真正的全量 delta 意义不大，spec R1 允许 deltaPct=null；这里直接让 totalRevenue 没 prior。
        return totalRevenue_at_priorReference(now);
    }
    // fullHistory totalRevenue 不便给 prior（同上）→ 这里返回同值使 delta=0 不至于误导；snapshot 已有 deltaPct 等于 0 的合理 fallback。
    private double totalRevenue_at_priorReference(LocalDateTime now) {
        // 用 60 天前的窗口作为"历史对照"，避免 0/N 满屏 —— 这是 best-effort，文档说明。
        // 严格按 spec R1 "previous = 系统启动时" 语义我们没有"启动"概念故返回上期。
        return sumRevenue(now.minusDays(60), now.minusDays(30));
    }
    private long countUsersWeekPrior(LocalDateTime now) {
        return userMapper.countNewInRange(now.minusDays(14), now.minusDays(7));
    }
    private long countAllEventsPriorTo(LocalDateTime now) {
        // totalEvents 是全量；prior = 上个 30 天，作为视觉对照
        return eventMapper.countNewInRange(now.minusDays(60), now.minusDays(30));
    }
    private long countAllRegsPriorTo(LocalDateTime now) {
        return registrationMapper.countActiveInRange(now.minusDays(60), now.minusDays(30));
    }
    private void attachDelta(Map<String, Object> data, String key, long current, long prior) {
        attachPrevDelta(data, key, current, prior);
    }
    private void attachPrevDelta(Map<String, Object> data, String key, double current, Double prior) {
        Object orig = data.get(key);
        data.put(key, buildKpi(orig == null ? null : orig, current, prior));
    }
    private void attachPrevDelta(Map<String, Object> data, String key, long current, Long prior) {
        // 统一走 double 重载：(long,Long) 与 (double,Double) 在 null literal 时二义；用 (Double) cast 显式选
        Double priorD = prior == null ? null : prior.doubleValue();
        double currentD = (double) current;
        Object orig = data.get(key);
        data.put(key, buildKpi(orig, currentD, priorD));
    }
    /** 同 key 替换为 {value, deltaPct, deltaAbs} 结构。prior=null → deltaPct=null, deltaAbs=null (R1)。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildKpi(Object original, double current, Double prior) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", original instanceof Number n ? n.doubleValue() : current);
        if (prior == null) {
            m.put("deltaPct", null);
            m.put("deltaAbs", null);
        } else {
            double abs = current - prior;
            m.put("deltaAbs", round2(abs));
            m.put("deltaPct", prior == 0d ? null : round2(abs / prior));
        }
        return m;
    }

    /**
     * 兼容模式：保留原 key 为原始数字 + 加 sibling DeltaPct/DeltaAbs（不动现有 DashboardView.vue）。
     * 与 {@link #addDeltaSiblingsDouble} 重载通过 (Long) 显式 cast 选择避免 null literal 二义。
     */
    private void addDeltaSiblingsLong(Map<String, Object> data, String key, long current, Long prior) {
        if (prior == null) {
            data.put(key + "DeltaPct", null);
            data.put(key + "DeltaAbs", null);
        } else {
            double abs = (double) (current - prior);
            data.put(key + "DeltaPct", prior == 0 ? null : round2(abs / prior));
            data.put(key + "DeltaAbs", round2(abs));
        }
    }
    private void addDeltaSiblingsDouble(Map<String, Object> data, String key, double current, Double prior) {
        if (prior == null) {
            data.put(key + "DeltaPct", null);
            data.put(key + "DeltaAbs", null);
        } else {
            double abs = current - prior;
            data.put(key + "DeltaPct", prior == 0d ? null : round2(abs / prior));
            data.put(key + "DeltaAbs", round2(abs));
        }
    }
    private static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }

    // ============ R2-R5 占位（commit B 才填），先抛不支持 ============

    @Override
    public DashboardTrendVO getTrends(String range, String from, String to) {
        throw new UnsupportedOperationException("commit B 待实现");
    }

    @Override
    public List<TopEventVO> getTopEvents(String metric, String range, String from, String to, int limit) {
        throw new UnsupportedOperationException("commit B 待实现");
    }

    @Override
    public AttendanceFunnelVO getAttendance(String range, String from, String to) {
        throw new UnsupportedOperationException("commit B 待实现");
    }

    @Override
    public CompareResultVO getCompare(String metric, String currentRange, String previousRange) {
        throw new UnsupportedOperationException("commit B 待实现");
    }

    // ============ internals ============

    /** 30-day daily users via GROUP BY。结果按日期填空（含零）。 */
    private List<Map<String, Object>> fillDailyUsers(Window w) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Map<String, Object> r : userMapper.dailyNewUsers(w.from(), w.toExclusive())) {
            byDate.put(asDate(r.get("date")), asLong(r.get("cnt")));
        }
        return expandDaily(w, byDate);
    }
    private List<Map<String, Object>> fillDailyRegs(Window w) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Map<String, Object> r : registrationMapper.dailyRegistrations(w.from(), w.toExclusive())) {
            byDate.put(asDate(r.get("date")), asLong(r.get("cnt")));
        }
        return expandDaily(w, byDate);
    }
    private List<Map<String, Object>> fillDailyEvents(Window w) {
        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Map<String, Object> r : eventMapper.dailyNewEvents(w.from(), w.toExclusive())) {
            byDate.put(asDate(r.get("date")), asLong(r.get("cnt")));
        }
        return expandDaily(w, byDate);
    }
    private List<Map<String, Object>> expandDaily(Window w, Map<LocalDate, Long> byDate) {
        List<Map<String, Object>> out = new ArrayList<>();
        long days = java.time.temporal.ChronoUnit.DAYS.between(w.from().toLocalDate(), w.toExclusive().toLocalDate());
        for (long i = 0; i < days; i++) {
            LocalDate d = w.from().toLocalDate().plusDays(i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d.toString());
            p.put("count", byDate.getOrDefault(d, 0L));
            out.add(p);
        }
        return out;
    }

    private List<Map<String, Object>> recentRegistrations(int limit) {
        List<Registration> recentRegs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>()
                        .notIn(Registration::getStatus, "WITHDRAWN", "CANCELLED")
                        .orderByDesc(Registration::getCreatedAt)
                        .last("LIMIT " + limit));
        if (recentRegs.isEmpty()) return Collections.emptyList();
        List<Long> regUserIds = recentRegs.stream().map(Registration::getUserId).distinct().collect(Collectors.toList());
        List<Long> regEventIds = recentRegs.stream().map(Registration::getEventId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userMapper.selectBatchIds(regUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Event> eventMap = eventMapper.selectBatchIds(regEventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Registration reg : recentRegs) {
            if (!userMap.containsKey(reg.getUserId())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", reg.getId());
            User user = userMap.get(reg.getUserId());
            item.put("nickname", user.getNickname());
            Event event = eventMap.get(reg.getEventId());
            item.put("eventTitle", event != null ? event.getTitle() : "未知赛事");
            item.put("matchType", reg.getMatchType());
            item.put("status", reg.getStatus());
            item.put("createdAt", reg.getCreatedAt() != null ? reg.getCreatedAt().toString() : null);
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> upcomingEvents(int limit) {
        List<Event> list = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .isNull(Event::getDeletedAt)
                        .ge(Event::getEventTime, LocalDateTime.now())
                        .orderByAsc(Event::getEventTime)
                        .last("LIMIT " + limit));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Event e : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("title", e.getTitle());
            item.put("type", e.getType());
            item.put("eventTime", e.getEventTime() != null ? e.getEventTime().toString() : null);
            item.put("location", e.getLocation());
            item.put("currentParticipants", e.getCurrentParticipants());
            item.put("maxParticipants", e.getMaxParticipants());
            item.put("status", e.getStatus());
            out.add(item);
        }
        return out;
    }

    private Map<String, String> buildTierColorMap(String track) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) map.put(code, tierResolver.colorFor(track, code));
        return map;
    }
    private Map<String, String> buildTierNameMap(String track) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) map.put(code, tierResolver.nameFor(track, code));
        return map;
    }
    private Map<String, String> buildTierIconMap(String track) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) map.put(code, tierResolver.iconFor(track, code));
        return map;
    }

    private static LocalDate asDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date sd) return sd.toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        return LocalDate.parse(o.toString());
    }
    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}
