package com.heypickler.vo;

import lombok.Data;

/**
 * One row in a group's standings.
 *
 * <p>Single value for {@code participantKey}: user_id for SINGLES, team_id
 * for DOUBLES/MIXED (determined by which slot the participant occupies in
 * completed matches within the group).
 */
@Data
public class StandingVO {
    /** user_id or team_id depending on event format. */
    private Long participantKey;
    private Integer rank;
    private Integer wins;
    private Integer losses;
    /** Cumulative games won by this participant across all completed matches in the group. */
    private Integer gamesFor;
    /** Cumulative games won by opponents against this participant. */
    private Integer gamesAgainst;
    /** For doubles, the two member names joined "m1 / m2"; for singles, the nickname. */
    private String displayName;
}