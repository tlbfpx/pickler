package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.entity.User;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppAuthFilterTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private ObjectMapper objectMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private AppAuthFilter filter;

    @Test
    void shouldNotFilter_excludedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/app/auth/login");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_nonAppPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/users");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilter_appProtectedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/app/user/profile");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void doFilter_validToken_setsUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/app/user/profile");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.validate("valid-token")).thenReturn(true);
        when(jwtUtil.getType("valid-token")).thenReturn("app");
        when(jwtUtil.getUserId("valid-token")).thenReturn(123L);

        // AppAuthFilter 会查 user 是否存在且未封禁
        User user = new User();
        user.setId(123L);
        user.setStatus("NORMAL");
        when(userMapper.selectById(123L)).thenReturn(user);

        // Use a spy to skip the filter chain
        filter.doFilterInternal(request, response, (req, res) -> {});
        assertEquals(123L, request.getAttribute("userId"));
    }
}
