package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("venue_business_hour")
public class VenueBusinessHour {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
