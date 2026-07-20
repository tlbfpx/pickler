package com.heypickler.filter;

import com.heypickler.entity.AccessLog;
import com.heypickler.service.AccessLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Loop-v19 Dashboard Phase 2 — AccessLogFilter 单测。
 *
 * <p>覆盖：① /api/** 进入记录 ② /actuator/health 跳过 ③ 鉴权后 userId
 * 通过 request.getAttribute 拿到 ④ recordAccess 抛异常时 filter 不传播
 * （catch all） ⑤ 401 也能记录（finally 块覆盖 chain.doFilter 抛异常场景）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessLogFilterTest {

    @Mock AccessLogService accessLogService;
    @Mock FilterChain chain;

    @Test
    void shouldNotFilter_nonApiPath_returnsTrue() {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertTrue(filter.shouldNotFilter(req), "/actuator/health 必须跳过");
    }

    @Test
    void shouldNotFilter_apiPath_returnsFalse() {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        assertFalse(filter.shouldNotFilter(req), "/api/** 不能跳过");
    }

    @Test
    void doFilter_records200_withAuthedUserId() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        req.setAttribute("userId", 42L);
        req.addHeader("X-Forwarded-For", "203.0.113.5");
        req.addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog log = cap.getValue();
        assertEquals("/api/app/events", log.getPath());
        assertEquals("GET", log.getMethod());
        assertEquals(200, log.getStatusCode());
        assertEquals(42L, log.getUserId());
        assertNull(log.getAdminId());
        assertEquals("203.0.113.5", log.getIp());
        assertTrue(log.getLatencyMs() >= 0);
    }

    @Test
    void doFilter_records200_withAdminId() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/admin/events");
        req.setAttribute("adminId", 7L);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(201);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog log = cap.getValue();
        assertEquals(7L, log.getAdminId());
        assertNull(log.getUserId());
        assertEquals(201, log.getStatusCode());
    }

    @Test
    void doFilter_anonymousRequest_recordsWithNullUser() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog log = cap.getValue();
        assertNull(log.getUserId());
        assertNull(log.getAdminId());
    }

    @Test
    void doFilter_recordAccessThrows_swallowsAndStillReturns() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        doThrow(new RuntimeException("DB down")).when(accessLogService).recordAccess(any(AccessLog.class));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        // 不抛 — 原请求响应不受影响
        filter.doFilter(req, resp, chain);
        verify(chain, times(1)).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    void doFilter_chainThrowsException_stillRecords() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        doAnswer(invocation -> { throw new RuntimeException("controller boom"); })
                .when(chain).doFilter(any(), any());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        try {
            filter.doFilter(req, resp, chain);
        } catch (RuntimeException expected) {
            // filter 让 chain 异常透传（Spring 框架兜底），但 finally 块仍记日志
        }
        verify(accessLogService, times(1)).recordAccess(any(AccessLog.class));
    }

    @Test
    void doFilter_truncatesLongUserAgent() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        StringBuilder longUa = new StringBuilder();
        for (int i = 0; i < 400; i++) longUa.append('x');
        req.addHeader("User-Agent", longUa.toString());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog log = cap.getValue();
        assertEquals(256, log.getUserAgent().length(), "UA 必须截断到 256 字符");
    }

    @Test
    void doFilter_noForwardedFor_usesRemoteAddr() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog log = cap.getValue();
        assertEquals("10.0.0.1", log.getIp());
    }

    @Test
    void doFilter_multiHopXffTakesFirst() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/events");
        req.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setStatus(200);

        filter.doFilter(req, resp, chain);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        assertEquals("1.1.1.1", cap.getValue().getIp());
    }

    @Test
    void doFilter_shouldNotFilterSkipsRecord() throws Exception {
        AccessLogFilter filter = new AccessLogFilter(accessLogService);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(accessLogService, never()).recordAccess(any(AccessLog.class));
    }
}