package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "管理后台-排名管理")
@RestController
@RequestMapping("/api/admin/rankings")
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingService rankingService;

    @GetMapping("/{type}")
    @Operation(summary = "获取排名列表")
    public Result<PageResult<RankingVO>> getRankings(@PathVariable String type) {
        RankingQuery query = new RankingQuery();
        query.setType(type.toUpperCase());
        query.setPage(1);
        query.setSize(100);
        return Result.ok(rankingService.getRankings(query));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新排名")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> refreshRankings(@RequestBody(required = false) Map<String, String> body) {
        String type = body != null ? body.getOrDefault("type", "STAR") : "STAR";
        rankingService.refreshRankings(type);
        return Result.ok();
    }

    @PostMapping("/points")
    @Operation(summary = "录入积分")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> enterPoints(
            HttpServletRequest request,
            @Valid @RequestBody PointEntryRequest req) {
        Long adminId = (Long) request.getAttribute("adminId");
        rankingService.enterPoints(null, req, adminId);
        return Result.ok();
    }
}
