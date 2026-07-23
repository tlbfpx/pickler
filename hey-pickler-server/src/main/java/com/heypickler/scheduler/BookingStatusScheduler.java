package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.config.BookingProperties;
import com.heypickler.entity.Booking;
import com.heypickler.mapper.BookingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Slf4j
public class BookingStatusScheduler {

    private final BookingMapper bookingMapper;
    private final BookingProperties props;
    private final Clock clock;

    public BookingStatusScheduler(BookingMapper bookingMapper, BookingProperties props,
                                 @Qualifier("clock") Clock clock) {
        this.bookingMapper = bookingMapper;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${hey-pickler.booking.complete-cadence:PT5M}",
            initialDelayString = "${hey-pickler.booking.initial-delay-seconds:30}"
    )
    public void scanCompleteCandidates() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusHours(props.getCompleteGraceHours());
        int batchSize = props.getCompleteBatchSize();

        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getStatus, "CONFIRMED")
                        .lt(Booking::getSlotEnd, threshold)
                        .set(Booking::getStatus, "COMPLETED")
                        .last("LIMIT " + batchSize));       // 批大小(spec §10 强约束;仿 DashboardServiceImpl 的 .last("LIMIT " + limit) 模式)

        if (rows == 0) return;
        if (rows < batchSize) {
            log.info("BookingStatusScheduler: 自动完成 {} 条预约(末批)", rows);
        } else {
            // rows == batchSize 表示可能还有更多;下次周期继续扫
            log.info("BookingStatusScheduler: 自动完成 {} 条预约(满批,可能还有)", rows);
        }
    }
}
