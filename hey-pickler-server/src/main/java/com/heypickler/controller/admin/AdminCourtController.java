package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.*;
import com.heypickler.service.CourtService;
import com.heypickler.vo.CourtVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/courts")
@RequiredArgsConstructor
@Tag(name = "管理端-场地管理")
public class AdminCourtController {
    private final CourtService courtService;

    @GetMapping @Operation(summary = "场地列表(按场馆)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<CourtVO>> list(@RequestParam Long venueId) { return Result.ok(courtService.listByVenue(venueId)); }

    @GetMapping("/{id}") @Operation(summary = "场地详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<CourtVO> get(@PathVariable Long id) { return Result.ok(courtService.get(id)); }

    @PostMapping @Operation(summary = "新建场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> create(@RequestBody @Valid CourtCreateRequest req) {
        return Result.ok(Map.of("id", courtService.create(req)));
    }
    @PutMapping("/{id}") @Operation(summary = "更新场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CourtCreateRequest req) {
        courtService.update(id, req); return Result.ok();
    }
    @DeleteMapping("/{id}") @Operation(summary = "删除场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> delete(@PathVariable Long id) { courtService.delete(id); return Result.ok(); }

    @PutMapping("/{id}/pricing-bands") @Operation(summary = "覆盖时段定价带(带重叠校验)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> replacePricingBands(@PathVariable Long id, @RequestBody @Valid CourtPricingBandBatchRequest req) {
        courtService.replacePricingBands(id, req); return Result.ok();
    }
    @PostMapping("/{id}/pricing-bands/copy") @Operation(summary = "从指定场地复制价目")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> copyPricingBands(@PathVariable Long id, @RequestParam Long from) {
        courtService.copyPricingBands(id, from); return Result.ok();
    }
}
