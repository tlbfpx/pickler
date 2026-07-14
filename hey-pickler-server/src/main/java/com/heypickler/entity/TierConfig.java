package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 段位配置（方案③ 专用表，双轨 per-track）。
 * <p>
 * tier_code 双轨统一 BRONZE..MASTER（系统绑定不可改）；tier_name/tier_color/threshold/icon/sort 可配。
 * STAR 沿用青铜…王者；PARTY 球友称号系（见习→活力→热血→资深→明星→传奇）。
 * 软删：deleted_at NULL=未删（@TableLogic，对齐 sys_dict 范式）。
 */
@Data
@TableName("tier_config")
public class TierConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 赛道：STAR / PARTY */
    private String track;
    /** 段位码：BRONZE / SILVER / GOLD / PLATINUM / DIAMOND / MASTER（双轨统一） */
    private String tierCode;
    /** 展示名：STAR=青铜…王者，PARTY=见习球友…传奇球友 */
    private String tierName;
    /** 段位色：#RRGGBB */
    private String tierColor;
    /** 升档阈值（>=threshold 命中该档；按 sort/threshold 升序） */
    private Integer threshold;
    /** 图标（emoji，前端可选展示） */
    private String icon;
    /** 排序（0..5，按 sort 升序遍历） */
    private Integer sort;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
