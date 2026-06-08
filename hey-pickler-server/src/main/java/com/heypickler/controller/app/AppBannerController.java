package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.BannerService;
import com.heypickler.vo.BannerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/banners")
@RequiredArgsConstructor
@Tag(name = "小程序-Banner")
public class AppBannerController {
    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "获取首页Banner")
    public Result<List<BannerVO>> list() {
        return Result.ok(bannerService.listEnabledBanners());
    }
}
