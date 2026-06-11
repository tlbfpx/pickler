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
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
