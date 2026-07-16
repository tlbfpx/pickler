package com.heypickler.vo;

import lombok.Data;

/**
 * 品牌配置展示对象（app / admin 共用）。
 * <p>
 * app_name / slogan / logo_url / primary_color，全部由 brand_config 单行驱动。
 */
@Data
public class BrandVO {
    private String appName;
    private String slogan;
    private String logoUrl;
    private String primaryColor;
}
