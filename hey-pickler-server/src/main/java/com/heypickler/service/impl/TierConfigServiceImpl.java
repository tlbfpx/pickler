package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.TierItemUpdateRequest;
import com.heypickler.entity.TierConfig;
import com.heypickler.mapper.TierConfigMapper;
import com.heypickler.service.DictCacheService;
import com.heypickler.service.TierConfigService;
import com.heypickler.vo.TierConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 段位配置服务实现。
 * <p>
 * 双轨 per-track（STAR / PARTY），每轨固定 6 档（BRONZE..MASTER，系统绑定不可改）。
 * 强校验：更新时按 tierCode 固定顺序校验 threshold 严格递增、BRONZE=0、全 >=0、恰好 6 档。
 * 铁律：tierCode 仅用于定位行（uk_track_tier），updateById 时永不回写 tierCode。
 * <p>
 * 版本号自增在 DB 事务内执行 Redis INCR（与 DictServiceImpl.updateItems 同模式）：
 * Redis 不参与 Spring 事务，但 tier_config 仅 12 行、版本号是粗粒度失效信号，
 * 幻影失效下次读会自愈，代价可接受。
 */
@Service
@RequiredArgsConstructor
public class TierConfigServiceImpl implements TierConfigService {

    /** tier_code 固定顺序（系统绑定，不可改）；校验和 patch 均按此顺序 */
    private static final List<String> TIER_CODE_ORDER = Arrays.asList(
            "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER");
    private static final String BRONZE = "BRONZE";

    private final TierConfigMapper tierConfigMapper;
    private final DictCacheService dictCacheService;

    @Override
    public List<TierConfigVO> getByTrack(String track) {
        return tierConfigMapper.selectList(new LambdaQueryWrapper<TierConfig>()
                        .eq(TierConfig::getTrack, track)
                        .orderByAsc(TierConfig::getSort))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTrack(String track, List<TierItemUpdateRequest> items) {
        // 1. 强校验：tierCode 非空（@Valid 对 List 元素不级联，service 兜底）
        for (TierItemUpdateRequest req : items) {
            if (req.getTierCode() == null || req.getTierCode().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "tierCode 不能为空");
            }
        }

        // 2. 恰好 6 档
        if (items.size() != TIER_CODE_ORDER.size()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "段位配置必须为 " + TIER_CODE_ORDER.size() + " 档，当前 " + items.size() + " 档");
        }

        // 3. 按 tierCode 收集（去重 + 缺失检测）
        Map<String, TierItemUpdateRequest> byCode = new LinkedHashMap<>();
        for (TierItemUpdateRequest req : items) {
            String code = req.getTierCode();
            if (byCode.containsKey(code)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "重复的 tierCode: " + code);
            }
            byCode.put(code, req);
        }
        for (String code : TIER_CODE_ORDER) {
            if (!byCode.containsKey(code)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "缺少 tierCode: " + code);
            }
        }

        // 4. threshold 校验：按固定顺序取值
        //    - BRONZE 必须 == 0
        //    - 全部 >= 0
        //    - 严格递增（threshold[i] > threshold[i-1]）
        Integer prevThreshold = null;
        for (String code : TIER_CODE_ORDER) {
            Integer threshold = byCode.get(code).getThreshold();
            if (threshold == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "threshold 不能为空: " + code);
            }
            if (threshold < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "threshold 不能为负数: " + code + "=" + threshold);
            }
            if (BRONZE.equals(code) && threshold != 0) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "BRONZE threshold 必须为 0，当前 " + threshold);
            }
            if (prevThreshold != null && threshold <= prevThreshold) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "threshold 必须严格递增: " + code + "=" + threshold
                                + " <= 前档 " + prevThreshold);
            }
            prevThreshold = threshold;
        }

        // 5. 逐档 patch（只 name/color/threshold/icon，tierCode 永不回写铁律）
        for (String code : TIER_CODE_ORDER) {
            TierItemUpdateRequest req = byCode.get(code);
            TierConfig existing = tierConfigMapper.selectOne(new LambdaQueryWrapper<TierConfig>()
                    .eq(TierConfig::getTrack, track)
                    .eq(TierConfig::getTierCode, code));
            if (existing == null) {
                throw new BizException(ErrorCode.NOT_FOUND,
                        "段位配置不存在: " + track + "/" + code);
            }
            TierConfig patch = new TierConfig();
            patch.setId(existing.getId());
            patch.setTierName(req.getTierName());
            patch.setTierColor(req.getTierColor());
            patch.setThreshold(req.getThreshold());
            patch.setIcon(req.getIcon());
            tierConfigMapper.updateById(patch);
        }

        // 6. 失效前端 bundle 版本号（同 DictServiceImpl.updateItems 模式）
        dictCacheService.incrementVersion();
    }

    private TierConfigVO toVO(TierConfig t) {
        TierConfigVO vo = new TierConfigVO();
        vo.setTrack(t.getTrack());
        vo.setTierCode(t.getTierCode());
        vo.setTierName(t.getTierName());
        vo.setTierColor(t.getTierColor());
        vo.setThreshold(t.getThreshold());
        vo.setIcon(t.getIcon());
        vo.setSort(t.getSort());
        return vo;
    }
}
