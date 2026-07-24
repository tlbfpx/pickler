package com.heypickler.vo;

import lombok.Data;

@Data
public class CourtVO {
    private Long id;
    private Long venueId;
    private String name;
    private String courtType;
    private Integer slotMinutes;
    private String status;
    private Integer sortOrder;
}
