package com.heypickler.vo;

import lombok.Data;

@Data
public class EventResultVO {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String matchType;
    private Integer points;
    private Integer rank;
}
