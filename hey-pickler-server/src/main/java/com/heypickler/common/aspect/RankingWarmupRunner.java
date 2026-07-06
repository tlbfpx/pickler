package com.heypickler.common.aspect;

import com.heypickler.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loop-v2 D11 — pre-warm the ranking cache at startup so the first batch of
 * concurrent /api/app/rankings requests doesn't slam MySQL with cold-miss
 * SELECT queries. Runs after the Spring context is ready, before HTTP traffic
 * is accepted.
 *
 * <p>Tolerates the missing-CURRENT-season case (existing tests / first-ever
 * boot) — logs WARN and skips that type instead of failing startup.
 *
 * <p>Loop-v4 D22 — warmup runs on a single-thread executor with a hard
 * per-type timeout (10s) so a stuck refresh can never block boot past
 * {@code WARMUP_BUDGET_MS}. The web server is accepting HTTP traffic by
 * the time {@code run()} executes; a hot DB stall that consumes the budget
 * falls through to cold-cache for the first batch of ranking reads, which
 * is bounded by CallerRunsPolicy on the listener side (D13).
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RankingWarmupRunner implements ApplicationRunner {

    private static final String[] TYPES = {"STAR", "PARTY"};

    /** Cap each type's refresh so a slow DB cannot lock up the boot thread. */
    private static final long PER_TYPE_TIMEOUT_SECONDS = 10L;

    private final RankingService rankingService;

    @Override
    public void run(ApplicationArguments args) {
        long t0 = System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ranking-warmup");
            t.setDaemon(true);  // must not keep JVM alive past spring context shutdown
            return t;
        });
        int warmed = 0;
        for (String type : TYPES) {
            try {
                executor.submit(() -> rankingService.refreshRankings(type)).get(
                        PER_TYPE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                warmed++;
            } catch (Exception e) {
                log.warn("[D11/D22] ranking warmup skipped for type={} ({}). "
                        + "Falling back to first-request cold-start; the "
                        + "CallerRunsPolicy on rankingExecutor (D13) keeps "
                        + "later ranking refreshes safe.", type, e.getMessage());
            }
        }
        executor.shutdownNow();
        long ms = System.currentTimeMillis() - t0;
        log.info("[D11/D22] ranking warmup finished — {} type(s) warmed in {} ms", warmed, ms);
    }
}
