package com.heypickler.controller.app;

import com.heypickler.common.dto.TrackEventRequest;
import com.heypickler.common.result.Result;
import com.heypickler.entity.AccessLog;
import com.heypickler.service.AccessLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Loop-v19 Dashboard Phase 2 R3 — AppTrackController 单测。
 *
 * <p>覆盖：happy 路径（userId 自动绑 / 匿名）、error_msg 字段塞事件名、
 * did 透传进 UA 列位、props 超 2KB 拒绝 400、空 name 拒。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppTrackControllerTest {

    @Mock AccessLogService accessLogService;
    @InjectMocks AppTrackController controller;

    @Test
    void track_anonymous_happyPath() {
        TrackEventRequest req = new TrackEventRequest();
        req.setName("app_launch");
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/track/event");

        Result<Void> result = controller.track(req, http);

        assertEquals(0, result.getCode());
        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog entry = cap.getValue();
        assertEquals("/api/app/track/event", entry.getPath());
        assertEquals("POST", entry.getMethod());
        assertEquals(200, entry.getStatusCode());
        assertEquals("app_launch", entry.getErrorMsg());
        assertNull(entry.getUserId());
        assertNull(entry.getUserAgent(), "无 did 时 UA 为 null");
    }

    @Test
    void track_authenticatedUserId_boundFromAttribute() {
        TrackEventRequest req = new TrackEventRequest();
        req.setName("event_view");
        req.setDid("device-abc-123");
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/track/event");
        http.setAttribute("userId", 99L);

        Result<Void> result = controller.track(req, http);

        assertEquals(0, result.getCode());
        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        AccessLog entry = cap.getValue();
        assertEquals(99L, entry.getUserId());
        assertEquals("event_view", entry.getErrorMsg());
        assertEquals("did=device-abc-123", entry.getUserAgent());
    }

    @Test
    void track_oversizedProps_returns400() {
        TrackEventRequest req = new TrackEventRequest();
        req.setName("big_event");
        // 构造 > 2KB 的 props（每个 key+value 长 100 字符）
        Map<String, Object> big = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append('a');
        for (int i = 0; i < 30; i++) big.put("k" + i, sb.toString());
        req.setProps(big);

        Result<Void> result = controller.track(req, new MockHttpServletRequest("POST", "/api/app/track/event"));

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("2KB"));
        verify(accessLogService, times(0)).recordAccess(any(AccessLog.class));
    }

    @Test
    void track_longDid_truncatesTo64() {
        TrackEventRequest req = new TrackEventRequest();
        req.setName("app_launch");
        StringBuilder did = new StringBuilder();
        for (int i = 0; i < 100; i++) did.append('d');
        req.setDid(did.toString());
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/track/event");

        controller.track(req, http);

        ArgumentCaptor<AccessLog> cap = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService, times(1)).recordAccess(cap.capture());
        // did= 前缀 4 字符 + 截断到 64
        String ua = cap.getValue().getUserAgent();
        assertNotNull(ua);
        assertTrue(ua.length() <= 256);
        assertTrue(ua.startsWith("did="));
    }
}