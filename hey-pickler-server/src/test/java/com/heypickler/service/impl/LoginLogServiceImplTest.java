package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.heypickler.entity.LoginLog;
import com.heypickler.mapper.LoginLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loop-v19 Dashboard Phase 2 — LoginLogService 单测。
 *
 * <p>覆盖：happy + DB 异常吞掉 + null 输入跳过 + countByResultInRange 透传
 * result 过滤条件（null/blank 时不过滤）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginLogServiceImplTest {

    @Mock LoginLogMapper mapper;
    @InjectMocks LoginLogServiceImpl service;

    @Test
    void recordLogin_happyPath_invokesSave() {
        LoginLog log = new LoginLog();
        log.setUserId(1L);
        log.setChannel("APP");
        log.setLoginResult("SUCCESS");

        service.recordLogin(log);

        verify(mapper, times(1)).insert(log);
    }

    @Test
    void recordLogin_dbFailure_swallowsException() {
        LoginLog log = new LoginLog();
        log.setAdminId(2L);
        log.setChannel("ADMIN");
        log.setLoginResult("FAIL_PWD");
        doThrow(new RuntimeException("DB down")).when(mapper).insert(any(LoginLog.class));

        assertDoesNotThrow(() -> service.recordLogin(log));
        verify(mapper, times(1)).insert(log);
    }

    @Test
    void recordLogin_nullInput_skipsSave() {
        service.recordLogin(null);
        verify(mapper, never()).insert(any(LoginLog.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void countByResultInRange_passesResultFilter() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 31, 0, 0);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(42L);

        long count = service.countByResultInRange("SUCCESS", from, to);
        assertEquals(42L, count);
        verify(mapper, times(1)).selectCount(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void countByResultInRange_nullOrBlankResult_skipsFilter() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(100L);

        long count = service.countByResultInRange(null, from, to);
        assertEquals(100L, count);

        long blank = service.countByResultInRange("   ", from, to);
        assertEquals(100L, blank);
    }
}