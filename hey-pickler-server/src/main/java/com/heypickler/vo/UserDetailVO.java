package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDetailVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String city;
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    /** 中文档名，由 TierResolver.nameFor("STAR", starTier) 装配 */
    private String starTierName;
    /** 中文档名，由 TierResolver.nameFor("PARTY", partyTier) 装配 */
    private String partyTierName;
    /** 段位色，由 TierResolver.colorFor("STAR", starTier) 装配 */
    private String starTierColor;
    /** 段位色，由 TierResolver.colorFor("PARTY", partyTier) 装配 */
    private String partyTierColor;
    /** 段位图标，由 TierResolver.iconFor("STAR", starTier) 装配 */
    private String starTierIcon;
    /** 段位图标，由 TierResolver.iconFor("PARTY", partyTier) 装配 */
    private String partyTierIcon;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
