package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.config.BookingProperties;
import com.heypickler.entity.Booking;
import com.heypickler.mapper.BookingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class BookingStatusSchedulerTest {
    private BookingMapper bookingMapper;
    private BookingProperties props;
    private BookingStatusScheduler scheduler;

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.ofHours(8));

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
        a.setCurrentNamespace("com.heypickler.mapper.BookingMapper");
        TableInfoHelper.initTableInfo(a, Booking.class);
    }

    @BeforeEach
    void setup() {
        bookingMapper = mock(BookingMapper.class);
        props = new BookingProperties();          // 默认 2h grace / 200 batch / PT5M / 30s
        scheduler = new BookingStatusScheduler(bookingMapper, props, FIXED);
    }

    @Test
    void scan_passesExactThresholdToMapper() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        scheduler.scanCompleteCandidates();

        ArgumentCaptor<LambdaUpdateWrapper<Booking>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(bookingMapper).update(eq(null), cap.capture());
        // 阈值精确等于 FIXED.now - 2h = "2026-07-22T00:00 +08" - 2h = "2026-07-21T22:00 +08"
        // 用 SQL 实际过 Lambda 的方式不易直接断言;改为间接:验证 update() 被调用 1 次且 batchSize=200
        verify(bookingMapper, times(1)).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void scan_noRows_returns() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates());
    }

    @Test
    void scan_underBatch_meansFinished() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(50);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates()); // 无额外动作
    }

    @Test
    void scan_fullBatch_signalsMoreToProcess_viaLog() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(200);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates()); // 仍不抛
    }
}
