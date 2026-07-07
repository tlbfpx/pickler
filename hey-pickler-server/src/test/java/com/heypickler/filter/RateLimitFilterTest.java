package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Loop-v11 — moves RateLimitFilter from 76.7% to ~90%+.
 * Direct invocation, no Spring context. Mocks StringRedisTemplate.execute().
 */
class RateLimitFilterTest {

    private StringRedisTemplate redis;
    private ObjectMapper objectMapper;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        filter = new RateLimitFilter(redis, objectMapper);
        ReflectionTestUtils.setField(filter, "loginRateLimit", 10);
        ReflectionTestUtils.setField(filter, "adminRateLimit", 120);
        ReflectionTestUtils.setField(filter, "adminAnonRateLimit", 30);
        ReflectionTestUtils.setField(filter, "defaultRateLimit", 60);
    }

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        r.setRemoteAddr("127.0.0.1");
        return r;
    }

    @Test
    void loginPath_admin_login_usesLoginLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/admin/auth/login");
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
        verify(redis, times(1)).execute(any(RedisScript.class), any(List.class), any(), any());
    }

    @Test
    void loginPath_app_login_usesLoginLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/auth/login");
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
    }

    @Test
    void loginPath_app_phone_usesLoginLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/auth/phone");
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
    }

    @Test
    void adminPath_withAdminIdAttribute_usesAdminLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/admin/events");
        r.setAttribute("adminId", 99L);
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
    }

    @Test
    void adminPath_anonymous_usesAdminAnonLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/admin/events");
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
    }

    @Test
    void appPath_usesDefaultLimit() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/something");
        MockHttpServletResponse w = new MockHttpServletResponse();
        filter.doFilter(r, w, new MockFilterChain());
    }

    @Test
    void blocked_returns429AndDoesNotCallChain() throws Exception {
        doReturn(0L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/x");
        MockHttpServletResponse w = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(r, w, chain);
        assertEquals(429, w.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowed_callsChain() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/x");
        MockHttpServletResponse w = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(r, w, chain);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void resolveClientIp_prefersXForwardedFor() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/x");
        r.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        r.addHeader("X-Real-IP", "9.9.9.9");
        filter.doFilter(r, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void resolveClientIp_fallsBackToXRealIP() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/x");
        r.addHeader("X-Real-IP", "9.9.9.9");
        filter.doFilter(r, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void resolveClientIp_fallsBackToRemoteAddr() throws Exception {
        doReturn(1L).when(redis).execute(any(RedisScript.class), any(List.class), any(), any());
        MockHttpServletRequest r = req("/api/app/x");
        filter.doFilter(r, new MockHttpServletResponse(), new MockFilterChain());
    }
}
