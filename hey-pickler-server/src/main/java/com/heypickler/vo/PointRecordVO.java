package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointRecordVO {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private String type;
    private Integer points;
    private String reason;
    private LocalDateTime createdAt;
}
