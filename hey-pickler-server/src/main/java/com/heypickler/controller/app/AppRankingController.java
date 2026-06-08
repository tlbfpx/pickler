package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.service.RankingService;
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
    public Result<PageResult<RankingVO>> getRankings(RankingQuery query) {
        return Result.ok(rankingService.getRankings(query));
    }

    @Operation(summary = "获取前5名")
    @GetMapping("/top5")
    public Result<List<RankingVO>> getTop5(@RequestParam String type) {
        return Result.ok(rankingService.getTop5(type));
    }
}
