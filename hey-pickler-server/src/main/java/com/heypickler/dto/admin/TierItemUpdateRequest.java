package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 段位配置批量更新请求项。
 * <p>
 * 铁律：tierCode 仅用于定位 tier_config 行（联合 uk_track_tier），永不写回。
 * 可改字段：tierName / tierColor / threshold / icon。
 */
@Data
public class TierItemUpdateRequest {
    /** 定位行（BRONZE..MASTER，不回写） */
    @NotBlank
    private String tierCode;
    private String tierName;
    private String tierColor;
    private Integer threshold;
    private String icon;
}
