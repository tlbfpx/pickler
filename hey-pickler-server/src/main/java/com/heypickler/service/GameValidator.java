package com.heypickler.service;

import com.heypickler.entity.Match;

import java.util.List;

/**
 * Validates badminton match submissions against best-of-3 / 21-point / deuce-to-30 rules.
 *
 * <p>Rules:
 * <ul>
 *   <li>1 to 3 games per match (best of 3).</li>
 *   <li>Each game: scores 0..30 inclusive; winner has score >= 21 AND >= loser + 2.</li>
 *   <li>First side to win 2 games wins the match; the 3rd game only played if 1-1.</li>
 * </ul>
 */
public class GameValidator {

    /** Maximum points either side may score in a single game. */
    public static final int MAX_POINTS = 30;

    /** Minimum points required for a game winner. */
    public static final int MIN_WIN_POINTS = 21;

    /** Games required to win the match. */
    public static final int GAMES_TO_WIN = 2;

    /**
     * Validate and compute the number of games won by each side.
     *
     * @throws IllegalArgumentException with a user-facing Chinese message describing the failure.
     */
    public Result validate(List<Match.GameScore> games) {
        if (games == null || games.isEmpty()) {
            throw new IllegalArgumentException("比分不能为空");
        }
        if (games.size() > GAMES_TO_WIN + 1) {
            throw new IllegalArgumentException("比赛最多 " + (GAMES_TO_WIN + 1) + " 局");
        }

        int winsA = 0, winsB = 0;
        for (int i = 0; i < games.size(); i++) {
            Match.GameScore g = games.get(i);
            int gameNo = i + 1;
            if (g == null) {
                throw new IllegalArgumentException("第 " + gameNo + " 局缺失");
            }
            int a = g.getA(), b = g.getB();
            if (a < 0 || a > MAX_POINTS || b < 0 || b > MAX_POINTS) {
                throw new IllegalArgumentException(
                        "第 " + gameNo + " 局比分需在 0-" + MAX_POINTS + " 之间");
            }
            if (a == b) {
                throw new IllegalArgumentException("第 " + gameNo + " 局比分不能为平局");
            }
            boolean aWins = a > b;
            if (aWins) {
                if (a < MIN_WIN_POINTS || a - b < 2) {
                    throw new IllegalArgumentException(
                            "第 " + gameNo + " 局 A 队获胜条件未满足（需 ≥ " + MIN_WIN_POINTS + " 且领先 2 分）");
                }
                winsA++;
            } else {
                if (b < MIN_WIN_POINTS || b - a < 2) {
                    throw new IllegalArgumentException(
                            "第 " + gameNo + " 局 B 队获胜条件未满足（需 ≥ " + MIN_WIN_POINTS + " 且领先 2 分）");
                }
                winsB++;
            }
        }

        // Match termination: once one side reaches GAMES_TO_WIN the match is over.
        if (winsA < GAMES_TO_WIN && winsB < GAMES_TO_WIN) {
            throw new IllegalArgumentException("比分不完整：未达到胜局数 " + GAMES_TO_WIN);
        }
        if (winsA > GAMES_TO_WIN || winsB > GAMES_TO_WIN) {
            throw new IllegalArgumentException("胜局数超出最大 " + GAMES_TO_WIN);
        }
        // The 3rd game is only played if 1-1.
        if (games.size() == 3 && winsA + winsB != 3) {
            throw new IllegalArgumentException("第 3 局仅在 1-1 时进行");
        }

        return new Result(winsA, winsB);
    }

    /** Outcome of validation. */
    public record Result(int gamesWonA, int gamesWonB) {}
}