package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BrandUpdateRequest;
import com.heypickler.service.BrandConfigService;
import com.heypickler.vo.BrandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端品牌配置。
 * <p>
 * GET 匿名读（AdminAuthFilter.PUBLIC_ADMIN_GET_PATHS 含 /api/admin/brand，仅 GET 放行）——
 * login 前页需展示 app 名 / logo / 主题色，故读不鉴权。
 * PUT 鉴权（SUPER_ADMIN / ADMIN），强校验后 patch。
 */
@RestController
@RequestMapping("/api/admin/brand")
@RequiredArgsConstructor
@Tag(name = "管理端-品牌配置")
public class AdminBrandController {

    private final BrandConfigService brandConfigService;

    @GetMapping
    @Operation(summary = "获取品牌配置（匿名读，login 前页可用）")
    public Result<BrandVO> get() {
        return Result.ok(brandConfigService.get());
    }

    @PutMapping
    @Operation(summary = "更新品牌配置（appName / slogan / logoUrl / primaryColor）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<BrandVO> update(@RequestBody @Valid BrandUpdateRequest req) {
        return Result.ok(brandConfigService.update(req));
    }
}
