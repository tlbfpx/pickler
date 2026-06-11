package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BanRequest;
import com.heypickler.dto.admin.UserQueryRequest;
import com.heypickler.dto.app.UserUpdateRequest;
import com.heypickler.service.UserService;
import com.heypickler.vo.MyEventVO;
import com.heypickler.vo.PointRecordVO;
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
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<UserAdminVO>> listUsers(UserQueryRequest request) {
        return Result.ok(userService.adminListUsers(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<UserAdminVO> getUser(@PathVariable Long id) {
        return Result.ok(userService.adminGetUser(id));
    }

    @GetMapping("/{id}/points")
    @Operation(summary = "用户积分明细")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<PointRecordVO>> getPointHistory(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getPointHistory(id, type, page, size));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "用户参赛记录")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<MyEventVO>> getEventHistory(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getMyEvents(id, type, page, size));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateUser(@PathVariable Long id,
                                   @RequestBody UserUpdateRequest request) {
        userService.adminUpdateUser(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/ban")
    @Operation(summary = "封禁用户")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> banUser(HttpServletRequest request,
                                 @PathVariable Long id,
                                 @Valid @RequestBody BanRequest banRequest) {
        Long adminId = (Long) request.getAttribute("adminId");
        userService.banUser(id, adminId, banRequest);
        return Result.ok();
    }

    @PostMapping("/{id}/unban")
    @Operation(summary = "解封用户")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> unbanUser(HttpServletRequest request,
                                   @PathVariable Long id) {
        Long adminId = (Long) request.getAttribute("adminId");
        userService.unbanUser(id, adminId);
        return Result.ok();
    }
}
