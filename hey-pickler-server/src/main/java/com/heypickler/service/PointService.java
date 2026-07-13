package com.heypickler.service;

import com.heypickler.common.enums.PointSource;
import com.heypickler.service.dto.PointEntry;

import java.util.List;

/**
 * 积分发分服务：负责把积分写入 point_record 并累加到 user 余额、重算 tier、
 * 发布赛季维度的事件。原 RankingServiceImpl.enterPoints 已迁移至此。
 */
public interface PointService {

    /**
     * 批量发分。
     *
     * @param eventId   关联赛事 ID（手动调整可为 null）
     * @param type      积分类型 STAR | PARTY
     * @param records   发分明细
     * @param source    来源（REGISTRATION / MANUAL / ...）
     * @param operatorId 操作者（管理员）ID
     */
    void enterPoints(Long eventId, String type, List<PointEntry> records,
                     PointSource source, Long operatorId);

    /**
     * Issue a single placement point row (Spec 3). Inserts a point_record with
     * source=PLACEMENT, updates user balance + tier, and publishes a
     * PointChangeEvent when the calling transaction commits.
     */
    void issuePlacement(Long eventId, Long userId, int points, String reason);

    /**
     * 撤销一条积分记录：写入 source=ADJUST 的补偿行（负分），仅限 MANUAL/ADJUST 来源。
     * PLACEMENT 等系统来源不可撤销（名次发分幂等且关联赛事生命周期，撤销会破坏一致性）。
     * 跨赛季撤销被拒——补偿行必须落在当前赛季，与原记录同赛季才有意义。
     * 复用 writeRecord：余额钳制≥0、段位重算、存库；PointChangeListener 异步刷新排名+失效缓存。
     *
     * @param recordId   原始 point_record.id
     * @param operatorId 操作管理员 ID
     */
    void revertPointRecord(Long recordId, Long operatorId);
}
