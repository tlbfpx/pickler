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
    VENUE_NOT_FOUND(1008, "场馆不存在"),
    COURT_NOT_FOUND(1009, "场地不存在"),
    COURT_NOT_AVAILABLE(1010, "场地不可预订"),
    SLOT_NOT_BOOKABLE(1011, "该时段不可预订"),
    SLOT_ALREADY_TAKEN(1012, "该时段刚被占用"),
    BOOKING_WINDOW_EXCEEDED(1013, "预约时段不在可订窗口"),
    CANCEL_DEADLINE_PASSED(1014, "已超过取消截止时间"),
    USER_BOOKING_LIMIT_EXCEEDED(1015, "您的有效预约数已达上限"),
    BOOKING_NOT_FOUND(1016, "预约不存在"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
