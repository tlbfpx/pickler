package com.heypickler.service.impl;

import com.heypickler.entity.AccessLog;
import com.heypickler.mapper.AccessLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Loop-v19 Dashboard Phase 2 — AccessLogService 单测。
 *
 * <p>recordAccess 是 @Async 异步方法，单测同步调用验证：
 * ① happy path 调 save
 * ② DB 异常被吞掉（warn 但不抛）
 * ③ null 输入直接返回不调 save
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessLogServiceImplTest {

    @Mock AccessLogMapper mapper;
    @InjectMocks AccessLogServiceImpl service;

    @Test
    void recordAccess_happyPath_invokesSave() {
        AccessLog log = new AccessLog();
        log.setPath("/api/app/events");
        log.setMethod("GET");
        log.setStatusCode(200);
        log.setLatencyMs(15);

        service.recordAccess(log);

        verify(mapper, times(1)).insert(log);
    }

    @Test
    void recordAccess_dbFailure_swallowsException() {
        AccessLog log = new AccessLog();
        log.setPath("/api/app/track/event");
        log.setStatusCode(200);
        doThrow(new RuntimeException("DB down")).when(mapper).insert(any(AccessLog.class));

        // 不抛 — append-only 数据源写失败不能污染请求响应
        assertDoesNotThrow(() -> service.recordAccess(log));
        verify(mapper, times(1)).insert(log);
    }

    @Test
    void recordAccess_nullInput_skipsSave() {
        service.recordAccess(null);
        verify(mapper, never()).insert(any(AccessLog.class));
    }
}