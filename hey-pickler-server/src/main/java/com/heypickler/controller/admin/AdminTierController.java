package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.TierItemUpdateRequest;
import com.heypickler.service.TierConfigService;
import com.heypickler.vo.TierConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 管理端段位配置（双轨 per-track：STAR / PARTY）。
 * <p>
 * GET 读 6 档配置；PUT 批量 patch（强校验后失效前端 bundle）。
 * tierCode 系统绑定不可改；track 必须为 STAR 或 PARTY。
 */
@RestController
@RequestMapping("/api/admin/tier")
@RequiredArgsConstructor
@Tag(name = "管理端-段位配置")
public class AdminTierController {

    private static final Set<String> ALLOWED_TRACKS = Set.of("STAR", "PARTY");

    private final TierConfigService tierConfigService;

    @GetMapping("/{track}")
    @Operation(summary = "读取某 track 的 6 档段位配置")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<TierConfigVO>> get(@PathVariable String track) {
        validateTrack(track);
        return Result.ok(tierConfigService.getByTrack(track));
    }

    @PutMapping("/{track}")
    @Operation(summary = "批量更新段位配置（name/color/threshold/icon，强校验）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> update(@PathVariable String track,
                               @RequestBody @Valid List<TierItemUpdateRequest> items) {
        validateTrack(track);
        tierConfigService.updateTrack(track, items);
        return Result.ok();
    }

    private void validateTrack(String track) {
        if (!ALLOWED_TRACKS.contains(track)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法 track: " + track + "（仅 STAR/PARTY）");
        }
    }
}
