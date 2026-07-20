package com.heypickler.service;

import com.heypickler.entity.AccessLog;

/**
 * Loop-v19 Dashboard Phase 2 — API 访问日志服务。
 *
 * <p>由 {@code AccessLogFilter} + {@code AppTrackController} 异步写入。
 * append-only：admin 端查询 UI 留 Phase 5。
 */
public interface AccessLogService {

    /**
     * 异步写一条访问日志。失败仅 warn，不影响原请求响应（filter 必须 catch
     * 所有异常让 request 透传）。
     *
     * @param log 待写入的 AccessLog 实体；createdAt 由 FieldFill.INSERT 自动填充
     */
    void recordAccess(AccessLog log);
}