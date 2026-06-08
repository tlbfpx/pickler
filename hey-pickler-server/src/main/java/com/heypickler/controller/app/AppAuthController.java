package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.AuthService;
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

    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<Map<String, Object>> login(@RequestBody WxLoginRequest request) {
        return Result.ok(authService.appLogin(request.getCode()));
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
