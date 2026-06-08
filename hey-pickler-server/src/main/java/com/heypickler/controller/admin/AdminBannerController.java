package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BannerCreateRequest;
import com.heypickler.service.BannerService;
import com.heypickler.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@Tag(name = "管理端-Banner管理")
public class AdminBannerController {
    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "Banner列表")
    public Result<List<BannerVO>> list() {
        return Result.ok(bannerService.adminListAll());
    }

    @PostMapping
    @Operation(summary = "创建Banner")
    public Result<Map<String, Object>> create(@RequestBody @Valid BannerCreateRequest request) {
        Long id = bannerService.createBanner(request);
        return Result.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑Banner")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid BannerCreateRequest request) {
        bannerService.updateBanner(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除Banner")
    public Result<Void> delete(@PathVariable Long id) {
        bannerService.deleteBanner(id);
        return Result.ok();
    }
}
