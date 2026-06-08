package com.heypickler.controller.admin;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.AdminUserCreateRequest;
import com.heypickler.entity.AdminUser;
import com.heypickler.service.AdminUserService;
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
    public Result<PageResult<AdminUser>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        checkSuperAdmin(request);
        return Result.ok(adminUserService.listAdminUsers(page, size));
    }

    @PostMapping
    @Operation(summary = "创建管理员")
    public Result<Map<String, Object>> create(@RequestBody @Valid AdminUserCreateRequest body,
                                                HttpServletRequest request) {
        checkSuperAdmin(request);
        Long id = adminUserService.createAdminUser(body);
        return Result.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "管理员详情")
    public Result<AdminUser> get(@PathVariable Long id, HttpServletRequest request) {
        checkSuperAdmin(request);
        return Result.ok(adminUserService.getAdminUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑管理员")
    public Result<Void> update(@PathVariable Long id,
                                @RequestBody Map<String, String> body,
                                HttpServletRequest request) {
        checkSuperAdmin(request);
        Long currentAdminId = (Long) request.getAttribute("adminId");
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能修改自己的角色");
        }
        adminUserService.updateAdminUser(id, body.get("role"), body.get("status"));
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        checkSuperAdmin(request);
        Long currentAdminId = (Long) request.getAttribute("adminId");
        if (id.equals(currentAdminId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能通过此接口重置自己的密码");
        }
        adminUserService.resetPassword(id, body.get("newPassword"));
        return Result.ok();
    }

    private void checkSuperAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("adminRole");
        if (!"SUPER_ADMIN".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
