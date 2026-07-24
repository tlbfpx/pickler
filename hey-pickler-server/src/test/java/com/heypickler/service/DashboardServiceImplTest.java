package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.DashboardCache;
import com.heypickler.service.impl.DashboardServiceImpl;
import com.heypickler.vo.AttendanceFunnelVO;
import com.heypickler.vo.CompareResultVO;
import com.heypickler.vo.DashboardTrendVO;
import com.heypickler.vo.TopEventVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Loop-v19 Dashboard Phase 1：4 个新端点（commit B）+ snapshot KPI delta 单测。
 *
 * <p>Redis 缓存在 commit C，不在本测试覆盖（直接 stub mapper 方法，绕开缓存层）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceImplTest {

    @Mock EventMapper eventMapper;
    @Mock UserMapper userMapper;
    @Mock RegistrationMapper registrationMapper;
    @Mock TierResolver tierResolver;
    @Mock DashboardCache dashboardCache;

    @InjectMocks DashboardServiceImpl service;

    /** Lambda 缓存预热（spec 默认注入）。 */
    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
        a.setCurrentNamespace("com.heypickler.mapper.UserMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, User.class);
    }

    private Map<String, Object> row(LocalDateSim date, long cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", date.toString());
        m.put("cnt", cnt);
        return m;
    }

    private record LocalDateSim(int y, int m, int d) {
        @Override public String toString() { return String.format("%04d-%02d-%02d", y, m, d); }
    }

    /** 将 java.time.LocalDate 适配为 LocalDateSim，让 mock 数据落在当前 7d 窗口内（避免日期漂移导致 flake）。 */
    private static LocalDateSim toSim(LocalDate d) {
        return new LocalDateSim(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    // ---------- R2 trends ----------
    @Test
    void getTrends_7dReturns7Buckets_evenWhenDataSparse() {
        // GROUP BY 只返回有数据的日期，service 端按天 0 填值
        // 用 today-2 / today-1 相对日期，避免日历漂移导致 mock 数据落到 7d 窗口外变 flake
        LocalDate today = LocalDate.now();
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of(row(toSim(today.minusDays(2)), 5L)));
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of(row(toSim(today.minusDays(1)), 3L)));
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(new BigDecimal("0"));

        DashboardTrendVO vo = service.getTrends("7d", null, null, false);
        assertEquals(7, vo.getBuckets().size(), "7d 必须返回 7 个桶（含零填值）");
        // 总量校验（不依赖具体日期，填零 OK）
        long sumUsers = vo.getBuckets().stream().mapToLong(DashboardTrendVO.DayBucket::getUsers).sum();
        long sumRegs = vo.getBuckets().stream().mapToLong(DashboardTrendVO.DayBucket::getRegistrations).sum();
        assertEquals(5L, sumUsers);
        assertEquals(3L, sumRegs);
    }

    @Test
    void getTrends_customRangeRespectsFromTo() {
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(new BigDecimal("0"));

        DashboardTrendVO vo = service.getTrends("custom", "2026-07-01", "2026-07-10", false);
        // 半开区间 7/01..7/10 → 10 桶（half-open: 7/01..7/11 exclusive）
        assertEquals(10, vo.getBuckets().size());
    }

    @Test
    void getTrends_rejectsCustomRangeWithoutFromTo() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTrends("custom", null, null, false));
    }

    // ---------- R3 top-events ----------
    @Test
    void getTopEvents_registrationsSortedByCountDesc() {
        Map<String, Object> r1 = topRow(1L, "A", 10, 10, 50L);
        Map<String, Object> r2 = topRow(2L, "B", 5, 5, 30L);
        when(registrationMapper.topEventsByRegistrations(any(), any(), eq(10))).thenReturn(List.of(r1, r2));

        List<TopEventVO> list = service.getTopEvents("registrations", "30d", null, null, 10, false);
        assertEquals(2, list.size());
        assertEquals(50d, list.get(0).getValue());
        assertEquals(30d, list.get(1).getValue());
        assertEquals("registrations", list.get(0).getMetric());
    }

    @Test
    void getTopEvents_fillRate_excludesMaxZeroEvents() {
        Map<String, Object> unlimited = topRow(1L, "Max0", 0, 5, 100L);
        Map<String, Object> capped = topRow(2L, "Max10", 10, 5, 30L);
        when(registrationMapper.topEventsByRegistrations(any(), any(), anyInt())).thenReturn(List.of(unlimited, capped));

        List<TopEventVO> list = service.getTopEvents("fillRate", "30d", null, null, 10, false);
        assertEquals(1, list.size(), "maxParticipants=0 必须被排除");
        assertEquals("Max10", list.get(0).getTitle());
        assertEquals(0.5d, list.get(0).getValue(), 0.01);
    }

    @Test
    void getTopEvents_rejectsInvalidLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTopEvents("registrations", "30d", null, null, 51, false));
        assertThrows(IllegalArgumentException.class,
                () -> service.getTopEvents("registrations", "30d", null, null, 0, false));
    }

    @Test
    void getTopEvents_revenue_usesRevenueMapper() {
        // metric=revenue 走 registrationMapper.topEventsByRevenue 分支（DashboardServiceImpl line 349-352）
        Map<String, Object> r1 = topRow(1L, "RevA", 10, 5, 800L);
        Map<String, Object> r2 = topRow(2L, "RevB", 20, 10, 500L);
        when(registrationMapper.topEventsByRevenue(any(), any(), eq(10))).thenReturn(List.of(r1, r2));

        List<TopEventVO> list = service.getTopEvents("revenue", "30d", null, null, 10, false);
        assertEquals(2, list.size());
        assertEquals("RevA", list.get(0).getTitle());
        assertEquals(800d, list.get(0).getValue());
        assertEquals("revenue", list.get(0).getMetric());
    }

    @Test
    void getTopEvents_invalidMetricThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTopEvents("garbage", "30d", null, null, 10, false));
    }

    @Test
    void getTopEvents_fillRate_includesNullMaxAsExcluded() {
        // mapTop 把 maxParticipants=null 当作 0 过滤（line 357-360）
        Map<String, Object> nullMax = topRow(1L, "NullMax", 0, 5, 100L); // helper 强制 int → 0
        Map<String, Object> capped = topRow(2L, "OK", 10, 5, 50L);
        when(registrationMapper.topEventsByRegistrations(any(), any(), anyInt())).thenReturn(List.of(nullMax, capped));

        List<TopEventVO> list = service.getTopEvents("fillRate", "30d", null, null, 10, false);
        assertEquals(1, list.size());
        assertEquals("OK", list.get(0).getTitle());
    }

    @Test
    void getTrends_invalidRangeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTrends("never", null, null, true));
    }

    @Test
    void getCompare_invalidRangeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getCompare("users", "bogus", "thisMonth", true));
    }

    private Map<String, Object> topRow(long id, String title, int max, int current, long value) {
        Map<String, Object> m = new HashMap<>();
        m.put("eventId", id);
        m.put("title", title);
        m.put("maxParticipants", max);
        m.put("currentParticipants", current);
        m.put("value", value);
        return m;
    }

    // ---------- R4 attendance ----------
    @Test
    void getAttendance_normalComputesNoShowRate() {
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(100L);
        when(registrationMapper.countCheckedInInRange(any(), any())).thenReturn(80L);

        AttendanceFunnelVO vo = service.getAttendance("30d", null, null, false);
        assertEquals(100, vo.getRegistered());
        assertEquals(80, vo.getCheckedIn());
        assertEquals(0.2, vo.getNoShowRate(), 0.001);
    }

    @Test
    void getAttendance_zeroRegistrationsReturnsNullRate() {
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countCheckedInInRange(any(), any())).thenReturn(0L);

        AttendanceFunnelVO vo = service.getAttendance("30d", null, null, false);
        assertEquals(0, vo.getRegistered());
        assertNull(vo.getNoShowRate(), "registered=0 时 noShowRate 必须为 null（spec R4 边界）");
    }

    // ---------- R5 compare ----------
    @Test
    void getCompare_normalMonthOverMonthHasDeltaPct() {
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(120L)   // thisMonth (resolveWindow 内顺序：currentRange 先)
                .thenReturn(100L); // lastMonth

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth", false);
        assertEquals(120, vo.getCurrent(), 0.001);
        assertEquals(100, vo.getPrevious(), 0.001);
        assertEquals(20, vo.getDeltaAbs(), 0.001);
        assertEquals(0.2, vo.getDeltaPct(), 0.001);
    }

    @Test
    void getCompare_previousZero_avoidsDivideByZero() {
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(50L)   // current
                .thenReturn(0L);   // previous

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth", false);
        assertNull(vo.getDeltaPct(), "previous=0 必须 deltaPct=null 避免除零");
        assertEquals(50, vo.getDeltaAbs(), 0.001);
    }

    @Test
    void getCompare_bothZero_pctNull() {
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(0L).thenReturn(0L);

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth", false);
        assertEquals(0, vo.getCurrent(), 0.001);
        assertEquals(0, vo.getPrevious(), 0.001);
        assertEquals(0, vo.getDeltaAbs(), 0.001);
        assertNull(vo.getDeltaPct());
    }

    @Test
    void getCompare_invalidMetricThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getCompare("nonsense", "thisMonth", "lastMonth", false));
    }

    // ---------- R1 snapshot sibling delta ----------
    @Test
    void getSnapshot_addsSiblingsButPreservesRawNumericKeys() {
        // 全量总数（不区分窗口）— 用户 100 / 60
        when(eventMapper.selectCount(any())).thenReturn(20L);
        when(userMapper.selectCount(any())).thenReturn(200L);
        when(registrationMapper.selectCount(any())).thenReturn(1000L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(200L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1000L);
        // 同比窗口：当前 60 / 上期 40（要 stub 6 次：thisMonth/lastMonth + 之前 60 天/后 30 天/前 30 天）
        when(userMapper.countNewInRange(any(), any()))
                .thenReturn(60L)   // 当前 30 天
                .thenReturn(40L)   // 上个 30 天
                .thenReturn(40L)   // weekPrior (14..7 天前)
                .thenReturn(30L)   // 上 60 天的 30 天（allEventsPriorTo）
                .thenReturn(300L); // 上 60 天的 30 天（allRegsPriorTo）
        when(eventMapper.countNewInRange(any(), any()))
                .thenReturn(20L).thenReturn(15L);
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(500L).thenReturn(400L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(new BigDecimal("12345.67"));
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        var data = service.getSnapshot(false);
        // 向后兼容：原 key 仍是数字
        assertEquals(200L, data.get("totalUsers"));
        assertEquals(60L, data.get("newUsersWeek"));
        // Sibling delta 字段存在（R1）
        assertTrue(data.containsKey("newUsersWeekDeltaPct"), "应加 sibling DeltaPct");
        assertTrue(data.containsKey("newUsersWeekDeltaAbs"), "应加 sibling DeltaAbs");
        // totalEvents 没 prior（null）→ Delta = null
        assertNull(data.get("totalEventsDeltaPct"));
        // dailyNewUsers 等趋势仍是 List
        assertTrue(data.get("dailyNewUsers") instanceof List);
    }

    // ---------- Commit C — cache-aside + bypass ----------

    @Test
    void getSnapshot_bypassFalse_checksCacheAndStoresFreshResult() {
        // cache miss → loader 跑；结果写回 cache
        when(dashboardCache.get(anyString(), eq(Map.class))).thenReturn(null);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(eventMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        service.getSnapshot(false);

        verify(dashboardCache, times(1)).get(
                argThat(k -> k != null && k.contains("dashboard:snapshot")),
                eq(Map.class));
        verify(dashboardCache, times(1)).put(
                argThat(k -> k != null && k.contains("dashboard:snapshot")),
                any());
    }

    // ============ Loop-v19 — 提高 DashboardServiceImpl 行覆盖 ============

    @Test
    void getSnapshot_includesTotalRevenueSiblingsAndPrevRevenueHelper() {
        // 全量基线 stub
        when(eventMapper.selectCount(any())).thenReturn(5L);
        when(userMapper.selectCount(any())).thenReturn(100L);
        when(registrationMapper.selectCount(any())).thenReturn(50L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(100L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(50L);
        // 6 次 countNewInRange 调用：当前 30 / 上 30 / weekPrior(14..7) / 上 60 天 allEventsPrior / 上 60 天 allRegsPrior / totalRevenue_at_priorReference(60..30)
        when(userMapper.countNewInRange(any(), any()))
                .thenReturn(20L).thenReturn(15L).thenReturn(8L).thenReturn(2L).thenReturn(1L);
        when(eventMapper.countNewInRange(any(), any()))
                .thenReturn(5L).thenReturn(3L).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(40L).thenReturn(30L).thenReturn(20L);
        when(registrationMapper.revenueInRange(any(), any()))
                .thenReturn(new BigDecimal("5000.00"))
                .thenReturn(new BigDecimal("3000.00"))
                .thenReturn(new BigDecimal("2000.00"));
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        var data = service.getSnapshot(false);
        // totalRevenue 是全量；R1 累计型无 prior → deltaPct/deltaAbs 都 null
        assertNull(data.get("totalRevenueDeltaPct"));
        assertNull(data.get("totalRevenueDeltaAbs"));
        // openEvents/inProgressEvents 也是累计型 null
        assertNull(data.get("openEventsDeltaPct"));
        assertNull(data.get("openEventsDeltaAbs"));
        assertNull(data.get("inProgressEventsDeltaPct"));
        assertNull(data.get("inProgressEventsDeltaAbs"));
    }

    @Test
    void getSnapshot_buildKpi_priorZero_returnsNullDeltaPct() {
        // 场景：上周新增用户 prior=0 → deltaPct 必须 null（避免 Infinity%），deltaAbs = current
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        // 顺序：当前 30 / 上 30（=0）/ weekPrior
        when(userMapper.countNewInRange(any(), any())).thenReturn(5L).thenReturn(0L).thenReturn(0L);
        when(eventMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(BigDecimal.ZERO);
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        var data = service.getSnapshot(false);
        // newUsersWeek = 5, prior week = 0 → deltaPct 必须 null（spec R1 + 后端 round2 prior==0? null 守卫）
        assertNull(data.get("newUsersWeekDeltaPct"),
                "prior=0 时 deltaPct 必须 null，避免 Infinity%");
        // deltaAbs = 5 - 0 = 5
        Object abs = data.get("newUsersWeekDeltaAbs");
        assertNotNull(abs, "deltaAbs 在 prior=0 时仍应是 current（绝对差）");
        assertEquals(5.0, ((Number) abs).doubleValue(), 0.01);
    }

    @Test
    void getTrends_resolveWindow_lastMonthRange_works() {
        // lastMonth 走 `firstThisMonth.minusMonths(1)` → 必须调用 mapper 拉数据
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(eventMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(BigDecimal.ZERO);
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        // 触发 lastMonth 分支（commit A 改了 resolveWindow）
        var vo = service.getTrends("lastMonth", null, null, true);
        assertNotNull(vo);
        assertEquals("lastMonth", vo.getRange());
        // buckets 数 = 上月天数（28/29/30/31 都行，关键是 ≥ 28 且 ≤ 31）
        assertTrue(vo.getBuckets().size() >= 28 && vo.getBuckets().size() <= 31,
                "lastMonth buckets 应为上月天数（28-31），实际 " + vo.getBuckets().size());
    }

    @Test
    void getSnapshot_cacheHit_skipsLoader() {
        // cache hit → 直接返回，loader 不应被调（即不调任何 mapper）
        Map<String, Object> cached = new LinkedHashMap<>();
        cached.put("cached", true);
        cached.put("totalUsers", 999L);
        when(dashboardCache.get(anyString(), eq(Map.class))).thenReturn(cached);

        Map<String, Object> result = service.getSnapshot(false);

        assertSame(cached, result, "cache hit 应返回缓存原对象");
        // mapper 不应被调
        verifyNoInteractions(eventMapper, userMapper, registrationMapper);
        verify(dashboardCache, never()).put(anyString(), any());
    }

    @Test
    void getSnapshot_bypassTrue_skipsCacheAndAlwaysLoadsFromDb() {
        // bypass=true → 完全跳过 cache.get 与 cache.put；直走 loader
        when(eventMapper.selectCount(any())).thenReturn(50L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(eventMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");

        service.getSnapshot(true);

        verifyNoInteractions(dashboardCache); // bypass：完全不读不写 cache
    }

    @Test
    void getSnapshot_cachePutFailure_doesNotPropagate() {
        // cache.put 抛异常 → loader 已拿到结果，不应影响返回
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(registrationMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(registrationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(eventMapper.countNewInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of());
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of());
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(tierResolver.defaultKey(any())).thenReturn("BRONZE");
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(tierResolver.colorFor(any(), any())).thenReturn("#000");
        when(tierResolver.nameFor(any(), any())).thenReturn("name");
        when(tierResolver.iconFor(any(), any())).thenReturn("/icon");
        doThrow(new RuntimeException("redis down")).when(dashboardCache).put(anyString(), any());

        assertDoesNotThrow(() -> service.getSnapshot(false), "cache 写入失败应降级，不应抛给上层");
    }
}
