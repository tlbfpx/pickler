package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.PointSource;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Season;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.service.PointService;
import com.heypickler.service.RankingService;
import com.heypickler.service.dto.PointEntry;
import com.heypickler.vo.RankingPageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "管理后台-排名管理")
@RestController
@RequestMapping("/api/admin/rankings")
@RequiredArgsConstructor
public class AdminRankingController {

    private final RankingService rankingService;
    private final PointService pointService;
    private final SeasonMapper seasonMapper;

    @GetMapping("/{type}")
    @Operation(summary = "获取排名工作台（榜单分页+段位分布+当前赛季元信息）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<RankingPageVO> getRankings(
            @PathVariable String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        RankingQuery query = new RankingQuery();
        query.setType(type.toUpperCase());
        query.setKeyword(keyword);
        query.setTier(tier != null ? tier.toUpperCase() : null);
        query.setPage(page);
        query.setSize(size);
        return Result.ok(rankingService.getRankingPage(query));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新当前赛季排名")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> refreshRankings(@RequestBody(required = false) Map<String, String> body) {
        String type = body != null ? body.getOrDefault("type", "STAR") : "STAR";
        type = type.toUpperCase();
        String seasonCode = resolveCurrentSeasonCode(type);
        rankingService.refreshRankings(type, seasonCode);
        return Result.ok();
    }

    @PostMapping("/points")
    @Operation(summary = "录入积分（手动发分，强制 MANUAL 来源）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> enterPoints(
            HttpServletRequest request,
            @Valid @RequestBody PointEntryRequest req) {
        Long adminId = (Long) request.getAttribute("adminId");
        // 手动发分：强制 MANUAL 来源，不读请求体 source
        pointService.enterPoints(req.getEventId(), req.getType(), toPointEntries(req), PointSource.MANUAL, adminId);
        return Result.ok();
    }

    @PostMapping("/points/{recordId}/revert")
    @Operation(summary = "撤销单条积分记录（写 ADJUST 补偿行，仅限 MANUAL/ADJUST）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> revertPointRecord(
            HttpServletRequest request,
            @PathVariable Long recordId) {
        Long adminId = (Long) request.getAttribute("adminId");
        pointService.revertPointRecord(recordId, adminId);
        return Result.ok();
    }

    private String resolveCurrentSeasonCode(String type) {
        Season season = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, type)
                .eq(Season::getStatus, "CURRENT"));
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + type);
        }
        return season.getCode();
    }

    private List<PointEntry> toPointEntries(PointEntryRequest req) {
        return req.getRecords().stream()
                .map(item -> new PointEntry(item.getUserId(), item.getPoints(), item.getReason()))
                .collect(java.util.stream.Collectors.toList());
    }
}
