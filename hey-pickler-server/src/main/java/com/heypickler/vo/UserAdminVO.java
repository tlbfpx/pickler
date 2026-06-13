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
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
