package com.heypickler.controller.admin;

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
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "管理端-认证")
public class AdminAuthController {

    private final AuthService authService;
    private final LoginLogService loginLogService;

    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public Result<Map<String, Object>> login(@RequestBody AdminLoginBody request,
                                             HttpServletRequest httpRequest) {
        try {
            Map<String, Object> result = authService.adminLogin(request.getUsername(), request.getPassword());
            Object adminIdObj = result.get("adminId");
            LoginLog log = baseLog(httpRequest, "ADMIN", "SUCCESS", null);
            if (adminIdObj instanceof Number n) log.setAdminId(n.longValue());
            loginLogService.recordLogin(log);
            return Result.ok(result);
        } catch (BizException e) {
            LoginLog log = baseLog(httpRequest, "ADMIN", mapBizToResult(e), String.valueOf(e.getCode()));
            loginLogService.recordLogin(log);
            throw e;
        } catch (RuntimeException e) {
            LoginLog log = baseLog(httpRequest, "ADMIN", "FAIL_OTHER", null);
            loginLogService.recordLogin(log);
            throw e;
        }
    }

    private static String mapBizToResult(BizException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("禁用") || msg.contains("封禁")) return "FAIL_BANNED";
        if (msg.contains("频繁") || msg.contains("限流") || msg.contains("RATE")) return "FAIL_RATE_LIMIT";
        if (msg.contains("密码")) return "FAIL_PWD";
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
        return log;
    }

    @Data
    public static class AdminLoginBody {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }
}