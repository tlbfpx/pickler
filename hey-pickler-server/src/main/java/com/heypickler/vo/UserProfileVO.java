package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String phone; // masked like 138****1234
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    /** 中文档名，由 TierProperties.nameFor(starTier) 装配 */
    private String starTierName;
    /** 中文档名，由 TierProperties.nameFor(partyTier) 装配 */
    private String partyTierName;
    private Integer totalEvents;
}
