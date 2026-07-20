package com.heypickler.service;

import com.heypickler.entity.LoginLog;

import java.time.LocalDateTime;

/**
 * Loop-v19 Dashboard Phase 2 — 登录行为日志服务。
 *
 * <p>所有写操作走 {@code @Async("loginLogExecutor")}，不阻塞调用方线程。
 * append-only 性质：service 不暴露 update / delete 接口（合规要求）。
 */
public interface LoginLogService {

    /**
     * 异步写一条登录日志。失败仅 warn（access log 同模式：合规数据不能丢，
     * 但不能因此阻塞登录响应）。
     *
     * @param log 待写入的 LoginLog 实体；createdAt 由 FieldFill.INSERT 自动填充
     */
    void recordLogin(LoginLog log);

    /**
     * Phase 3 同期群预热：在窗口内按 loginResult 计数。loginResult 命中
     * {@code SUCCESS} 表示当日活跃用户基数；命中 {@code FAIL_*} 表示异常。
     *
     * @param result login_result 枚举值；传 {@code null} 或空表示不过滤
     * @param from 半开区间 [from, to)
     * @param to 半开区间 [from, to)
     */
    long countByResultInRange(String result, LocalDateTime from, LocalDateTime to);
}