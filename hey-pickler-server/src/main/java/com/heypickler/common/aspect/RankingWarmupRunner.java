package com.heypickler.common.aspect;

import com.heypickler.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loop-v2 D11 — pre-warm the ranking cache at startup so the first batch of
 * concurrent /api/app/rankings requests doesn't slam MySQL with cold-miss
 * SELECT queries. Runs after the Spring context is ready, before HTTP traffic
 * is accepted.
 *
 * <p>Tolerates the missing-CURRENT-season case (existing tests / first-ever
 * boot) — logs WARN and skips that type instead of failing startup.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class RankingWarmupRunner implements ApplicationRunner {

    private static final String[] TYPES = {"STAR", "PARTY"};

    private final RankingService rankingService;

    @Override
    public void run(ApplicationArguments args) {
        long t0 = System.currentTimeMillis();
        int warmed = 0;
        for (String type : TYPES) {
            try {
                rankingService.refreshRankings(type);
                warmed++;
            } catch (RuntimeException e) {
                log.warn("[D11] ranking warmup skipped for type={} ({}) — first "
                        + "request will cold-start that path until first refresh.",
                        type, e.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - t0;
        log.info("[D11] ranking warmup finished — {} type(s) warmed in {} ms", warmed, ms);
    }
}
