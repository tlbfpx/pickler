package com.heypickler.listener;

import com.heypickler.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointChangeListener {

    private final RankingService rankingService;

    @Async("rankingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPointChange(PointChangeEvent event) {
        if (event.seasonCode() == null) {
            // No current season — ranking.season is NOT NULL (V1), so a refresh would
            // throw SQLException. Point records were still written (PlacementService
            // tolerates a null season); skip ranking refresh until a season-bound event
            // arrives. Common in dev/seed environments without a configured CURRENT season.
            log.warn("Skipping ranking refresh for type={} — no current season (seasonCode=null).",
                    event.type());
            return;
        }
        try {
            rankingService.refreshRankings(event.type(), event.seasonCode());
        } catch (Exception e) {
            log.error("Failed to refresh rankings for type={} season={}",
                    event.type(), event.seasonCode(), e);
        }
    }

    public record PointChangeEvent(String type, String seasonCode) {
    }
}
