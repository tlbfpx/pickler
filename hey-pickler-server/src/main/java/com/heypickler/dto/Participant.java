package com.heypickler.dto;

/**
 * Grouping participant abstraction: either a single user (SINGLES events) or a
 * team (DOUBLES/MIXED), carrying the rank score used to order distribution.
 *
 * Exactly one of {@code userId} / {@code teamId} must be set — enforced at
 * construction so strategies and the grouping service can't produce an
 * ambiguous assignment.
 */
public class Participant {

    private final Long userId;   // non-null for singles participants
    private final Long teamId;   // non-null for team participants
    private final int rankScore; // higher = stronger

    public Participant(Long userId, Long teamId, int rankScore) {
        if ((userId == null) == (teamId == null)) {
            // both null, or both non-null — neither is a valid participant
            throw new IllegalArgumentException(
                    "Participant must set exactly one of userId/teamId");
        }
        this.userId = userId;
        this.teamId = teamId;
        this.rankScore = rankScore;
    }

    public static Participant singles(Long userId, int rankScore) {
        return new Participant(userId, null, rankScore);
    }

    public static Participant team(Long teamId, int rankScore) {
        return new Participant(null, teamId, rankScore);
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTeamId() {
        return teamId;
    }

    public int getRankScore() {
        return rankScore;
    }

    public boolean isTeam() {
        return teamId != null;
    }
}
