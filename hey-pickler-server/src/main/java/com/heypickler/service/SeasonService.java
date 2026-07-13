package com.heypickler.service;

import com.heypickler.dto.admin.SeasonCreateRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.vo.RankingPageVO;
import com.heypickler.vo.SeasonVO;

import java.util.List;

public interface SeasonService {

    /**
     * 列出赛季（按类型过滤，可传 null 列全部），按创建时间倒序。
     */
    List<SeasonVO> list(String type);

    /**
     * 新建赛季，默认状态 ARCHIVED。
     */
    SeasonVO create(SeasonCreateRequest request);

    /**
     * 切换某赛季为 CURRENT：同 type 下原 CURRENT 归档为 ARCHIVED，目标置为 CURRENT。
     * 事务保证同 type 唯一 CURRENT。激活后同步给新赛季播种排名快照。
     */
    void activate(Long id);

    /**
     * 查询某赛季的排名工作台（走 DB，不读缓存；含段位分布 + 赛季元信息）。
     */
    RankingPageVO getRankings(Long seasonId, RankingQuery query);
}
