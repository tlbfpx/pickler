package com.heypickler.common.aspect;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Loop-v2 D8 — counts rejected audit-log submissions and emits a periodic
 * WARN line so audit-backpressure is observable. CallerRunsPolicy turns each
 * rejection into a synchronous write on the admin thread, so no entry is
 * dropped; this monitor exists to surface that the queue is too small.
 */
@Slf4j
public class AuditExecutorMonitor {

    private static final int WARN_EVERY_N_REJECTIONS = 100;

    private final AtomicLong rejections = new AtomicLong();

    public void recordRejection() {
        long n = rejections.incrementAndGet();
        if (n == 1 || n % WARN_EVERY_N_REJECTIONS == 0) {
            log.warn("auditLogExecutor rejection #{} — async queue saturated, "
                    + "audit was persisted inline on the calling thread. "
                    + "If this number keeps climbing, audit queue capacity may need increasing.", n);
        }
    }

    public long getRejectionCount() {
        return rejections.get();
    }
}
