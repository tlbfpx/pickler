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
}