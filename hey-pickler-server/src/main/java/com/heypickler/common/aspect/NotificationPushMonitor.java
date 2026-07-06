package com.heypickler.common.aspect;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Loop-v3 D15 — counts failed notification pushes so lost deliveries can be
 * detected from logs/metrics, not by user complaints.
 *
 * <p>NotificationServiceImpl.push is intentionally exception-swallowing (callers
 * are transactional and a notification failure must not roll back domain state).
 * Previously the only signal of failure was a {@code log.error} with no
 * counter, so a misconfigured DB connection or schema drift could silently drop
 * notifications for days. Now: one monotonic counter per push failure plus a
 * periodic WARN line, mirroring the operator-visibility pattern used for
 * {@link AuditExecutorMonitor} and {@link RankingExecutorMonitor}.
 */
@Slf4j
public class NotificationPushMonitor {

    private static final int WARN_EVERY_N_PUSH_FAILURES = 25;

    private final AtomicLong failures = new AtomicLong();

    public void recordFailure(Long userId, String type, Throwable cause) {
        long n = failures.incrementAndGet();
        // First failure or every batch — surface it. Trace summary includes
        // the last userId/type/cause as breadcrumbs for grep + alerting.
        if (n == 1 || n % WARN_EVERY_N_PUSH_FAILURES == 0) {
            log.warn("notification.push failure #{} (userId={}, type={}, cause={}) — "
                    + "if this keeps climbing, the notification table or DB may be down. "
                    + "Users won't see banners; downstream flows are unaffected.",
                    n, userId, type, cause == null ? "null" : cause.getClass().getSimpleName());
        }
    }

    public long getFailureCount() {
        return failures.get();
    }
}
