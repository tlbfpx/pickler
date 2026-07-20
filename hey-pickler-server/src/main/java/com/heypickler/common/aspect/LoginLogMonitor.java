package com.heypickler.common.aspect;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Loop-v19 Dashboard Phase 2 — login/access log executor 监控。
 *
 * <p>同 {@link AuditExecutorMonitor} 模式：每次 executor 拒绝时 +1，每 100
 * 次打一条 WARN 提示运维关注（队列可能扩，或写库变慢）。
 *
 * <p>append-only 性质：login/access 日志是合规/审计数据源，不能丢。监控
 * 计数让 backpressure 在日志里可见，而不是静默丢数据。
 */
@Slf4j
public class LoginLogMonitor {

    private final AtomicLong rejections = new AtomicLong(0);

    public void recordRejection() {
        long total = rejections.incrementAndGet();
        if (total % 100 == 0) {
            log.warn("loginLogExecutor rejection #{} — async queue saturated, "
                    + "login/access log was persisted inline on the calling thread. "
                    + "If this number keeps climbing, login queue capacity may need increasing.", total);
        }
    }

    /** 测试用 + 健康检查端点（Phase 5 接入 actuator）。 */
    public long getRejectionCount() {
        return rejections.get();
    }
}