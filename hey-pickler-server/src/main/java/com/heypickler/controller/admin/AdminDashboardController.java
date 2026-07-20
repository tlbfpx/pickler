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
    @Operation(summary = "首页快照数据（向后兼容 + 每个数字 KPI 加 sibling DeltaPct/DeltaAbs 字段）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Object>> getStats() {
        return Result.ok(dashboardService.getSnapshot());
    }

    @GetMapping("/trends")
    @Operation(summary = "时序趋势（user/registration/revenue/events，4 条时序）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<DashboardTrendVO> getTrends(
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.getTrends(range, from, to));
    }

    @GetMapping("/top-events")
    @Operation(summary = "Top 10 活动排行（按 metric=registrations|revenue|fillRate）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<TopEventVO>> getTopEvents(
            @RequestParam(required = false, defaultValue = "registrations") String metric,
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return Result.ok(dashboardService.getTopEvents(metric, range, from, to, limit));
    }

    @GetMapping("/attendance")
    @Operation(summary = "出席漏斗（已报名/已签到/no-show%）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<AttendanceFunnelVO> getAttendance(
            @RequestParam(required = false, defaultValue = "30d") String range,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.getAttendance(range, from, to));
    }

    @GetMapping("/compare")
    @Operation(summary = "同比/环比")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<CompareResultVO> getCompare(
            @RequestParam String metric,
            @RequestParam(required = false, defaultValue = "thisMonth") String currentRange,
            @RequestParam(required = false, defaultValue = "lastMonth") String previousRange) {
        return Result.ok(dashboardService.getCompare(metric, currentRange, previousRange));
    }
}
