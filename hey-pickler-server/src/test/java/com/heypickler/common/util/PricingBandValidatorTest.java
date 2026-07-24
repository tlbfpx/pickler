package com.heypickler.common.util;

import com.heypickler.common.exception.BizException;
import com.heypickler.entity.CourtPricingBand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingBandValidatorTest {
    private final PricingBandValidator validator = new PricingBandValidator();

    private CourtPricingBand band(String dayType, String s, String e, int price) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType(dayType);
        b.setStartTime(LocalTime.parse(s));
        b.setEndTime(LocalTime.parse(e));
        b.setPrice(new BigDecimal(price));
        return b;
    }

    @Test
    void validate_adjacentBandsTouching_ok() {
        // 半开区间: [09:00,12:00) 与 [12:00,15:00) 相邻不重叠
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "12:00", "15:00", 60))));
    }

    @Test
    void validate_weekdayOverlap_throws() {
        assertThrows(BizException.class, () -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "11:00", "14:00", 60))));
    }

    @Test
    void validate_allOverlapsWeekday_throws() {
        // ALL 与 WEEKDAY 同属工作日有效集,重叠必须拒
        assertThrows(BizException.class, () -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("ALL", "11:00", "14:00", 60))));
    }

    @Test
    void validate_weekdayAndWeekendSameTime_ok() {
        // 工作日带与周末带时间相同不冲突(分属不同有效集)
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKEND", "09:00", "12:00", 80))));
    }

    @Test
    void validate_gapAllowed_ok() {
        // 缺口允许:12:00-14:00 无 band,校验通过(运行期那两格不可订)
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "14:00", "18:00", 60))));
    }

    @Test
    void validate_empty_ok() {
        assertDoesNotThrow(() -> validator.validate(List.of()));
    }
}
