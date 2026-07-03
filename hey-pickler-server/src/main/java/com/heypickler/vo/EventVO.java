package com.heypickler.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventVO {
    private Long id;
    private String type;
    private String title;
    private String bannerUrl;
    private LocalDateTime eventTime;
    private String location;
    private String status;
    private Integer currentParticipants;
    private Integer maxParticipants;
    private BigDecimal fee;
    private LocalDateTime registrationDeadline;
    private Integer minPoints;
    private String format;
    private Boolean groupingLocked;
    private String createdByUsername;
}
