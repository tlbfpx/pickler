package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.app.RankingQuery;
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

    PageResult<RankingVO> getRankings(RankingQuery query);

    List<RankingVO> getTop5(String type);
}
