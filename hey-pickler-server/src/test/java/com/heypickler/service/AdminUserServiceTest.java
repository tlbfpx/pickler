package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import com.heypickler.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new AdminUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setPasswordHash("$2a$10$encodedPassword");
        adminUser.setRole("SUPER_ADMIN");
        adminUser.setStatus("ACTIVE");
    }

    @Test
    void createAdminUser_Success_ShouldReturnId() {
        // Given
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("newadmin");
        request.setPassword("password123");
        request.setRole("ADMIN");

        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });

        // When
        Long id = adminUserService.createAdminUser(request);

        // Then
        assertEquals(100L, id);
        verify(adminUserMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(passwordEncoder).encode("password123");
        verify(adminUserMapper).insert(argThat(user ->
            user.getUsername().equals("newadmin") &&
            user.getPasswordHash().equals("$2a$10$encoded") &&
            user.getRole().equals("ADMIN") &&
            user.getStatus().equals("ACTIVE")
        ));
    }

    @Test
    void createAdminUser_DuplicateUsername_ShouldThrowException() {
        // Given
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("existingadmin");
        request.setPassword("password123");
        request.setRole("ADMIN");

        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            adminUserService.createAdminUser(request);
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("用户名已存在"));
        verify(adminUserMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(adminUserMapper, never()).insert(any());
    }

    @Test
    void createAdminUser_InvalidRole_ShouldThrowException() {
        // Given
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("newadmin");
        request.setPassword("password123");
        request.setRole("INVALID_ROLE");

        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            adminUserService.createAdminUser(request);
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("无效的角色"));
        verify(adminUserMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(adminUserMapper, never()).insert(any());
    }

    @Test
    void createAdminUser_ValidRoles_ShouldSucceed() {
        // Given - SUPER_ADMIN
        AdminUserCreateRequest request1 = new AdminUserCreateRequest();
        request1.setUsername("super");
        request1.setPassword("pass");
        request1.setRole("SUPER_ADMIN");

        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encoded");
        when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        // When
        Long id1 = adminUserService.createAdminUser(request1);

        // Then
        assertEquals(1L, id1);

        // Given - ADMIN
        AdminUserCreateRequest request2 = new AdminUserCreateRequest();
        request2.setUsername("admin2");
        request2.setPassword("pass");
        request2.setRole("ADMIN");

        when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser user = invocation.getArgument(0);
            user.setId(2L);
            return 1;
        });

        // When
        Long id2 = adminUserService.createAdminUser(request2);

        // Then
        assertEquals(2L, id2);

        // Given - OPERATOR
        AdminUserCreateRequest request3 = new AdminUserCreateRequest();
        request3.setUsername("operator");
        request3.setPassword("pass");
        request3.setRole("OPERATOR");

        when(adminUserMapper.insert(any(AdminUser.class))).thenAnswer(invocation -> {
            AdminUser user = invocation.getArgument(0);
            user.setId(3L);
            return 1;
        });

        // When
        Long id3 = adminUserService.createAdminUser(request3);

        // Then
        assertEquals(3L, id3);
    }

    @Test
    void resetPassword_Success_ShouldUpdateHashAndClearSession() {
        // Given
        Long adminId = 1L;
        String newPassword = "newPassword123";

        when(adminUserMapper.selectById(adminId)).thenReturn(adminUser);
        when(passwordEncoder.encode(newPassword)).thenReturn("$2a$10$newEncoded");
        when(adminUserMapper.updateById(any(AdminUser.class))).thenReturn(1);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // When
        adminUserService.resetPassword(adminId, newPassword);

        // Then
        verify(adminUserMapper).selectById(adminId);
        verify(passwordEncoder).encode(newPassword);
        verify(adminUserMapper).updateById(argThat(user ->
            user.getId().equals(adminId) &&
            user.getPasswordHash().equals("$2a$10$newEncoded")
        ));
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void resetPassword_UserNotFound_ShouldThrowException() {
        // Given
        Long adminId = 999L;
        String newPassword = "newPassword123";

        when(adminUserMapper.selectById(adminId)).thenReturn(null);

        // When & Then
        BizException exception = assertThrows(BizException.class, () -> {
            adminUserService.resetPassword(adminId, newPassword);
        });

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(adminUserMapper).selectById(adminId);
        verify(adminUserMapper, never()).updateById(any());
        verify(redisTemplate, never()).delete(anyString());
    }
}
