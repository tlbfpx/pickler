package com.heypickler.vo;

import lombok.Data;

@Data
public class EventParticipantVO {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String city;
    private String matchType;
    private String registrationStatus;
}
