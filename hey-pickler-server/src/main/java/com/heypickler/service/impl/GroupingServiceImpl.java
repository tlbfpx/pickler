package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.common.enums.EventFormat;
import com.heypickler.common.enums.GroupingStrategyType;
import com.heypickler.common.enums.TeamStatus;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.Participant;
import com.heypickler.entity.Event;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.entity.MatchGroup;
import com.heypickler.entity.Registration;
import com.heypickler.entity.Team;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.GroupAssignmentMapper;
import com.heypickler.mapper.MatchGroupMapper;
import com.heypickler.mapper.RegistrationMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.GroupingService;
import com.heypickler.service.GroupingStrategy;
import com.heypickler.vo.AssignmentVO;
import com.heypickler.vo.GroupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Grouping lifecycle for an event. Participants come from active registrations
 * (SINGLES) or confirmed teams (DOUBLES/MIXED); rank score is taken from the
 * event's point track (STAR -> starPoints, PARTY -> partyPoints; teams sum both
 * members). Strategies are stateless and instantiated per call.
 */
@Service
@RequiredArgsConstructor
public class GroupingServiceImpl implements GroupingService {

    private final EventMapper eventMapper;
    private final MatchGroupMapper matchGroupMapper;
    private final GroupAssignmentMapper groupAssignmentMapper;
    private final RegistrationMapper registrationMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<GroupVO> group(Long eventId, GroupingStrategyType strategy, int groupCount) {
        Event event = requireEvent(eventId);
        guardNotLocked(event, "重新分组");
        if (groupCount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "组数必须大于0");
        }

        // replace any existing grouping (child -> parent); rolled back on later failure
        groupAssignmentMapper.delete(new LambdaQueryWrapper<GroupAssignment>()
                .eq(GroupAssignment::getEventId, eventId));
        matchGroupMapper.delete(new LambdaQueryWrapper<MatchGroup>()
                .eq(MatchGroup::getEventId, eventId));

        List<Participant> participants = buildParticipants(event);
        if (participants.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无可分组的参赛者");
        }
        participants.sort(Comparator.comparingInt(Participant::getRankScore).reversed());

        List<MatchGroup> groups = new ArrayList<>();
        for (int i = 0; i < groupCount; i++) {
            MatchGroup mg = new MatchGroup();
            mg.setEventId(eventId);
            mg.setGroupIndex(i);
            mg.setName(groupName(i));
            matchGroupMapper.insert(mg);
            groups.add(mg);
        }

        if (strategy != GroupingStrategyType.MANUAL) {
            List<GroupAssignment> assignments = strategyOf(strategy).assign(participants, groupCount);
            for (GroupAssignment ga : assignments) {
                int index = ga.getGroupId().intValue(); // strategy stored the group index here
                ga.setGroupId(groups.get(index).getId());
                ga.setEventId(eventId);
                groupAssignmentMapper.insert(ga);
            }
        }

        return getGroups(eventId);
    }

    @Override
    public List<GroupVO> getGroups(Long eventId) {
        requireEvent(eventId);
        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>()
                        .eq(MatchGroup::getEventId, eventId)
                        .orderByAsc(MatchGroup::getGroupIndex));
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }

        List<GroupAssignment> assignments = groupAssignmentMapper.selectList(
                new LambdaQueryWrapper<GroupAssignment>()
                        .eq(GroupAssignment::getEventId, eventId)
                        .orderByAsc(GroupAssignment::getSeed));

        NameResolver names = NameResolver.build(assignments, teamMapper, userMapper);
        Map<Long, List<GroupAssignment>> byGroup = assignments.stream()
                .collect(Collectors.groupingBy(GroupAssignment::getGroupId));

        return groups.stream().map(g -> {
            GroupVO vo = new GroupVO();
            vo.setId(g.getId());
            vo.setEventId(eventId);
            vo.setGroupIndex(g.getGroupIndex());
            vo.setName(g.getName());
            vo.setAssignments(byGroup.getOrDefault(g.getId(), Collections.emptyList()).stream()
                    .map(a -> toAssignmentVO(a, names)).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reassign(Long eventId, Long assignmentId, Long targetGroupId) {
        Event event = requireEvent(eventId);
        guardNotLocked(event, "调整分组");

        GroupAssignment assignment = groupAssignmentMapper.selectById(assignmentId);
        if (assignment == null || !eventId.equals(assignment.getEventId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "分组记录不存在");
        }
        MatchGroup target = matchGroupMapper.selectById(targetGroupId);
        if (target == null || !eventId.equals(target.getEventId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目标组不存在");
        }

        groupAssignmentMapper.update(null, new LambdaUpdateWrapper<GroupAssignment>()
                .eq(GroupAssignment::getId, assignmentId)
                .set(GroupAssignment::getGroupId, targetGroupId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lock(Long eventId) {
        requireEvent(eventId);
        eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                .eq(Event::getId, eventId)
                .set(Event::getGroupingLocked, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long eventId) {
        requireEvent(eventId);
        // child first, then parent
        groupAssignmentMapper.delete(new LambdaQueryWrapper<GroupAssignment>()
                .eq(GroupAssignment::getEventId, eventId));
        matchGroupMapper.delete(new LambdaQueryWrapper<MatchGroup>()
                .eq(MatchGroup::getEventId, eventId));
        eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                .eq(Event::getId, eventId)
                .set(Event::getGroupingLocked, false));
    }

    // ---------- internals ----------

    private List<Participant> buildParticipants(Event event) {
        boolean star = "STAR".equals(event.getType());
        String format = event.getFormat() != null ? event.getFormat() : EventFormat.SINGLES.name();

        if (EventFormat.SINGLES.name().equals(format)) {
            List<Registration> regs = registrationMapper.selectList(
                    new LambdaQueryWrapper<Registration>()
                            .eq(Registration::getEventId, event.getId())
                            .in(Registration::getStatus, "REGISTERED", "CHECKED_IN")
                            .isNull(Registration::getTeamId));
            if (regs.isEmpty()) {
                return Collections.emptyList();
            }
            Map<Long, User> users = loadUsers(regs.stream()
                    .map(Registration::getUserId).collect(Collectors.toSet()));
            return regs.stream()
                    .map(r -> Participant.singles(r.getUserId(), points(users.get(r.getUserId()), star)))
                    .collect(Collectors.toList());
        }

        List<Team> teams = teamMapper.selectList(
                new LambdaQueryWrapper<Team>()
                        .eq(Team::getEventId, event.getId())
                        .eq(Team::getStatus, TeamStatus.CONFIRMED.name()));
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> memberIds = new HashSet<>();
        teams.forEach(t -> {
            memberIds.add(t.getMember1UserId());
            memberIds.add(t.getMember2UserId());
        });
        Map<Long, User> users = loadUsers(memberIds);
        return teams.stream()
                .map(t -> Participant.team(t.getId(),
                        points(users.get(t.getMember1UserId()), star)
                                + points(users.get(t.getMember2UserId()), star)))
                .collect(Collectors.toList());
    }

    private GroupingStrategy strategyOf(GroupingStrategyType type) {
        switch (type) {
            case SERPENTINE:
                return new SerpentineStrategy();
            case RANDOM:
                return new RandomStrategy();
            default:
                throw new BizException(ErrorCode.PARAM_ERROR, "不支持的分组策略: " + type);
        }
    }

    private AssignmentVO toAssignmentVO(GroupAssignment a, NameResolver names) {
        AssignmentVO vo = new AssignmentVO();
        vo.setId(a.getId());
        vo.setUserId(a.getUserId());
        vo.setTeamId(a.getTeamId());
        vo.setSeed(a.getSeed());
        vo.setDisplayName(names.displayNameOf(a));
        return vo;
    }

    private Map<Long, User> loadUsers(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private int points(User user, boolean star) {
        return user == null ? 0 : (star ? user.getStarPoints() : user.getPartyPoints());
    }

    private String groupName(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private Event requireEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return event;
    }

    private void guardNotLocked(Event event, String action) {
        if (Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "赛事已分组锁定，" + action + "请先解锁");
        }
    }

    /** Resolves display names: singles -> nickname, teams -> "member1 / member2". */
    private static final class NameResolver {
        private final Map<Long, User> users;
        private final Map<Long, Team> teams;

        private NameResolver(Map<Long, User> users, Map<Long, Team> teams) {
            this.users = users;
            this.teams = teams;
        }

        static NameResolver build(List<GroupAssignment> assignments, TeamMapper teamMapper, UserMapper userMapper) {
            Set<Long> userIds = assignments.stream().map(GroupAssignment::getUserId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> teamIds = assignments.stream().map(GroupAssignment::getTeamId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());

            Map<Long, Team> teams = teamIds.isEmpty() ? Collections.emptyMap()
                    : teamMapper.selectBatchIds(teamIds).stream()
                            .collect(Collectors.toMap(Team::getId, t -> t));

            Set<Long> allUserIds = new HashSet<>(userIds);
            teams.values().forEach(t -> {
                allUserIds.add(t.getMember1UserId());
                allUserIds.add(t.getMember2UserId());
            });
            Map<Long, User> users = allUserIds.isEmpty() ? Collections.emptyMap()
                    : userMapper.selectBatchIds(allUserIds).stream()
                            .collect(Collectors.toMap(User::getId, u -> u));
            return new NameResolver(users, teams);
        }

        String displayNameOf(GroupAssignment a) {
            if (a.getUserId() != null) {
                User u = users.get(a.getUserId());
                return u == null ? null : u.getNickname();
            }
            Team t = teams.get(a.getTeamId());
            if (t == null) {
                return null;
            }
            return nickname(users.get(t.getMember1UserId())) + " / " + nickname(users.get(t.getMember2UserId()));
        }

        private String nickname(User u) {
            return u == null ? "?" : u.getNickname();
        }
    }
}
