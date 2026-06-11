package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BanRecordVO {
    private Long id;
    private Long userId;
    private String userNickname;
    private String userPhone;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String reason;
    private LocalDateTime banUntil;
    private LocalDateTime createdAt;
}
