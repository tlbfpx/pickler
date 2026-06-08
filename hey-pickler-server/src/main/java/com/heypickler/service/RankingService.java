package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.vo.RankingVO;

import java.util.List;

public interface RankingService {
    void enterPoints(Long eventId, PointEntryRequest request, Long operatorId);

    void refreshRankings(String type);

    PageResult<RankingVO> getRankings(RankingQuery query);

    List<RankingVO> getTop5(String type);
}
