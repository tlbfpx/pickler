package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.SeasonCreateRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.service.SeasonService;
import com.heypickler.vo.RankingPageVO;
import com.heypickler.vo.SeasonVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seasons")
@RequiredArgsConstructor
@Tag(name = "管理端-赛季管理")
public class AdminSeasonController {

    private final SeasonService seasonService;

    @GetMapping
    @Operation(summary = "赛季列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<SeasonVO>> list(@RequestParam(required = false) String type) {
        return Result.ok(seasonService.list(type));
    }

    @PostMapping
    @Operation(summary = "新建赛季")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<SeasonVO> create(@RequestBody @Valid SeasonCreateRequest request) {
        return Result.ok(seasonService.create(request));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "切换赛季为当前（同类型归档原 CURRENT）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> activate(@PathVariable Long id) {
        seasonService.activate(id);
        return Result.ok();
    }

    @GetMapping("/{id}/rankings")
    @Operation(summary = "赛季排名查询（走 DB；含段位分布+赛季元信息）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<RankingPageVO> getRankings(
            @PathVariable Long id,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        RankingQuery query = new RankingQuery();
        query.setTier(tier);
        query.setPage(page);
        query.setSize(size);
        return Result.ok(seasonService.getRankings(id, query));
    }
}
