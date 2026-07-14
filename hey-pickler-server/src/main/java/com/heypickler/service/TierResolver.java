package com.heypickler.service;

import java.util.List;

/**
 * 段位解析器（替代旧 TierProperties 配置驱动）。
 * <p>
 * 段位配置由 tier_config 表（V19）驱动，双轨 per-track：
 * STAR 沿用青铜…王者；PARTY 球友称号系（见习→活力→热血→资深→明星→传奇）。
 * tier_code 双轨统一 BRONZE..MASTER（系统绑定不可改），name/color/icon/threshold 可配。
 * <p>
 * 行为等价于旧 TierProperties（keyFor/cacheKeysWithNull 签名不变）；nameFor/colorFor/iconFor
 * 新增 track 入参以支持 per-track 展示名（STAR BRONZE=青铜，PARTY BRONZE=见习球友）。
 */
public interface TierResolver {

    /**
     * 积分→tier_code（type 即 track：STAR/PARTY，非 PARTY 一律按 STAR）。
     * 对齐旧 TierProperties.keyFor：线性遍历 threshold，取 points>=threshold 最后命中的 tier_code。
     */
    String keyFor(int points, String type);

    /** tier_code→展示名（per-track：STAR BRONZE=青铜，PARTY BRONZE=见习球友） */
    String nameFor(String track, String key);

    /** tier_code→段位色（per-track） */
    String colorFor(String track, String key);

    /** tier_code→图标（per-track） */
    String iconFor(String track, String key);

    /** 默认档 tier_code（BRONZE） */
    String defaultKey(String type);

    /** 所有 tier_code + null（清缓存用，对齐旧 TierProperties.cacheKeysWithNull） */
    List<String> cacheKeysWithNull();
}
