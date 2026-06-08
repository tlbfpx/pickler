package com.heypickler.listener;

import com.heypickler.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointChangeListener {

    private final RankingService rankingService;

    @Async("rankingExecutor")
    @EventListener
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
