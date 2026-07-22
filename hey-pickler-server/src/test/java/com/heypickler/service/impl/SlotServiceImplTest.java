package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.entity.BookingSlot;
import com.heypickler.entity.Court;
import com.heypickler.entity.CourtPricingBand;
import com.heypickler.entity.Venue;
import com.heypickler.entity.VenueBusinessHour;
import com.heypickler.mapper.BookingSlotMapper;
import com.heypickler.mapper.CourtMapper;
import com.heypickler.mapper.CourtPricingBandMapper;
import com.heypickler.mapper.VenueBusinessHourMapper;
import com.heypickler.mapper.VenueMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SlotService 把库数据组装好委派给纯 {@link SlotCalculator}。本测试用真实 calculator
 * (spy 注入),锁定三个分支:
 * <ol>
 *   <li>closed day(bh.openTime=null)→ 空</li>
 *   <li>court 非 OPEN → 抛 COURT_NOT_AVAILABLE</li>
 *   <li>OPEN court + band 09:00-11:00 + 未来工作日 → 返回 calculator 的结果数</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotServiceImplTest {

    @Mock CourtMapper courtMapper;
    @Mock VenueMapper venueMapper;
    @Mock VenueBusinessHourMapper businessHourMapper;
    @Mock CourtPricingBandMapper bandMapper;
    @Mock BookingSlotMapper bookingSlotMapper;

    /** 纯算法——直接用真实实例,不必 mock。 */
    @Spy
    private final SlotCalculator calculator = new SlotCalculator();

    @InjectMocks SlotServiceImpl service;

    /**
     * 5 个实体都在 LambdaQueryWrapper 路径上 → 必须预热 TableInfo,否则 "can not find lambda cache"。
     * (Court 走 selectById 不需要预热,但多注册一个也无害。)
     */
    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Court.class, Venue.class, VenueBusinessHour.class,
                CourtPricingBand.class, BookingSlot.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    private Court court(Long id, String status, Long venueId, int slotMinutes) {
        Court c = new Court();
        c.setId(id);
        c.setStatus(status);
        c.setVenueId(venueId);
        c.setSlotMinutes(slotMinutes);
        return c;
    }

    private Venue venue(Long id, int leadDays) {
        Venue v = new Venue();
        v.setId(id);
        v.setBookingLeadDays(leadDays);
        return v;
    }

    private VenueBusinessHour bh(Long venueId, int dow, LocalTime open, LocalTime close) {
        VenueBusinessHour b = new VenueBusinessHour();
        b.setVenueId(venueId);
        b.setDayOfWeek(dow);
        b.setOpenTime(open);
        b.setCloseTime(close);
        return b;
    }

    private CourtPricingBand band(String dayType, String s, String e, int price) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType(dayType);
        b.setStartTime(LocalTime.parse(s));
        b.setEndTime(LocalTime.parse(e));
        b.setPrice(new BigDecimal(price));
        return b;
    }

    @Test
    void getCourtSlots_closedDay_returnsEmpty() {
        // 当日休:openTime=null → calculator 立即返回空
        Long courtId = 1L, venueId = 10L;
        when(courtMapper.selectById(courtId)).thenReturn(court(courtId, "OPEN", venueId, 60));
        when(venueMapper.selectById(venueId)).thenReturn(venue(venueId, 14));
        // 2026-07-22 是周三 → schemaDow = 3
        when(businessHourMapper.selectOne(any())).thenReturn(bh(venueId, 3, null, null));
        when(bandMapper.selectList(any())).thenReturn(List.of());
        when(bookingSlotMapper.selectList(any())).thenReturn(List.of());

        var r = service.getCourtSlots(courtId, LocalDate.of(2026, 7, 22));
        assertTrue(r.isEmpty());
    }

    @Test
    void getCourtSlots_courtNotOpen_throwsCourtNotAvailable() {
        Long courtId = 2L;
        when(courtMapper.selectById(courtId)).thenReturn(court(courtId, "MAINTENANCE", 10L, 60));

        BizException ex = assertThrows(BizException.class,
                () -> service.getCourtSlots(courtId, LocalDate.of(2026, 7, 22)));
        assertEquals(ErrorCode.COURT_NOT_AVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void getCourtSlots_openCourtWithBand_returnsCalculatorSlotCount() {
        Long courtId = 3L, venueId = 10L;
        when(courtMapper.selectById(courtId)).thenReturn(court(courtId, "OPEN", venueId, 60));
        when(venueMapper.selectById(venueId)).thenReturn(venue(venueId, 14));
        // today=2026-07-22 (本机时钟固定日), leadDays=14 → 窗口到 2026-08-05。
        // 选 2026-07-23 周四 (schemaDow=4) 落在窗内,避开 now+30min 与 now+14d 上下界。
        LocalDate date = LocalDate.of(2026, 7, 23);
        when(businessHourMapper.selectOne(any())).thenReturn(bh(venueId, 4, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        when(bandMapper.selectList(any())).thenReturn(List.of(band("WEEKDAY", "09:00", "11:00", 40)));
        when(bookingSlotMapper.selectList(any())).thenReturn(List.of());

        var r = service.getCourtSlots(courtId, date);
        // calculator 应产出 09:00 + 10:00 两格
        assertEquals(2, r.size());
        assertEquals(40, r.get(0).getPrice().intValueExact());
        assertTrue(r.get(0).isAvailable());
        // 第一格锚定 09:00
        assertEquals(date.atTime(9, 0), r.get(0).getStart());
    }
}
