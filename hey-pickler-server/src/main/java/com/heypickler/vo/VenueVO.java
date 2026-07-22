package com.heypickler.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class VenueVO {
    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coverUrl;
    private String description;
    private String status;
    private Integer bookingLeadDays;
    private List<VenueContactVO> contacts;
}
