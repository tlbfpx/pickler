package com.heypickler.vo;

import lombok.Data;

/**
 * 段位配置展示对象（管理端读双轨配置）。
 * <p>
 * 字段与 tier_config 表对齐；tierCode 不可改（系统绑定 BRONZE..MASTER）。
 */
@Data
public class TierConfigVO {
    private String track;
    private String tierCode;
    private String tierName;
    private String tierColor;
    private Integer threshold;
    private String icon;
    private Integer sort;
}
