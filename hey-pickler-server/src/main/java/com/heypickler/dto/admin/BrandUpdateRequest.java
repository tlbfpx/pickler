package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 品牌配置更新请求。
 * <p>
 * appName 必填（@NotBlank + service 兜底）；slogan / logoUrl 可清空（传 "" → 清空，回退默认）；
 * primaryColor 缺省回退 #4CAF50，非空须为 #RRGGBB。logoUrl 非空时走 HeadBasedImageUrlValidator。
 */
@Data
public class BrandUpdateRequest {
    @NotBlank
    private String appName;
    private String slogan;
    private String logoUrl;
    private String primaryColor;
}
