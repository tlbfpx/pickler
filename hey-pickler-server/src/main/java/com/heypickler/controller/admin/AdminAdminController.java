package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.dto.admin.AdminUserUpdateRequest;
import com.heypickler.dto.admin.PasswordResetRequest;
import com.heypickler.service.AdminUserService;
import com.heypickler.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
@Tag(name = "管理端-管理员管理")
public class AdminAdminController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "管理员列表")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<PageResult<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminUserService.listAdminUsers(page, size));
    }

    @PostMapping
    @Operation(summary = "创建管理员")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<Map<String, Object>> create(@RequestBody @Valid AdminUserCreateRequest body) {
        Long id = adminUserService.createAdminUser(body);
        return Result.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "管理员详情")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<AdminUserVO> get(@PathVariable Long id) {
        return Result.ok(adminUserService.getAdminUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑管理员")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<Void> update(@PathVariable Long id,
                                @RequestBody @Valid AdminUserUpdateRequest body,
                                HttpServletRequest request) {
        Long currentAdminId = (Long) request.getAttribute("adminId");
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能修改自己的角色");
        }
        adminUserService.updateAdminUser(id, body.getRole(), body.getStatus());
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<Void> resetPassword(@PathVariable Long id,
                                       @RequestBody @Valid PasswordResetRequest body,
                                       HttpServletRequest request) {
        Long currentAdminId = (Long) request.getAttribute("adminId");
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能通过此接口重置自己的密码");
        }
        adminUserService.resetPassword(id, body.getNewPassword());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除管理员")
    @RequireRole(UserRole.SUPER_ADMIN)
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long currentAdminId = (Long) request.getAttribute("adminId");
        adminUserService.deleteAdminUser(id, currentAdminId);
        return Result.ok();
    }
}
