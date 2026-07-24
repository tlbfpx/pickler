package com.heypickler.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SlotVO {
    private LocalDateTime start;
    private LocalDateTime end;
    private boolean available;
    private BigDecimal price;
}
