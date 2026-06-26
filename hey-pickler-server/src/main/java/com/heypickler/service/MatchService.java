package com.heypickler.service;

import com.heypickler.common.enums.GroupingStrategyType;
import com.heypickler.entity.Match;
import com.heypickler.vo.MatchVO;
import com.heypickler.vo.StandingVO;

import java.util.List;

/**
 * Match-play lifecycle (Spec 2): generate, score, reset, standings, complete.
 *
 * <p>Match is the unit of play within a group; this service is the only writer.
 * Score validation uses GameValidator; participant authorization is enforced
 * inline (slot occupant OR admin).
 */
public interface MatchService {

    /** Generate round-robin matches for every group in an event. Replaces any prior matches. */
    List<Match> generate(Long eventId);

    /**
     * Submit score for a match. The caller must be one of the two slot occupants
     * (single user or team member) or an admin.
     */
    void submitScore(Long matchId, Long userId, List<Match.GameScore> games, boolean isAdmin);

    /** Admin-only: reset a match to SCHEDULED, clearing score fields. */
    void reset(Long matchId);

    /** Compute per-group standings from completed matches. Returns a list per group. */
    List<List<StandingVO>> standings(Long eventId);

    /** Admin-only: transition event to COMPLETED once every match is COMPLETED. */
    void complete(Long eventId);

    /** App-side read: matches where the caller is a slot occupant. */
    List<MatchVO> listMyMatches(Long eventId, Long userId);

    /** Admin-side read: all matches in the event, grouped by match_group. */
    List<List<MatchVO>> listEventMatches(Long eventId);

    /** Convert a Match entity to its VO representation (display names unresolved). */
    MatchVO toVO(Match match);
}