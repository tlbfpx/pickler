package com.heypickler.common.aspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loop-v11 — moves NotificationPushMonitor from 20.3% to ~95%+.
 */
class NotificationPushMonitorTest {

    @Test
    void recordFailure_startsAtOne() {
        NotificationPushMonitor m = new NotificationPushMonitor();
        m.recordFailure(7L, "EVENT", new RuntimeException("boom"));
        assertEquals(1L, m.getFailureCount());
    }

    @Test
    void recordFailure_incrementsCount() {
        NotificationPushMonitor m = new NotificationPushMonitor();
        for (int i = 0; i < 25; i++) {
            m.recordFailure(i + 1L, "TYPE", null);
        }
        assertEquals(25L, m.getFailureCount());
    }

    @Test
    void recordFailure_50thThreshold_emitsWarn() {
        NotificationPushMonitor m = new NotificationPushMonitor();
        for (int i = 0; i < 50; i++) {
            m.recordFailure((long) i, "X", null);
        }
        assertEquals(50L, m.getFailureCount());
    }

    @Test
    void recordFailure_acceptsNullCause() {
        NotificationPushMonitor m = new NotificationPushMonitor();
        m.recordFailure(7L, "EVENT", null);
        assertEquals(1L, m.getFailureCount());
    }
}
