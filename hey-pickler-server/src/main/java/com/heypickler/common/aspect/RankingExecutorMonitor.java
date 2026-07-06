package com.heypickler.common.aspect;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Loop-v3 D13 — rejection counter for {@code rankingExecutor}.
 * Mirrors {@link AuditExecutorMonitor} but tracks the ranking side, where
 * undetected rejection means stale ranking data visible to users.
 */
@Slf4j
public class RankingExecutorMonitor {

    private static final int WARN_EVERY_N_REJECTIONS = 50;  // ranking is more user-visible than audit, log sooner

    private final AtomicLong rejections = new AtomicLong();

    public void recordRejection() {
        long n = rejections.incrementAndGet();
        if (n == 1 || n % WARN_EVERY_N_REJECTIONS == 0) {
            log.warn("rankingExecutor rejection #{} — refresh task ran inline on the "
                    + "calling thread; /api/app/rankings may serve slightly stale data "
                    + "during bursts until the next transaction commits.", n);
        }
    }

    public long getRejectionCount() {
        return rejections.get();
    }
}
