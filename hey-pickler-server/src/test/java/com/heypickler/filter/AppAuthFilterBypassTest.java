package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AppAuthFilterBypassTest {

    private final AppAuthFilter filter =
            new AppAuthFilter(mock(JwtUtil.class), new ObjectMapper(), mock(UserMapper.class));

    @Test
    void dictBundleGetIsBypassed() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/dict/bundle");
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void dictPostIsNotBypassed() {
        // bypass 仅限 GET；POST 仍走鉴权
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/app/dict/bundle");
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void adminPathIsBypassed() {
        // 非 /api/app/ 前缀整体跳过（AppAuthFilter 只管 app 端）
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/dict");
        assertTrue(filter.shouldNotFilter(req));
    }
}
