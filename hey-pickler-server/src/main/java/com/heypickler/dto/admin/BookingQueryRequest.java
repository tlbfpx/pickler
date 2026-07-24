package com.heypickler.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BookingQueryRequest {
    private Long venueId;
    private Long courtId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String status;            // CONFIRMED/CANCELLED/COMPLETED/NO_SHOW
    private String keyword;           // bookingNo 或 userId
    @Min(value = 1) private int page = 1;
    @Min(value = 1) @Max(value = 100) private int size = 20;
}
