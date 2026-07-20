package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端首页（Loop-v19 Dashboard Phase 1）。
 *
 * <p>原内联聚合逻辑（KPI / tier 分布 / recent / 30-day 趋势 / 收入）已下沉到
 * {@link DashboardService}。Controller 仅做路由 + RBAC + 返回。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理端-首页")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "首页统计数据（向后兼容 + 每个数字 KPI 加 deltaPct/deltaAbs）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> getStats() {
        return Result.ok(dashboardService.getSnapshot());
    }
}
