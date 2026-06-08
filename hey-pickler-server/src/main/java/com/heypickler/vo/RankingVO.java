package com.heypickler.vo;

import lombok.Data;

@Data
public class RankingVO {
    private Integer rank;
    private Integer change;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String city;
    private Integer points;
    private String tier;
}
