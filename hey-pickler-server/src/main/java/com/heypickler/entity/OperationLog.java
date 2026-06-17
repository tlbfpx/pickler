package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
