package com.heypickler.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingVO {
    private Long id;
    private String bookingNo;
    private Long courtId;
    private String courtName;
    private Long venueId;
    private String venueName;
    private LocalDate slotDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime slotStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;
    private String cancelReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelledAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
}
