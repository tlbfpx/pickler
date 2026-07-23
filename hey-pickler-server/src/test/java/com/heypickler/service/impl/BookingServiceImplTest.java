package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.config.BookingProperties;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingCreateResultVO;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {
    @Mock private BookingMapper bookingMapper;
    @Mock private BookingSlotMapper bookingSlotMapper;
    @Mock private CourtMapper courtMapper;
    @Mock private VenueMapper venueMapper;
    @Mock private VenueBusinessHourMapper businessHourMapper;
    @Mock private CourtPricingBandMapper bandMapper;
    @Mock private com.heypickler.mapper.UserMapper userMapper;
    @Mock private SlotCalculator calculator;
    @Mock private PricingBandValidator validator;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedis;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, String> valueOps;

    private BookingProperties props;
    private BookingServiceImpl service;
    private final Clock FIXED = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.ofHours(8));
    private final LocalDate DATE = LocalDate.of(2026, 7, 22);

    @BeforeAll
    static void warm() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Booking.class, BookingSlot.class, Court.class, Venue.class,
                                   VenueBusinessHour.class, CourtPricingBand.class,
                                   com.heypickler.entity.User.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    @BeforeEach
    void setup() {
        props = new BookingProperties();
        service = new BookingServiceImpl(
                bookingMapper, bookingSlotMapper, courtMapper, venueMapper,
                businessHourMapper, bandMapper, userMapper, calculator, validator, stringRedis, props, FIXED);
    }

    private HttpServletRequest mockReq(long userId) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("userId")).thenReturn(userId);
        return req;
    }

    private BookingCreateRequest req_create() {
        BookingCreateRequest r = new BookingCreateRequest();
        r.setCourtId(1L);
        r.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(9, 0)));
        r.setSlotsCount(2);
        return r;
    }

    /* ---------- create happy path ---------- */

    @Test
    void create_happy_writesBookingAndNBookingSlots_andUpdatesPrices() {
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));
        CourtPricingBand band = new CourtPricingBand();
        band.setDayType("WEEKDAY"); band.setStartTime(LocalTime.of(8,0));
        band.setEndTime(LocalTime.of(22,0)); band.setPrice(new BigDecimal("40"));
        // slot 09:00 + 10:00 各落入 band;occupied 空

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(band));
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        doNothing().when(validator).validate(anyList());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), true,  new BigDecimal("40"))
                ));
        when(stringRedis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        BookingCreateResultVO vo = service.create(mockReq(99L), req_create());
        assertNotNull(vo.getBookingNo());
        assertTrue(vo.getBookingNo().startsWith("BK20260722-"));
        verify(bookingMapper).insert(any(Booking.class));
        verify(bookingSlotMapper, times(2)).insert(any(BookingSlot.class));   // 2 格
    }

    /* ---------- create multi-slot band ---------------- */

    @Test
    void create_anySlotOutOfBand_throwsAndNoInsert() {
        // 第二格 unavailable(模拟无 band 价态)→ 整单拒绝,无 insert。
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), false, null) // 缺 band
                ));

        assertThrows(BizException.class, () -> service.create(mockReq(99L), req_create()));
        verify(bookingMapper, never()).insert(any());
        verify(bookingSlotMapper, never()).insert(any());
    }

    /* ---------- user concurrent limit ---------- */

    @Test
    void create_userAtLimit_throws() {
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));
        CourtPricingBand band = new CourtPricingBand();
        band.setDayType("WEEKDAY"); band.setStartTime(LocalTime.of(8,0));
        band.setEndTime(LocalTime.of(22,0)); band.setPrice(new BigDecimal("40"));

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(band));
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), true,  new BigDecimal("40"))
                ));

        // impl uses bookingMapper.selectCount(... and gt(slotStart, now)) → 直接 stub selectCount
        when(bookingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);   // 已有 5 条 CONFIRMED

        assertThrows(BizException.class, () -> service.create(mockReq(99L), req_create()));
        verify(bookingMapper, never()).insert(any());
    }

    /* ---------- cancel CAS — every terminal transition forces compare-and-set ---------- */

    @Test
    void complete_zeroAffected_throwsAndKeepsStatus() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.complete(1L));
    }

    @Test
    void markNoShow_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.markNoShow(1L));
    }

    @Test
    void forceCancel_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.forceCancel(1L, new com.heypickler.dto.admin.BookingForceCancelRequest()));
    }

    @Test
    void cancelMine_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.cancelMine(mockReq(7L), 1L));
    }

    @Test
    void cancelMine_pastCutoff_throws() {
        // FIXED_CLOCK now = 2026-07-22 08:00 +08(本地)。cancelDeadlineHours=2 → deadline=06:00。
        // 把 slotStart 推到 03:00(now-5h)—now < deadline 不成立 → 整单过截止;update 不应触发。
        Booking b = new Booking();
        b.setUserId(99L); b.setStatus("CONFIRMED");
        b.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(3, 0)));    // 已过去 5h
        b.setSlotEnd(LocalDateTime.of(DATE, LocalTime.of(4, 0)));
        when(bookingMapper.selectById(1L)).thenReturn(b);
        assertThrows(BizException.class, () -> service.cancelMine(mockReq(99L), 1L));
        verify(bookingMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void cancelMine_casZeroAffected_throws() {
        // 在 cutoff 内,但 CAS 返回 0(模拟 admin/scheduler 抢先改了状态)
        Booking b = new Booking();
        b.setUserId(99L); b.setStatus("CONFIRMED");
        b.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(14, 0)));
        b.setSlotEnd(LocalDateTime.of(DATE, LocalTime.of(15, 0)));
        when(bookingMapper.selectById(1L)).thenReturn(b);
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BizException.class, () -> service.cancelMine(mockReq(99L), 1L));
        verify(bookingSlotMapper, never()).delete(any(LambdaQueryWrapper.class));
    }
}
