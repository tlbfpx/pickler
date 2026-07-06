package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.entity.Event;
import com.heypickler.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Auto-transitions OPEN/FULL events to IN_PROGRESS once their
 * {@code event_time} has passed. Runs every 60 seconds.
 *
 * <p>Loop-v3 D14: previously gated by
 * {@code @ConditionalOnProperty(matchIfMissing=false)} on
 * {@code hey-pickler.scheduler.status-transition-enabled}. Production yml did
 * not set this flag → bean was never registered → transitions never happened
 * silently. The cron was effectively dead in prod while dev kept it alive.
 *
 * <p>The gate is removed; the scheduler is on by default. Operators can still
 * disable it explicitly via {@code HEY_PICKLER_STATUS_SCHEDULER_ENABLED=false}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventStatusScheduler {

    private final EventMapper eventMapper;

    private static final List<String> ACTIVE_STATUSES = Arrays.asList("OPEN", "FULL");

    @Scheduled(fixedRate = 60000)
    public void autoTransitionToInProgress() {
        LocalDateTime now = LocalDateTime.now();

        int rows = eventMapper.update(null,
                new LambdaUpdateWrapper<Event>()
                        .in(Event::getStatus, ACTIVE_STATUSES)
                        .le(Event::getEventTime, now)
                        .set(Event::getStatus, "IN_PROGRESS"));

        if (rows > 0) {
            log.info("Auto transitioned {} event(s) to IN_PROGRESS", rows);
        }
    }
}
