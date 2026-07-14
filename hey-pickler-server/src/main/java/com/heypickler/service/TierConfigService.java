package com.heypickler.service;

import com.heypickler.dto.admin.TierItemUpdateRequest;
import com.heypickler.vo.TierConfigVO;

import java.util.List;

/**
 * 段位配置服务（管理端读写双轨 tier_config）。
 * <p>
 * 读：按 track（STAR/PARTY）取出 6 档配置。
 * 写：批量 patch name/color/threshold/icon，强校验后失效缓存版本号。
 * 铁律：tierCode 永不回写（系统绑定 BRONZE..MASTER）。
 */
public interface TierConfigService {

    /** 读某 track 的全部档位配置（按 sort 升序）。 */
    List<TierConfigVO> getByTrack(String track);

    /**
     * 批量更新某 track 的档位配置（name/color/threshold/icon）。
     * <p>
     * 强校验（同 track 内）：
     * <ul>
     *   <li>恰好 6 档（BRONZE/SILVER/GOLD/PLATINUM/DIAMOND/MASTER 齐全）</li>
     *   <li>BRONZE threshold == 0</li>
     *   <li>所有 threshold &gt;= 0</li>
     *   <li>按 tierCode 固定顺序 threshold 严格递增</li>
     * </ul>
     * 写后 dictCacheService.incrementVersion() 失效前端 bundle。
     */
    void updateTrack(String track, List<TierItemUpdateRequest> items);
}
