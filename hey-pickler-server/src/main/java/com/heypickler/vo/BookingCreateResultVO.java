package com.heypickler.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingCreateResultVO {
    private Long id;
    private String bookingNo;
    private String courtName;
    private String venueName;
    private LocalDate slotDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;
}
