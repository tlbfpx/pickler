package com.heypickler.common.aspect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditExecutorMonitorTest {

    @Test
    void recordsAndExposesRejectionCount() {
        AuditExecutorMonitor m = new AuditExecutorMonitor();
        assertEquals(0, m.getRejectionCount());

        m.recordRejection();
        m.recordRejection();
        m.recordRejection();

        assertEquals(3, m.getRejectionCount());
    }

    @Test
    void counterIsThreadSafe() throws Exception {
        AuditExecutorMonitor m = new AuditExecutorMonitor();
        int threads = 8;
        int perThread = 1_000;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) m.recordRejection();
            });
            workers[i].start();
        }
        for (Thread w : workers) w.join();

        assertEquals((long) threads * perThread, m.getRejectionCount());
    }
}
