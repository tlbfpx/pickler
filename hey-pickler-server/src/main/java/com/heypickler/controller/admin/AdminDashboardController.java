package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.service.DashboardService;
import com.heypickler.vo.AttendanceFunnelVO;
import com.heypickler.vo.CompareResultVO;
import com.heypickler.vo.DashboardTrendVO;
import com.heypickler.vo.TopEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端首页（Loop-v19 Dashboard Phase 1）。
 *
 * <p>原内联聚合逻辑已下沉到 {@link DashboardService}。Controller 仅做路由 + RBAC + 透传
 * {@code no_cache=1} 与当前 role 给 service 决定 cache-aside / bypass。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "管理端-首页")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "首页快照（向后兼容 + 数字 KPI 加 sibling DeltaPct/DeltaAbs）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> getStats(HttpServletRequest request) {
        return Result.ok(dashboardService.getSnapshot(isSuperAdminBypass(request)));
    }

    @GetMapping("/trends")
    @Operation(summary = "时序趋势（user/registration/revenue/events）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<DashboardTrendVO> getTrends(
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        return Result.ok(dashboardService.getTrends(range, from, to, isSuperAdminBypass(request)));
    }

    @GetMapping("/top-events")
    @Operation(summary = "Top 10 活动排行（按 metric=registrations|revenue|fillRate）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<TopEventVO>> getTopEvents(
            @RequestParam(required = false, defaultValue = "registrations") String metric,
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "10") int limit,
            HttpServletRequest request) {
        return Result.ok(dashboardService.getTopEvents(metric, range, from, to, limit, isSuperAdminBypass(request)));
    }

    @GetMapping("/attendance")
    @Operation(summary = "出席漏斗（已报名/已签到/no-show%）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<AttendanceFunnelVO> getAttendance(
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        return Result.ok(dashboardService.getAttendance(range, from, to, isSuperAdminBypass(request)));
    }

    @GetMapping("/compare")
    @Operation(summary = "同比/环比")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<CompareResultVO> getCompare(
            @RequestParam String metric,
            @RequestParam(required = false, defaultValue = "thisMonth") String currentRange,
            @RequestParam(required = false, defaultValue = "lastMonth") String previousRange,
            HttpServletRequest request) {
        return Result.ok(dashboardService.getCompare(metric, currentRange, previousRange, isSuperAdminBypass(request)));
    }

    /**
     * spec R6：bypassCache 仅 SUPER_ADMIN + ?no_cache=1 同时成立时返回 true；普通角色
     * 传 no_cache=1 静默忽略（返回 false）。null request 容错（支持测试桩）。
     */
    private static boolean isSuperAdminBypass(HttpServletRequest request) {
        if (request == null) return false;
        if (!"1".equals(request.getParameter("no_cache"))) return false;
        Object roleAttr = request.getAttribute("adminRole");
        return UserRole.SUPER_ADMIN.name().equals(roleAttr);
    }
}
