package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 品牌配置更新请求。
 * <p>
 * appName 必填（@NotBlank + service 兜底）；slogan / logoUrl 可清空（传 "" → 清空，回退默认）；
 * primaryColor 缺省回退 #4CAF50，非空须为 #RRGGBB。logoUrl 非空时走 HeadBasedImageUrlValidator
 * （含 SSRF 私网拒绝）。
 */
@Data
public class BrandUpdateRequest {
    @NotBlank
    private String appName;
    private String slogan;
    @Pattern(
        regexp = "^(https://[^/]+(/.*)?)?$",
        message = "logo 必须为 https 开头（留空则清除）"
    )
    private String logoUrl;
    private String primaryColor;
}
