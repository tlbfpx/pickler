package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * In-app notification.
 *
 * <p>Mirrors the {@code notification} table created by Flyway V16. Recipients
 * are app users (U-BIGINT). The {@code type} column is a free-form string
 * (kept loose so future types can be added without DDL); known values are
 * listed in {@link com.heypickler.common.enums.NotificationType}.
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String linkUrl;
    /** 0 = unread (badge), 1 = read. Stored as TINYINT. */
    private Integer readFlag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
