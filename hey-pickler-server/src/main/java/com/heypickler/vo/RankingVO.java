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
    /** 中文档名（如 BRONZE→青铜），由 TierResolver.nameFor(ranking.getType(), tier) 装配 */
    private String tierName;
    /** 段位色，由 TierResolver.colorFor(ranking.getType(), tier) 装配 */
    private String tierColor;
    /** 段位图标，由 TierResolver.iconFor(ranking.getType(), tier) 装配 */
    private String tierIcon;
}
