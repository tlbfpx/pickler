package com.heypickler.controller.app;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.result.Result;
import com.heypickler.common.util.IpResolver;
import com.heypickler.entity.LoginLog;
import com.heypickler.service.AuthService;
import com.heypickler.service.LoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
@Tag(name = "小程序-认证")
public class AppAuthController {

    private final AuthService authService;
    private final LoginLogService loginLogService;

    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<Map<String, Object>> login(@RequestBody WxLoginRequest request,
                                              HttpServletRequest httpRequest) {
        try {
            Map<String, Object> result = authService.appLogin(request.getCode());
            // 成功路径：从 result 里拿 userId（AuthService.appLogin 返回值含 userId）
            Object userIdObj = result.get("userId");
            LoginLog log = baseLog(httpRequest, "APP", "SUCCESS", null);
            if (userIdObj instanceof Number n) log.setUserId(n.longValue());
            loginLogService.recordLogin(log);
            return Result.ok(result);
        } catch (BizException e) {
            LoginLog log = baseLog(httpRequest, "APP", mapBizToResult(e), String.valueOf(e.getCode()));
            loginLogService.recordLogin(log);
            throw e;
        } catch (RuntimeException e) {
            LoginLog log = baseLog(httpRequest, "APP", "FAIL_OTHER", null);
            loginLogService.recordLogin(log);
            throw e;
        }
    }

    /** BizException → login_result 枚举（基于 message 关键词，避免依赖 ErrorCode name 字符串）。 */
    private static String mapBizToResult(BizException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("禁用") || msg.contains("封禁") || msg.contains("BANNED")) return "FAIL_BANNED";
        if (msg.contains("频繁") || msg.contains("限流") || msg.contains("RATE")) return "FAIL_RATE_LIMIT";
        if (msg.contains("微信") || msg.contains("会话") || msg.contains("code")) return "FAIL_INVALID_CODE";
        if (msg.contains("密码") || msg.contains("密码错误")) return "FAIL_PWD";
        return "FAIL_OTHER";
    }

    private static LoginLog baseLog(HttpServletRequest req, String channel, String result, String errorCode) {
        LoginLog log = new LoginLog();
        log.setChannel(channel);
        log.setLoginResult(result);
        log.setErrorCode(errorCode);
        log.setIp(IpResolver.resolveIp(req));
        String ua = req == null ? null : req.getHeader("User-Agent");
        if (ua != null && ua.length() > 256) ua = ua.substring(0, 256);
        log.setUserAgent(ua);
        // deviceId / did：Phase 2 由 wxapp 通过 track/event 后续上报时落库；
        // 这里若 header 带 X-Device-Id 也透传（兼容未来其他渠道）
        if (req != null) {
            String did = req.getHeader("X-Device-Id");
            if (did != null && !did.isBlank()) {
                log.setDeviceId(did.length() > 64 ? did.substring(0, 64) : did);
            }
        }
        return log;
    }

    @PostMapping("/phone")
    @Operation(summary = "绑定手机号")
    public Result<Void> bindPhone(HttpServletRequest httpRequest,
                                   @RequestBody PhoneBindRequest request) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        authService.bindPhone(userId, request.getEncryptedData(), request.getIv());
        return Result.ok();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token")
    public Result<Map<String, Object>> refresh(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String token = authService.refreshToken(userId);
        return Result.ok(Map.of("token", token));
    }

    @Data
    public static class WxLoginRequest {
        @NotBlank(message = "code不能为空")
        private String code;
    }

    @Data
    public static class PhoneBindRequest {
        @NotBlank(message = "加密数据不能为空")
        private String encryptedData;
        @NotBlank(message = "iv不能为空")
        private String iv;
    }
}