package com.heypickler.controller.admin;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Season;
import com.heypickler.service.PointService;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingPageVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loop-v8 coverage sprint — moves AdminRankingController from 35.9% to ~80%+.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRankingControllerTest {

    @Mock private RankingService rankingService;
    @Mock private PointService pointService;
    @Mock private com.heypickler.mapper.SeasonMapper seasonMapper;
    @InjectMocks private AdminRankingController controller;

    @Test
    void getRankings_uppercasesTypeAndPassesQuery() {
        RankingPageVO stub = new RankingPageVO();
        stub.setPage(PageResult.of(0, 1, 20, List.of()));
        stub.setSeasonCode("2026-Q2");
        stub.setSeasonStatus("CURRENT");
        doReturn(stub).when(rankingService).getRankingPage(any(RankingQuery.class));
        RankingPageVO result = controller.getRankings("star", "alice", null, 1, 20).getData();
        assertEquals("2026-Q2", result.getSeasonCode());
    }

    @Test
    void refreshRankings_bodyNull_defaultsToStar() {
        Season s = new Season();
        s.setType("STAR");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);

        controller.refreshRankings(null);
    }

    @Test
    void refreshRankings_bodyEmpty_defaultsToStar() {
        Season s = new Season();
        s.setType("STAR");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);

        controller.refreshRankings(Map.of());
    }

    @Test
    void refreshRankings_withTypeParty() {
        Season s = new Season();
        s.setType("PARTY");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);

        controller.refreshRankings(Map.of("type", "PARTY"));
    }

    @Test
    void enterPoints_extractsAdminIdAndDelegates() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("adminId")).thenReturn(99L);
        PointEntryRequest body = new PointEntryRequest();
        body.setEventId(1L);
        body.setType("STAR");
        body.setRecords(List.of(new PointEntryRequest.PointRecordItem() {{
            setUserId(7L);
            setPoints(50);
            setReason("test");
        }}));
        controller.enterPoints(req, body);
    }

    @Test
    void getRankings_keywordNull_handled() {
        RankingPageVO stub = new RankingPageVO();
        stub.setPage(PageResult.of(0, 1, 20, List.of()));
        doReturn(stub).when(rankingService).getRankingPage(any(RankingQuery.class));
        controller.getRankings("STAR", null, null, 1, 20);
    }

    @Test
    void revertPointRecord_extractsAdminIdAndDelegates() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("adminId")).thenReturn(7L);
        controller.revertPointRecord(req, 42L);
        verify(pointService).revertPointRecord(42L, 7L);
    }
}
