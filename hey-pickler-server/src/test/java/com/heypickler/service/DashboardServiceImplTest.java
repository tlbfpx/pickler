package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.UserMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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

    // ---------- R2 trends ----------
    @Test
    void getTrends_7dReturns7Buckets_evenWhenDataSparse() {
        // GROUP BY 只返回有数据的日期，service 端按天 0 填值
        when(userMapper.dailyNewUsers(any(), any())).thenReturn(List.of(row(new LocalDateSim(2026, 7, 17), 5L)));
        when(registrationMapper.dailyRegistrations(any(), any())).thenReturn(List.of(row(new LocalDateSim(2026, 7, 18), 3L)));
        when(eventMapper.dailyNewEvents(any(), any())).thenReturn(List.of());
        when(registrationMapper.revenueInRange(any(), any())).thenReturn(new BigDecimal("0"));

        DashboardTrendVO vo = service.getTrends("7d", null, null);
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

        DashboardTrendVO vo = service.getTrends("custom", "2026-07-01", "2026-07-10");
        // 半开区间 7/01..7/10 → 10 桶（half-open: 7/01..7/11 exclusive）
        assertEquals(10, vo.getBuckets().size());
    }

    @Test
    void getTrends_rejectsCustomRangeWithoutFromTo() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTrends("custom", null, null));
    }

    // ---------- R3 top-events ----------
    @Test
    void getTopEvents_registrationsSortedByCountDesc() {
        Map<String, Object> r1 = topRow(1L, "A", 10, 10, 50L);
        Map<String, Object> r2 = topRow(2L, "B", 5, 5, 30L);
        when(registrationMapper.topEventsByRegistrations(any(), any(), eq(10))).thenReturn(List.of(r1, r2));

        List<TopEventVO> list = service.getTopEvents("registrations", "30d", null, null, 10);
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

        List<TopEventVO> list = service.getTopEvents("fillRate", "30d", null, null, 10);
        assertEquals(1, list.size(), "maxParticipants=0 必须被排除");
        assertEquals("Max10", list.get(0).getTitle());
        assertEquals(0.5d, list.get(0).getValue(), 0.01);
    }

    @Test
    void getTopEvents_rejectsInvalidLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getTopEvents("registrations", "30d", null, null, 51));
        assertThrows(IllegalArgumentException.class,
                () -> service.getTopEvents("registrations", "30d", null, null, 0));
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

        AttendanceFunnelVO vo = service.getAttendance("30d", null, null);
        assertEquals(100, vo.getRegistered());
        assertEquals(80, vo.getCheckedIn());
        assertEquals(0.2, vo.getNoShowRate(), 0.001);
    }

    @Test
    void getAttendance_zeroRegistrationsReturnsNullRate() {
        when(registrationMapper.countActiveInRange(any(), any())).thenReturn(0L);
        when(registrationMapper.countCheckedInInRange(any(), any())).thenReturn(0L);

        AttendanceFunnelVO vo = service.getAttendance("30d", null, null);
        assertEquals(0, vo.getRegistered());
        assertNull(vo.getNoShowRate(), "registered=0 时 noShowRate 必须为 null（spec R4 边界）");
    }

    // ---------- R5 compare ----------
    @Test
    void getCompare_normalMonthOverMonthHasDeltaPct() {
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(120L)   // thisMonth (resolveWindow 内顺序：currentRange 先)
                .thenReturn(100L); // lastMonth

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth");
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

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth");
        assertNull(vo.getDeltaPct(), "previous=0 必须 deltaPct=null 避免除零");
        assertEquals(50, vo.getDeltaAbs(), 0.001);
    }

    @Test
    void getCompare_bothZero_pctNull() {
        when(registrationMapper.countActiveInRange(any(), any()))
                .thenReturn(0L).thenReturn(0L);

        CompareResultVO vo = service.getCompare("registrations", "thisMonth", "lastMonth");
        assertEquals(0, vo.getCurrent(), 0.001);
        assertEquals(0, vo.getPrevious(), 0.001);
        assertEquals(0, vo.getDeltaAbs(), 0.001);
        assertNull(vo.getDeltaPct());
    }

    @Test
    void getCompare_invalidMetricThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getCompare("nonsense", "thisMonth", "lastMonth"));
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

        var data = service.getSnapshot();
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
}
