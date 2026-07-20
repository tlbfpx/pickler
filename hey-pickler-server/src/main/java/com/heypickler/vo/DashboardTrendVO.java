package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** R2 trends 响应。 */
@Data
public class DashboardTrendVO {
    private String range;
    private List<DayBucket> buckets;

    @Data
    public static class DayBucket {
        private LocalDate date;
        private long users;
        private long registrations;
        private double revenue;     // fen
        private long eventsCount;
    }
}
