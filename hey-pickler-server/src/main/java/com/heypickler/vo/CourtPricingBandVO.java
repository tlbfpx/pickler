package com.heypickler.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class CourtPricingBandVO {
    private Long id;
    /** WEEKDAY / WEEKEND / ALL */
    private String dayType;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
}
