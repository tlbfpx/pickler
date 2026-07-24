package com.heypickler.vo;

import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class VenueDetailVO extends VenueVO {
    private List<BusinessHourVO> businessHours;
    private List<CourtVO> courts;

    @Data
    public static class BusinessHourVO {
        private Integer dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
    }
}
