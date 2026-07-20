package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 访问日志（Loop-v19 Dashboard Phase 2 V21）。
 *
 * <p>append-only 表，无 {@code deleted_at}。由 {@code AccessLogFilter} 在
 * {@code @Order(Ordered.LOWEST_PRECEDENCE - 10)} 包全部 {@code /api/**} 写入；
 * 鉴权失败请求也会记录（userId / adminId 为 null，statusCode 为 401/403）。
 *
 * <p>errorMsg 字段在 Phase 2 R3 中复用：{@code POST /api/app/track/event} 上报时
 * 写入事件 name，便于 SQL 检索（最小 schema 原则，不再额外加 event_name 列）。
 */
@Data
@TableName("access_log")
public class AccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求 URI（不带 query string）。 */
    private String path;

    /** HTTP method。 */
    private String method;

    /** HTTP 响应状态码。 */
    private Integer statusCode;

    /** 请求处理耗时（ms）。 */
    private Integer latencyMs;

    /** APP 用户 id；admin 路径为 null。 */
    private Long userId;

    /** 管理员 id；app 路径为 null。 */
    private Long adminId;

    private String ip;

    private String userAgent;

    /** track/event 复用：event name；错误请求：异常摘要。 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}