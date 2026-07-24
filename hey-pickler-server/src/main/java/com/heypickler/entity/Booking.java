package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking")
public class Booking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bookingNo;
    private Long userId;
    private Long venueId;
    private Long courtId;
    private LocalDate slotDate;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;            // CONFIRMED / CANCELLED / COMPLETED / NO_SHOW
    private String cancelReason;
    private LocalDateTime cancelledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}