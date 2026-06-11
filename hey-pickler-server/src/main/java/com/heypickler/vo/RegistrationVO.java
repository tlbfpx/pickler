package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RegistrationVO {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String matchType;
    private Long partnerId;
    private String partnerNickname;
    private String status;
    private LocalDateTime createdAt;
}
