package com.heypickler.service.impl;

import com.heypickler.vo.StandingVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlacementServiceImpl.assignGlobalRanks 纯函数单测：多组赛事全局排名的确定性 + 平分共享。
 * 修 P2：原按组内 rank 拼接 + 稳定排序，两组冠军 rank=1 的先后序取决于 HashMap 迭代 → 点数随机。
 */
class PlacementServiceImplTest {

    private StandingVO standing(long key, int wins, int gamesFor, int gamesAgainst) {
        StandingVO s = new StandingVO();
        s.setParticipantKey(key);
        s.setWins(wins);
        s.setGamesFor(gamesFor);
        s.setGamesAgainst(gamesAgainst);
        return s;
    }

    private int rankOf(List<StandingVO> ranked, long key) {
        return ranked.stream()
                .filter(s -> s.getParticipantKey() != null && s.getParticipantKey() == key)
                .findFirst()
                .map(StandingVO::getRank)
                .orElse(-1);
    }

    @Test
    void multiGroup_tiedGroupWinnersShareGlobalRank1_nextIsRank3() {
        // 两组各 2 人；两组冠军战绩完全相同（wins=3, 净胜局=10）→ 必须共享全局第 1，而非随机的 1 和 2。
        List<StandingVO> groupA = Arrays.asList(standing(1, 3, 21, 11), standing(2, 1, 10, 15));
        List<StandingVO> groupB = Arrays.asList(standing(3, 3, 20, 10), standing(4, 0, 5, 18));

        List<StandingVO> ranked = PlacementServiceImpl.assignGlobalRanks(Arrays.asList(groupA, groupB));

        assertEquals(1, rankOf(ranked, 1), "group A 冠军 = 全局第 1");
        assertEquals(1, rankOf(ranked, 3), "group B 冠军同战绩 → 也 = 全局第 1（共享，非第 2）");
        assertEquals(3, rankOf(ranked, 2), "竞技式跳号：第 1 并列后下一个是第 3");
        assertEquals(4, rankOf(ranked, 4));
    }

    @Test
    void ordersByWinsDescThenGamesDiffDesc() {
        List<StandingVO> g = Arrays.asList(standing(10, 2, 21, 10), standing(11, 2, 21, 15));
        // 同 wins=2：key 10 净胜局 +11 > key 11 净胜局 +6 → key 10 排前
        List<StandingVO> ranked = PlacementServiceImpl.assignGlobalRanks(List.of(g));
        assertEquals(10, ranked.get(0).getParticipantKey());
        assertEquals(1, ranked.get(0).getRank());
        assertEquals(11, ranked.get(1).getParticipantKey());
        assertEquals(2, ranked.get(1).getRank());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(PlacementServiceImpl.assignGlobalRanks(List.of()).isEmpty());
    }
}
