package com.heypickler.controller.admin;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.UserService;
import com.heypickler.vo.UserAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "管理端-用户")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "获取用户列表")
    public Result<PageResult<UserAdminVO>> listUsers(UserQueryRequest request) {
        return Result.ok(userService.adminListUsers(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    public Result<UserAdminVO> getUser(@PathVariable Long id) {
        return Result.ok(userService.adminGetUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息")
    public Result<Void> updateUser(@PathVariable Long id,
                                   @RequestBody UserUpdateRequest request) {
        userService.adminUpdateUser(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/ban")
    @Operation(summary = "封禁用户")
    public Result<Void> banUser(HttpServletRequest request,
                                 @PathVariable Long id,
                                 @Valid @RequestBody BanRequest banRequest) {
        Long adminId = (Long) request.getAttribute("adminId");
        userService.banUser(id, adminId, banRequest);
        return Result.ok();
    }

    @PostMapping("/{id}/unban")
    @Operation(summary = "解封用户")
    public Result<Void> unbanUser(HttpServletRequest request,
                                   @PathVariable Long id) {
        Long adminId = (Long) request.getAttribute("adminId");
        userService.unbanUser(id, adminId);
        return Result.ok();
    }
}
