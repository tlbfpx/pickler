package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.vo.RankingPageVO;
import com.heypickler.vo.RankingVO;

import java.util.List;

public interface RankingService {

    /**
     * 刷新某积分类型当前赛季的排名（兼容旧签名，内部解析 CURRENT 赛季后委托新签名）。
     */
    void refreshRankings(String type);

    /**
     * 刷新指定赛季的排名（仅删除/重算该赛季，保留归档赛季）。
     */
    void refreshRankings(String type, String seasonCode);

    /**
     * 当前赛季榜单分页（按 tier/keyword 过滤，Redis 缓存）。
     * 过滤条件含 seasonCode，避免轮转后读到归档赛季行。
     */
    PageResult<RankingVO> getRankings(RankingQuery query);

    /**
     * 排名工作台分页：榜单分页 + 段位分布 + 当前赛季元信息。
     */
    RankingPageVO getRankingPage(RankingQuery query);

    List<RankingVO> getTop5(String type);
}
