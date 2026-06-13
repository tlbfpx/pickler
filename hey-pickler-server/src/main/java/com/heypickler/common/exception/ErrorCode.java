package com.heypickler.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMITED(429, "请求过于频繁"),
    PARAM_ERROR(1001, "参数校验失败"),
    USER_BANNED(1002, "账号已被封禁"),
    REGISTRATION_FULL(1003, "报名已满"),
    DUPLICATE_REGISTRATION(1004, "重复报名"),
    REGISTRATION_CLOSED(1005, "报名已截止"),
    INVALID_STATUS_TRANSITION(1006, "无效的状态转换"),
    INSUFFICIENT_POINTS(1007, "积分不足"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
