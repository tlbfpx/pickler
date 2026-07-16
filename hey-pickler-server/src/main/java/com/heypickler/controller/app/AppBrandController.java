package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.BrandConfigService;
import com.heypickler.vo.BrandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 端品牌配置（匿名读，启动拉取）。
 * <p>
 * AppAuthFilter.PUBLIC_GET_PREFIXES 含 /api/app/brand，故 GET 匿名可达（同 /api/app/dict）。
 */
@RestController
@RequestMapping("/api/app/brand")
@RequiredArgsConstructor
@Tag(name = "APP - 品牌")
public class AppBrandController {

    private final BrandConfigService brandConfigService;

    @GetMapping
    @Operation(summary = "获取品牌配置（匿名）")
    public Result<BrandVO> get() {
        return Result.ok(brandConfigService.get());
    }
}
