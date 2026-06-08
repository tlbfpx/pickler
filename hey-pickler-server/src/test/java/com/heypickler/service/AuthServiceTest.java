package com.heypickler.service;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.AesUtil;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.entity.AdminUser;
import com.heypickler.entity.User;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private AdminUserMapper adminUserMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private AesUtil aesUtil;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "appId", "test-appid");
        ReflectionTestUtils.setField(authService, "appSecret", "test-secret");
    }

    @Test
    void refreshToken_shouldReturnNewToken() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtUtil.generateAppToken(1L)).thenReturn("new-token");

        String token = authService.refreshToken(1L);
        assertEquals("new-token", token);
    }

    @Test
    void refreshToken_userNotFound_shouldThrow() {
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThrows(BizException.class, () -> authService.refreshToken(999L));
    }

    @Test
    void adminLogin_success() {
        AdminUser admin = new AdminUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash("$2a$10$hash");
        admin.setRole("SUPER_ADMIN");
        admin.setStatus("ACTIVE");

        when(adminUserMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("admin123", "$2a$10$hash")).thenReturn(true);
        when(jwtUtil.generateAdminToken(1L, "SUPER_ADMIN")).thenReturn("admin-token");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Map<String, Object> result = authService.adminLogin("admin", "admin123");
        assertEquals("admin-token", result.get("token"));
        assertEquals("SUPER_ADMIN", result.get("role"));
    }

    @Test
    void adminLogin_wrongPassword_shouldThrow() {
        AdminUser admin = new AdminUser();
        admin.setId(1L);
        admin.setPasswordHash("$2a$10$hash");
        admin.setStatus("ACTIVE");
        when(adminUserMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> authService.adminLogin("admin", "wrong"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    void adminLogin_disabledAccount_shouldThrow() {
        AdminUser admin = new AdminUser();
        admin.setPasswordHash("hash");
        admin.setStatus("DISABLED");
        when(adminUserMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> authService.adminLogin("admin", "pass"));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }
}
