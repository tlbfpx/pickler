package com.heypickler.vo;

import lombok.Data;

@Data
public class VenueContactVO {
    private Long id;
    private String type;
    private String value;
    private String label;
    private Integer sortOrder;
}
