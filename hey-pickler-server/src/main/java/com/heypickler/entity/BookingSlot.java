package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("booking_slot")
public class BookingSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookingId;
    private Long courtId;
    private LocalDateTime slotStart;
}
