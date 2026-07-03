package com.heypickler.service;

import com.heypickler.entity.Team;
import com.heypickler.vo.TeamVO;

import java.util.List;

/**
 * Team state machine for doubles/mixed events.
 *
 * Lifecycle:
 *   createTeam (captain invites)  -> Team{PENDING}  + captain Registration
 *   confirmTeam (partner accepts) -> Team{CONFIRMED} + partner Registration
 *   decline    (partner rejects)  -> team deleted   + captain Registration withdrawn
 *   dissolve   (confirmed team, either withdraws) -> team deleted + both Registrations withdrawn
 */
public interface TeamService {

    /**
     * Captain creates a PENDING team and registers themselves.
     *
     * @param eventId        event id
     * @param captainId      the inviting user (becomes member1)
     * @param partnerUserId  the invited user (becomes member2)
     * @return the freshly-created team (with id populated)
     */
    Team createTeam(Long eventId, Long captainId, Long partnerUserId);

    /**
     * Invited partner confirms a PENDING team. Promotes team to CONFIRMED and
     * registers the partner.
     *
     * @param teamId      team id
     * @param userId      must equal team.member2UserId
     * @return the confirmed team
     */
    Team confirmTeam(Long teamId, Long userId);

    /**
     * Dissolve a CONFIRMED team: physically delete the team row and withdraw
     * both members' registrations. Used when either member of a CONFIRMED team
     * withdraws from the event.
     */
    void dissolve(Long teamId);

    /**
     * Invited partner declines the invitation: delete the team row and withdraw
     * the captain's registration.
     *
     * @param teamId  team id
     * @param userId  must equal team.member2UserId
     */
    void decline(Long teamId, Long userId);

    /**
     * Look up the team a user belongs to in an event (as either member).
     *
     * @return the team, or null if the user has no team in this event
     */
    Team getMyTeam(Long eventId, Long userId);

    /**
     * List all teams in an event (PENDING + CONFIRMED) as VOs with member
     * nicknames populated. Used by the admin panel to show the team context
     * alongside the registration list.
     */
    List<TeamVO> listByEventId(Long eventId);

    /**
     * Convert a team entity to a VO with member names populated.
     */
    TeamVO toVO(Team team);
}
