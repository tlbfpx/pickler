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
        try {
            rankingService.refreshRankings(event.type());
        } catch (Exception e) {
            log.error("Failed to refresh rankings for type: {}", event.type(), e);
        }
    }

    public record PointChangeEvent(String type) {
    }
}
