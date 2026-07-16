package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌配置（单行 id=1）：app 名称 / slogan / logo URL / 主题色。
 * <p>
 * 三端配置驱动：backend VO 装配、admin（标题/logo/Element Plus 主色）、wxapp（导航栏/logo/chrome）。
 * 运营在 admin 改即生效；logo_url NULL → 前端回退内置资源。
 * 软删：deleted_at NULL=未删（@TableLogic，对齐 sys_dict / tier_config）。
 */
@Data
@TableName("brand_config")
public class BrandConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** app 名称（admin 标题、wxapp 导航栏 / login） */
    private String appName;
    /** 副标题（wxapp login） */
    private String slogan;
    /** logo 外链 URL（HeadBasedImageUrlValidator 校验）；NULL / 空 → 前端回退内置资源 */
    private String logoUrl;
    /** 主题色 #RRGGBB（admin Element Plus primary、wxapp 导航栏 / TabBar / 关键面 inline） */
    private String primaryColor;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
