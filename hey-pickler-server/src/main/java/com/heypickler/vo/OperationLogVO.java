package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationLogVO {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String method;
    private String module;
    private String action;
    private String targetType;
    private String targetId;
    private String path;
    private String params;
    private Integer status;
    private Integer errorCode;
    private String errorMsg;
    private String ip;
    private String userAgent;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
