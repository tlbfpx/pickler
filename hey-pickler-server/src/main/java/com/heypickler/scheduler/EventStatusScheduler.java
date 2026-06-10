package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.entity.Event;
import com.heypickler.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hey-pickler.scheduler.status-transition-enabled", havingValue = "true", matchIfMissing = false)
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
