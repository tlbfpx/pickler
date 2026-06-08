package com.heypickler.vo;

import lombok.Data;

@Data
public class BannerVO {
    private Long id;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private String status;
}
