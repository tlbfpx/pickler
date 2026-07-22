package com.heypickler.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
public class VenueBusinessHourRequest {

    @NotEmpty(message = "营业时间不能为空")
    @Size(max = 7, message = "至多 7 天")
    private List<Item> hours;

    @Data
    public static class Item {
        @Min(value = 0)
        @Max(value = 6)
        private Integer dayOfWeek; // 0=日..6=六

        private LocalTime openTime;  // null=当日休
        private LocalTime closeTime;
    }
}
