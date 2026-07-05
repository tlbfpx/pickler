package com.heypickler.service;

import com.heypickler.entity.Match;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameValidatorTest {

    private final GameValidator v = new GameValidator();

    private Match.GameScore game(int n, int a, int b) {
        Match.GameScore g = new Match.GameScore();
        g.setGame(n);
        g.setA(a);
        g.setB(b);
        return g;
    }

    @Test
    void validTwoZero_returnsTwoZero() {
        var r = v.validate(List.of(game(1, 21, 15), game(2, 21, 18)));
        assertEquals(2, r.gamesWonA());
        assertEquals(0, r.gamesWonB());
    }

    @Test
    void validTwoOne_returnsTwoOne() {
        var r = v.validate(List.of(
                game(1, 21, 19),
                game(2, 18, 21),
                game(3, 21, 15)));
        assertEquals(2, r.gamesWonA());
        assertEquals(1, r.gamesWonB());
    }

    @Test
    void zeroZero_isDraw_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 0, 0))));
    }

    @Test
    void reject21to20_onePointMargin() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 20))));
    }

    @Test
    void reject31to29_overMaxPoints() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 31, 29))));
    }

    @Test
    void rejectOverMaxPoints_onEitherSide() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 35))));
    }

    @Test
    void reject31to20_withinRangeButGameInvalid() {
        // 31 > 30, invalid
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 31, 20))));
    }

    @Test
    void deuceTo30_isAccepted() {
        // 2-0 with the second game being a 30-28 deuce (winner >= 21, lead >= 2, both <= 30)
        var r = v.validate(List.of(game(1, 21, 15), game(2, 30, 28)));
        assertEquals(2, r.gamesWonA());
        assertEquals(0, r.gamesWonB());
    }

    @Test
    void rejectIncomplete_withOnlyOneGame() {
        // A single game never reaches the 2-wins threshold (best-of-3)
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 19))));
    }

    @Test
    void validSweep3to0_isRejected() {
        // 3 games where A wins all 3 = impossible (match should end at game 2)
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(
                        game(1, 21, 10), game(2, 21, 8), game(3, 21, 5))));
    }

    @Test
    void rejectNegativeScores() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, -1, 21))));
    }

    @Test
    void rejectEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> v.validate(List.of()));
    }

    @Test
    void rejectNullList() {
        assertThrows(IllegalArgumentException.class, () -> v.validate(null));
    }

    @Test
    void rejectTooManyGames() {
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(
                        game(1, 21, 0), game(2, 21, 0),
                        game(3, 21, 0), game(4, 21, 0))));
    }

    @Test
    void rejectThirdGamePlayedWhenNot1to1() {
        // 2-0 + an extra game 3 is invalid (third game only at 1-1)
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(
                        game(1, 21, 10), game(2, 21, 8), game(3, 21, 5))));
    }

    // -------- Loop engineering Pass D7: 边界值收敛 --------

    @Test
    void valid21to19_exactTwoMargin() {
        // 21 : 19 → 正好 2 分领先 ≥ 21，合规
        var r = v.validate(List.of(game(1, 21, 19), game(2, 21, 17)));
        assertEquals(2, r.gamesWonA());
    }

    @Test
    void valid22to20_deuceStyleBeyondMin() {
        // 22 : 20 → 21+ 分 + 2 分差
        var r = v.validate(List.of(game(1, 22, 20), game(2, 21, 18)));
        assertEquals(2, r.gamesWonA());
    }

    @Test
    void valid30to28_capEdge() {
        // 30 : 28 → 到达封顶 30 仍合规
        var r = v.validate(List.of(game(1, 30, 28), game(2, 21, 5)));
        assertEquals(2, r.gamesWonA());
    }

    @Test
    void reject29to30_capExceededOnLoser() {
        // 30 是封顶；29:30 表示 A=B 不会有胜负，但 30>29 触发 win 校验 + max 校验。
        // 这里 score=30,A=29,B=30 → a 失败（未达 21）。改测试为镜像：B 胜但 B=30,
        // 实际：b=30, a=29 → b 胜 (b>=21 && b-a>=2)=true → 通过。
        // 这里要构造"被拒"场景：A=29,B=31（超 max）。
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 31))));
    }

    @Test
    void reject21to19_singleGameBelowWinThreshold() {
        // 单局 = 没达到 2 胜局
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 19))));
    }

    @Test
    void rejectOnePointLeadRegardlessOfMax() {
        // 21:20 仅 1 分差，拒
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 21, 20))));
    }

    @Test
    void rejectThirtyOneToTwentyNine_loserBelowMaxButAboveCap() {
        // 31 > MAX(30) 即拒
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 31, 29))));
    }

    @Test
    void rejectThirtyOneToTwentyNine_winnerBreach() {
        // 同样 a=29, b=31 — 一方先触发 max 校验失败
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 29, 31))));
    }

    @Test
    void rejectBothSidesUnderMinEvenWithBigDiff() {
        // 5 : 0 远远未达 21 win points
        assertThrows(IllegalArgumentException.class,
                () -> v.validate(List.of(game(1, 5, 0))));
    }
}