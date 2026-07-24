package com.heypickler.dto.app;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingCreateRequest {
    @NotNull(message = "courtId 不能为空")
    private Long courtId;
    @NotNull(message = "slotStart 不能为空")
    private LocalDateTime slotStart;
    @NotNull(message = "slotsCount 不能为空")
    @Min(value = 1, message = "slotsCount 最少 1")
    @Max(value = 8, message = "单次最多连订 8 格")
    private Integer slotsCount;
}
