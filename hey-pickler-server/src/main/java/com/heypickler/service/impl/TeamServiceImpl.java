package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.common.enums.TeamStatus;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.Registration;
import com.heypickler.entity.Team;
import com.heypickler.entity.User;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.TeamService;
import com.heypickler.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Team state machine for doubles/mixed events.
 *
 * All mutating operations run inside a transaction. Membership uniqueness is
 * enforced in two layers:
 *   1. application-level check via SELECT WHERE event_id=? AND (member1=? OR member2=?)
 *      (returns clear BizException messages before hitting the DB constraint)
 *   2. database UNIQUE KEY uk_event_member1 / uk_event_member2 (V12 migration)
 *      as the race-condition backstop. A DataIntegrityViolationException from
 *      these would surface as a 500 — the application check is the primary gate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl implements TeamService {

    private static final String REG_REGISTERED = "REGISTERED";
    private static final String REG_WITHDRAWN = "WITHDRAWN";

    private final TeamMapper teamMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Team createTeam(Long eventId, Long captainId, Long partnerUserId) {
        if (captainId == null || partnerUserId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "队长与搭档不能为空");
        }
        if (captainId.equals(partnerUserId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能与自己组队");
        }

        // Membership check: neither user may already be in a team in this event.
        List<Team> existing = teamMapper.selectList(
                new LambdaQueryWrapper<Team>()
                        .eq(Team::getEventId, eventId)
                        .and(w -> w.eq(Team::getMember1UserId, captainId)
                                .or().eq(Team::getMember2UserId, captainId)
                                .or().eq(Team::getMember1UserId, partnerUserId)
                                .or().eq(Team::getMember2UserId, partnerUserId)));
        if (!existing.isEmpty()) {
            throw new BizException(ErrorCode.DUPLICATE_REGISTRATION, "该用户已在本赛事组队");
        }

        Team team = new Team();
        team.setEventId(eventId);
        team.setMember1UserId(captainId);
        team.setMember2UserId(partnerUserId);
        team.setStatus(TeamStatus.PENDING.name());
        teamMapper.insert(team);

        // Captain self-registers immediately; partner's registration appears on confirm.
        Registration captainReg = new Registration();
        captainReg.setUserId(captainId);
        captainReg.setEventId(eventId);
        captainReg.setTeamId(team.getId());
        captainReg.setStatus(REG_REGISTERED);
        registrationMapper.insert(captainReg);

        return team;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Team confirmTeam(Long teamId, Long userId) {
        Team team = requireTeam(teamId);

        // Only the invited partner may confirm.
        if (!userId.equals(team.getMember2UserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅受邀队友可确认组队");
        }
        if (!TeamStatus.PENDING.name().equals(team.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前队伍状态无法确认: " + team.getStatus());
        }

        // The partner must not have joined another team in the meantime.
        List<Team> conflicts = teamMapper.selectList(
                new LambdaQueryWrapper<Team>()
                        .eq(Team::getEventId, team.getEventId())
                        .ne(Team::getId, teamId)
                        .and(w -> w.eq(Team::getMember1UserId, userId)
                                .or().eq(Team::getMember2UserId, userId)));
        if (!conflicts.isEmpty()) {
            throw new BizException(ErrorCode.DUPLICATE_REGISTRATION, "该用户已在本赛事组队");
        }

        team.setStatus(TeamStatus.CONFIRMED.name());
        teamMapper.updateById(team);

        Registration partnerReg = new Registration();
        partnerReg.setUserId(userId);
        partnerReg.setEventId(team.getEventId());
        partnerReg.setTeamId(teamId);
        partnerReg.setStatus(REG_REGISTERED);
        registrationMapper.insert(partnerReg);

        return team;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolve(Long teamId) {
        Team team = requireTeam(teamId);

        // Withdraw every registration tied to this team.
        List<Registration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().eq(Registration::getTeamId, teamId));
        for (Registration reg : regs) {
            if (!REG_WITHDRAWN.equals(reg.getStatus())) {
                reg.setStatus(REG_WITHDRAWN);
                registrationMapper.updateById(reg);
            }
        }

        // Physically delete the team row — registration snapshots remain for audit.
        teamMapper.deleteById(teamId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decline(Long teamId, Long userId) {
        Team team = requireTeam(teamId);

        if (!userId.equals(team.getMember2UserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅受邀队友可拒绝邀请");
        }

        // Withdraw the captain's registration (only they have one so far).
        List<Registration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<Registration>().eq(Registration::getTeamId, teamId));
        for (Registration reg : regs) {
            if (!REG_WITHDRAWN.equals(reg.getStatus())) {
                reg.setStatus(REG_WITHDRAWN);
                registrationMapper.updateById(reg);
            }
        }

        teamMapper.deleteById(teamId);
    }

    @Override
    public Team getMyTeam(Long eventId, Long userId) {
        return teamMapper.selectOne(
                new LambdaQueryWrapper<Team>()
                        .eq(Team::getEventId, eventId)
                        .and(w -> w.eq(Team::getMember1UserId, userId)
                                .or().eq(Team::getMember2UserId, userId)));
    }

    @Override
    public TeamVO toVO(Team team) {
        if (team == null) return null;
        TeamVO vo = new TeamVO();
        vo.setId(team.getId());
        vo.setEventId(team.getEventId());
        vo.setMember1UserId(team.getMember1UserId());
        vo.setMember2UserId(team.getMember2UserId());
        vo.setName(team.getName());
        vo.setStatus(team.getStatus());

        List<Long> ids = idsOf(team.getMember1UserId(), team.getMember2UserId());
        if (!ids.isEmpty()) {
            Map<Long, User> userMap = userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            User m1 = userMap.get(team.getMember1UserId());
            User m2 = userMap.get(team.getMember2UserId());
            if (m1 != null) vo.setMember1Name(m1.getNickname());
            if (m2 != null) vo.setMember2Name(m2.getNickname());
        }
        return vo;
    }

    // ---------- helpers ----------

    private Team requireTeam(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "队伍不存在");
        }
        return team;
    }

    private List<Long> idsOf(Long... ids) {
        if (ids == null) return Collections.emptyList();
        List<Long> out = new java.util.ArrayList<>();
        for (Long id : ids) {
            if (id != null) out.add(id);
        }
        return out;
    }
}
