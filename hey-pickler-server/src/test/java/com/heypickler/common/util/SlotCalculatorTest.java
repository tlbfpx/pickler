package com.heypickler.common.util;

import com.heypickler.entity.CourtPricingBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SlotCalculatorTest {
    private final SlotCalculator calc = new SlotCalculator();

    private CourtPricingBand band(String s, String e, int price) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType("WEEKDAY");
        b.setStartTime(LocalTime.parse(s));
        b.setEndTime(LocalTime.parse(e));
        b.setPrice(new BigDecimal(price));
        return b;
    }

    private final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 8, 0); // 周三 08:00
    private final LocalDate DATE = LocalDate.of(2026, 7, 22);

    @Test
    void closedDay_returnsEmpty() {
        // openTime=null 表示当日休
        assertTrue(calc.generate(null, null, 60, List.of(), Set.of(), DATE, NOW, 14).isEmpty());
    }

    @Test
    void happyPath_generatesHourlySlotsWithPrice() {
        // 09:00-11:00 营业,1h 格,价 40 → 09:00/10:00 两格
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(2, r.size());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 0), r.get(0).start());
        assertTrue(r.get(0).available());
        assertEquals(new BigDecimal("40"), r.get(0).price());
    }

    @Test
    void trailingPartialSlot_dropped() {
        // 09:00-10:30, 1h 格 → 仅 09:00 一格(10:00-11:00 越过 10:30 丢弃)
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(10, 30), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(1, r.size());
    }

    @Test
    void pastSlot_skipped() {
        // now=10:00+，09:00 格早于 now+30min 跳过
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, now, 14);
        // 09:00 < 10:30 跳过; 10:00 < 10:30 跳过 → 空
        assertTrue(r.isEmpty());
    }

    @Test
    void gapBand_slotUnbookableNoPrice() {
        // band 只覆盖 09:00-10:00,10:00-11:00 无 band → 不可订且无价
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "10:00", 40)), Set.of(), DATE, NOW, 14);
        assertTrue(r.get(0).available());
        assertFalse(r.get(1).available());
        assertNull(r.get(1).price());
    }

    @Test
    void occupiedSlot_unavailable() {
        var occ = Set.of(LocalDateTime.of(2026, 7, 22, 9, 0));
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), occ, DATE, NOW, 14);
        assertFalse(r.get(0).available()); // 09:00 被占
        assertTrue(r.get(1).available());
    }

    @Test
    void windowEnd_stopsGeneration() {
        // leadDays=0 → windowEnd=now,当日格子全部 >= windowEnd → 空
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 0);
        assertTrue(r.isEmpty());
    }

    @Test
    void bandBoundary_routesToSecondBand() {
        // [09:00,10:00)=40, [10:00,11:00)=80:10:00 格落第二带
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "10:00", 40), band("10:00", "11:00", 80)), Set.of(), DATE, NOW, 14);
        assertEquals(new BigDecimal("40"), r.get(0).price());
        assertEquals(new BigDecimal("80"), r.get(1).price());
    }

    @Test
    void midnightWrap_terminatesNoInfiniteLoop() {
        // 22:00-23:00 营业,1h 格:22:00 格有效;下一格 23:00 → endT=00:00 回绕,应停而非产出越界格
        var r = calc.generate(LocalTime.of(22, 0), LocalTime.of(23, 0), 60,
                List.of(band("22:00", "23:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(1, r.size()); // 无回绕守卫时会多产出一个 23:00 越界格 → 2,故此用例锁死守卫
    }
}
