package com.heypickler.controller.app;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loop-v19 Dashboard Phase 2 — AppAuthController.login 路径改造单测。
 *
 * <p>覆盖：成功路径写 SUCCESS 日志；BizException 4 种枚举映射；
 * RuntimeException 写 FAIL_OTHER + 重抛。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppAuthControllerTest {

    @Mock AuthService authService;
    @Mock LoginLogService loginLogService;
    @InjectMocks AppAuthController controller;

    @Test
    void login_success_writesSuccessLog() {
        AppAuthController.WxLoginRequest req = new AppAuthController.WxLoginRequest();
        req.setCode("valid-code");
        when(authService.appLogin("valid-code")).thenReturn(Map.of("token", "jwt-xxx", "userId", 7L));

        Result<Map<String, Object>> result = controller.login(req, new MockHttpServletRequest("POST", "/api/app/auth/login"));

        assertEquals(0, result.getCode());
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        LoginLog log = cap.getValue();
        assertEquals(7L, log.getUserId());
        assertEquals("APP", log.getChannel());
        assertEquals("SUCCESS", log.getLoginResult());
        assertEquals(null, log.getErrorCode());
    }

    @Test
    void login_banned_writesFailBanned() {
        AppAuthController.WxLoginRequest req = new AppAuthController.WxLoginRequest();
        req.setCode("banned-code");
        when(authService.appLogin(any()))
                .thenThrow(new BizException(ErrorCode.USER_BANNED, "用户已被封禁，无法登录"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/auth/login");

        assertThrows(BizException.class, () -> controller.login(req, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        LoginLog log = cap.getValue();
        assertEquals("FAIL_BANNED", log.getLoginResult());
        assertNotNull(log.getErrorCode());
    }

    @Test
    void login_rateLimited_writesFailRateLimit() {
        AppAuthController.WxLoginRequest req = new AppAuthController.WxLoginRequest();
        req.setCode("hot-code");
        when(authService.appLogin(any()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "请求过于频繁"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/auth/login");

        assertThrows(BizException.class, () -> controller.login(req, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        LoginLog log = cap.getValue();
        assertEquals("FAIL_RATE_LIMIT", log.getLoginResult());
    }

    @Test
    void login_invalidWxCode_writesFailInvalidCode() {
        AppAuthController.WxLoginRequest req = new AppAuthController.WxLoginRequest();
        req.setCode("invalid-code");
        when(authService.appLogin(any()))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "微信登录失败: code 无效"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/auth/login");

        assertThrows(BizException.class, () -> controller.login(req, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_INVALID_CODE", cap.getValue().getLoginResult());
    }

    @Test
    void login_runtimeException_writesFailOther() {
        AppAuthController.WxLoginRequest req = new AppAuthController.WxLoginRequest();
        req.setCode("boom");
        when(authService.appLogin(any())).thenThrow(new RuntimeException("DB connection lost"));
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/api/app/auth/login");

        assertThrows(RuntimeException.class, () -> controller.login(req, http));
        ArgumentCaptor<LoginLog> cap = ArgumentCaptor.forClass(LoginLog.class);
        verify(loginLogService, times(1)).recordLogin(cap.capture());
        assertEquals("FAIL_OTHER", cap.getValue().getLoginResult());
    }
}