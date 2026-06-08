package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ban_record")
public class BanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long operatorId;
    private String action;
    private String reason;
    private LocalDateTime banUntil;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
