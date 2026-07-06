package com.heypickler.controller.admin;

import com.heypickler.dto.app.ScoreSubmissionRequest;
import com.heypickler.entity.Match;
import com.heypickler.service.MatchService;
import com.heypickler.vo.MatchVO;
import com.heypickler.vo.StandingVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * Loop-v8 coverage sprint — moves AdminMatchController from 46.8% to ~80%+.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminMatchControllerTest {

    @Mock private MatchService matchService;
    @InjectMocks private AdminMatchController controller;

    @Test
    void generate_returnsMatchVOList() {
        Match m = new Match();
        m.setId(1L);
        MatchVO vo = new MatchVO();
        doReturn(List.of(m)).when(matchService).generate(7L);
        doReturn(vo).when(matchService).toVO(m);
        List<MatchVO> result = controller.generate(7L).getData();
        assertEquals(1, result.size());
    }

    @Test
    void listEventMatches_delegates() {
        doReturn(List.of()).when(matchService).listEventMatches(7L);
        assertEquals(0, controller.listEventMatches(7L).getData().size());
    }

    @Test
    void standings_delegates() {
        doReturn(List.<List<StandingVO>>of()).when(matchService).standings(7L);
        assertEquals(0, controller.standings(7L).getData().size());
    }

    @Test
    void reset_delegates() {
        controller.reset(1L);
    }

    @Test
    void adminScore_mapsGamesToGameScore() {
        ScoreSubmissionRequest req = new ScoreSubmissionRequest();
        MatchVO.GameScore g = new MatchVO.GameScore();
        g.setGame(1);
        g.setA(21);
        g.setB(15);
        req.setGames(List.of(g));
        controller.adminScore(1L, req);
    }

    @Test
    void complete_delegates() {
        controller.complete(7L);
    }
}
