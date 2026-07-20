package com.heypickler.controller.admin;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.entity.LoginLog;
import com.heypickler.service.AuthService;
import com.heypickler.service.LoginLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loop-v19 Dashboard Phase 2 — AdminAuthController.login 路径改造单测。
 *
 * <p>覆盖：成功写 SUCCESS + adminId；BizException FAIL_BANNED / FAIL_PWD；
 * RuntimeException FAIL_OTHER。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerTest {

    @Mock AuthService authService;
    @Mock LoginLogService loginLogService;
    @InjectMocks AdminAuthController controller;

    @Test
    void login_success_writesSuccessLogWithAdminId() {
        AdminAuthController.AdminLoginBody body = new AdminAuthController.AdminLoginBody();
        body.setUsername("admin");
        body.setPassword("admin123");
        when(authService.adminLogin(anyString(), anyString()))
                .thenReturn(Map.of("token", "jwt-admin", "adminId", 1L));

        Result<Map<String, Object>> result = controller.login(body, new MockHttpServletRequest("POST", "/api/admin/auth/login"));

        assertEquals(0, result.getCode());
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        LoginLog log = cap.getValue();
        assertEquals("ADMIN", log.getChannel());
        assertEquals("SUCCESS", log.getLoginResult());
        assertEquals(1L, log.getAdminId());
    }

    @Test
    void login_disabledAccount_writesFailBanned() {
        AdminAuthController.AdminLoginBody body = new AdminAuthController.AdminLoginBody();
        body.setUsername("disabled");
        body.setPassword("xxx");
        when(authService.adminLogin(anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "账号已被禁用"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/admin/auth/login");

        assertThrows(BizException.class, () -> controller.login(body, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_BANNED", cap.getValue().getLoginResult());
    }

    @Test
    void login_wrongPassword_writesFailPwd() {
        AdminAuthController.AdminLoginBody body = new AdminAuthController.AdminLoginBody();
        body.setUsername("admin");
        body.setPassword("wrong");
        when(authService.adminLogin(anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/admin/auth/login");

        assertThrows(BizException.class, () -> controller.login(body, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_PWD", cap.getValue().getLoginResult());
    }

    @Test
    void login_rateLimited_writesFailRateLimit() {
        AdminAuthController.AdminLoginBody body = new AdminAuthController.AdminLoginBody();
        body.setUsername("hot");
        body.setPassword("hot");
        when(authService.adminLogin(anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "请求过于频繁，请稍后"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/admin/auth/login");

        assertThrows(BizException.class, () -> controller.login(body, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_RATE_LIMIT", cap.getValue().getLoginResult());
    }

    @Test
    void login_runtimeException_writesFailOther() {
        AdminAuthController.AdminLoginBody body = new AdminAuthController.AdminLoginBody();
        body.setUsername("admin");
        body.setPassword("admin123");
        when(authService.adminLogin(anyString(), anyString())).thenThrow(new RuntimeException("redis down"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/admin/auth/login");

        assertThrows(RuntimeException.class, () -> controller.login(body, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_OTHER", cap.getValue().getLoginResult());
    }
}