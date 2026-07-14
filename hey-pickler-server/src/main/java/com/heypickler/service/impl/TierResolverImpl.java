package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.entity.TierConfig;
import com.heypickler.mapper.TierConfigMapper;
import com.heypickler.service.TierResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TierResolver 默认实现：直接查 tier_config 表（本期不做 Redis 缓存）。
 * <p>
 * 决策依据：tier_config 仅 12 行（双轨 6 档），DB 读极快；RedisConfig 关闭 default typing，
 * 反序列化 List<TierConfig> 会得 LinkedHashMap 触发 ClassCastException（同阶段 1 DictCacheService）。
 * Redis 缓存 + 写时失效留待 Chunk 2 接入 admin 端点时补。
 * <p>
 * 行为等价于旧 TierProperties：keyFor 按 threshold 升序遍历取最后命中档；
 * 非 PARTY 一律按 STAR（保留旧实现的隐式 STAR 兜底语义）。
 */
@Service
public class TierResolverImpl implements TierResolver {

    private static final String DEFAULT_TIER_CODE = "BRONZE";
    private static final String FALLBACK_COLOR = "#6B7280";
    private static final List<String> TIER_CODES = Arrays.asList(
            "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER");

    private final TierConfigMapper tierConfigMapper;

    public TierResolverImpl(TierConfigMapper tierConfigMapper) {
        this.tierConfigMapper = tierConfigMapper;
    }

    /** 归一化 track：仅 PARTY 走 PARTY 阈值/称号，其余（含 null/ACTIVITY 等）一律按 STAR */
    private String normalizeTrack(String type) {
        return "PARTY".equals(type) ? "PARTY" : "STAR";
    }

    /** 按 sort 升序取出该 track 的全部档位（mapper 复用同一 wrapper，由调用方 track 区分） */
    private List<TierConfig> tiers(String track) {
        String resolved = normalizeTrack(track);
        List<TierConfig> all = tierConfigMapper.selectList(
                new LambdaQueryWrapper<TierConfig>()
                        .eq(TierConfig::getTrack, resolved)
                        .orderByAsc(TierConfig::getSort));
        // 极少数情况：DB 未 seed 或查询异常返回空，给出空 List 而非 null，避免 NPE
        return all != null ? all : new ArrayList<>();
    }

    @Override
    public String keyFor(int points, String type) {
        List<TierConfig> list = tiers(type);
        String result = DEFAULT_TIER_CODE;
        for (TierConfig t : list) {
            if (points >= t.getThreshold()) {
                result = t.getTierCode();
            }
        }
        return result;
    }

    @Override
    public String nameFor(String track, String key) {
        for (TierConfig t : tiers(track)) {
            if (t.getTierCode().equals(key)) {
                return t.getTierName();
            }
        }
        // 未命中：保留旧 TierProperties.nameFor 的 names.get(0) 语义有歧义（依赖 keys 顺序），
        // 新实现以 key 本身兜底，避免误把 PARTY 未知档映射成 STAR 青铜。
        return key;
    }

    @Override
    public String colorFor(String track, String key) {
        for (TierConfig t : tiers(track)) {
            if (t.getTierCode().equals(key)) {
                return t.getTierColor();
            }
        }
        return FALLBACK_COLOR;
    }

    @Override
    public String iconFor(String track, String key) {
        for (TierConfig t : tiers(track)) {
            if (t.getTierCode().equals(key)) {
                return t.getIcon();
            }
        }
        return null;
    }

    @Override
    public String defaultKey(String type) {
        return DEFAULT_TIER_CODE;
    }

    @Override
    public List<String> cacheKeysWithNull() {
        // List.of 不允许 null 元素，用 ArrayList 构造以兼容旧 TierProperties.cacheKeysWithNull 契约。
        List<String> all = new ArrayList<>(TIER_CODES);
        all.add(null);
        return all;
    }
}
