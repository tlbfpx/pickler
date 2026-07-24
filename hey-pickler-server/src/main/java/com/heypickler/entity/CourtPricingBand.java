package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("court_pricing_band")
public class CourtPricingBand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    private String dayType;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
