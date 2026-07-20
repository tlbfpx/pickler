package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录行为日志（Loop-v19 Dashboard Phase 2 V21）。
 *
 * <p>append-only 表，无 {@code deleted_at}。userId 与 adminId 互斥：
 * APP 登录用 userId，ADMIN 登录用 adminId。loginResult 枚举固定
 * SUCCESS | FAIL_PWD | FAIL_BANNED | FAIL_RATE_LIMIT | FAIL_INVALID_CODE | FAIL_OTHER。
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** APP 登录用户 id；ADMIN 登录为 null。 */
    private Long userId;

    /** ADMIN 登录管理员 id；APP 登录为 null。 */
    private Long adminId;

    /** APP | ADMIN。 */
    private String channel;

    /** SUCCESS | FAIL_PWD | FAIL_BANNED | FAIL_RATE_LIMIT | FAIL_INVALID_CODE | FAIL_OTHER。 */
    private String loginResult;

    /** 失败时的 BizException errorCode；SUCCESS 时 null。 */
    private String errorCode;

    /** X-Forwarded-For 第一跳 / fallback getRemoteAddr()。 */
    private String ip;

    /** 小程序持久化 did（wx.getStorageSync('did')）；ADMIN 登录 null。 */
    private String deviceId;

    /** 请求 UA，截断 256 字符。 */
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}