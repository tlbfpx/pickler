package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAdminVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String phone; // decrypted
    private Integer starPoints;
    private Integer partyPoints;
    private String starTier;
    private String partyTier;
    /** 中文档名，由 TierResolver.nameFor("STAR", starTier) 装配 */
    private String starTierName;
    /** 中文档名，由 TierResolver.nameFor("PARTY", partyTier) 装配 */
    private String partyTierName;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
