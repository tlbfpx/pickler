package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointRecordVO {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private String type;
    private Integer points;
    private String reason;
    /** 来源：MANUAL / ADJUST / PLACEMENT / REGISTRATION / CHECK_IN / REDEEM */
    private String source;
    private String seasonCode;
    /** 操作管理员用户名；PLACEMENT 等系统记录为 null（前端显示"系统"） */
    private String operatorName;
    private LocalDateTime createdAt;
}
