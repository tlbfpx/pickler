package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.service.RankingService;
import com.heypickler.vo.AppRankingPageVO;
import com.heypickler.vo.RankingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "APP - 排行榜")
@RestController
@RequestMapping("/api/app/rankings")
@RequiredArgsConstructor
public class AppRankingController {

    private final RankingService rankingService;

    @Operation(summary = "获取排行榜")
    @GetMapping
    public Result<AppRankingPageVO> getRankings(RankingQuery query) {
        // 榜单分页（list 平铺顶层，不破坏 wxapp res.data.list）+ 当前 track 段位名映射（供段位筛选 tab 双轨）
        PageResult<RankingVO> page = rankingService.getRankings(query);
        return Result.ok(AppRankingPageVO.of(page, rankingService.tierNameMap(query.getType())));
    }

    @Operation(summary = "获取前5名")
    @GetMapping("/top5")
    public Result<List<RankingVO>> getTop5(@RequestParam String type) {
        return Result.ok(rankingService.getTop5(type));
    }
}
