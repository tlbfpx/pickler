package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.enums.GroupingStrategyType;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
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
import com.heypickler.service.impl.GroupingServiceImpl;
import com.heypickler.vo.GroupVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupingServiceImplTest {

    @Mock private EventMapper eventMapper;
    @Mock private MatchGroupMapper matchGroupMapper;
    @Mock private GroupAssignmentMapper groupAssignmentMapper;
    @Mock private RegistrationMapper registrationMapper;
    @Mock private TeamMapper teamMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private GroupingServiceImpl groupingService;

    @BeforeAll
    static void warmLambdaCache() {
        // LambdaWrapper resolution needs the static TableInfo cache (empty without Spring).
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        for (Class<?> c : List.of(Event.class, Registration.class, Team.class, MatchGroup.class, GroupAssignment.class)) {
            org.apache.ibatis.builder.MapperBuilderAssistant a =
                    new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, c);
        }
    }

    // ---------- group ----------

    @Test
    void group_serpentineSingles_createsGroupsAndDistributesByStarPoints() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(registrationMapper.selectList(any())).thenReturn(List.of(
                reg(1L, null), reg(2L, null), reg(3L, null), reg(4L, null)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, 100), user(2L, 80), user(3L, 60), user(4L, 40)));

        List<MatchGroup> createdGroups = new ArrayList<>();
        long[] gid = {10};
        when(matchGroupMapper.insert(any())).thenAnswer(inv -> {
            MatchGroup m = inv.getArgument(0);
            m.setId(gid[0]++);
            createdGroups.add(m);
            return 1;
        });
        List<GroupAssignment> createdAssignments = new ArrayList<>();
        when(groupAssignmentMapper.insert(any())).thenAnswer(inv -> {
            GroupAssignment a = inv.getArgument(0);
            createdAssignments.add(a);
            return 1;
        });
        when(matchGroupMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(createdGroups));
        when(groupAssignmentMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(createdAssignments));

        List<GroupVO> result = groupingService.group(1L, GroupingStrategyType.SERPENTINE, 2);

        assertEquals(2, createdGroups.size());
        assertEquals(4, createdAssignments.size());
        assertEquals(2, result.size());

        // serpentine 4 (scores 100,80,60,40) into 2: group0=rank1+rank4 (users 1,4), group1=users 2,3
        Map<Long, Set<Long>> usersByGroup = createdAssignments.stream().collect(
                Collectors.groupingBy(GroupAssignment::getGroupId,
                        Collectors.mapping(GroupAssignment::getUserId, Collectors.toSet())));
        assertEquals(Set.of(1L, 4L), usersByGroup.get(10L));
        assertEquals(Set.of(2L, 3L), usersByGroup.get(11L));
    }

    @Test
    void group_lockedEvent_throws() {
        Event event = event(1L, "STAR", "SINGLES", true);
        when(eventMapper.selectById(1L)).thenReturn(event);

        BizException ex = assertThrows(BizException.class,
                () -> groupingService.group(1L, GroupingStrategyType.SERPENTINE, 2));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
        verify(matchGroupMapper, never()).insert(any());
    }

    @Test
    void group_manual_createsEmptyGroupsOnly() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(registrationMapper.selectList(any())).thenReturn(List.of(
                reg(1L, null), reg(2L, null), reg(3L, null)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(1L, 10), user(2L, 5), user(3L, 1)));
        when(matchGroupMapper.insert(any())).thenAnswer(inv -> {
            ((MatchGroup) inv.getArgument(0)).setId(99L);
            return 1;
        });
        when(matchGroupMapper.selectList(any())).thenReturn(List.of());
        when(groupAssignmentMapper.selectList(any())).thenReturn(List.of());

        groupingService.group(1L, GroupingStrategyType.MANUAL, 3);

        // MANUAL builds the requested empty groups but assigns nobody.
        verify(matchGroupMapper, times(3)).insert(any());
        verify(groupAssignmentMapper, never()).insert(any());
    }

    @Test
    void group_doubles_usesConfirmedTeamsAndMemberSumRanking() {
        Event event = event(1L, "STAR", "DOUBLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(teamMapper.selectList(any())).thenReturn(List.of(
                team(20L, 1L, 2L), team(21L, 3L, 4L)));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, 100), user(2L, 20), user(3L, 60), user(4L, 50)));
        List<MatchGroup> createdGroups = new ArrayList<>();
        long[] gid = {30};
        when(matchGroupMapper.insert(any())).thenAnswer(inv -> {
            MatchGroup m = inv.getArgument(0);
            m.setId(gid[0]++);
            createdGroups.add(m);
            return 1;
        });
        List<GroupAssignment> createdAssignments = new ArrayList<>();
        when(groupAssignmentMapper.insert(any())).thenAnswer(inv -> {
            createdAssignments.add(inv.getArgument(0));
            return 1;
        });
        when(matchGroupMapper.selectList(any())).thenReturn(new ArrayList<>(createdGroups));
        when(groupAssignmentMapper.selectList(any())).thenReturn(new ArrayList<>(createdAssignments));
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(team(20L, 1L, 2L), team(21L, 3L, 4L)));

        groupingService.group(1L, GroupingStrategyType.RANDOM, 2);

        // Both teams assigned, team ids preserved (not user ids).
        assertEquals(2, createdAssignments.size());
        Set<Long> teamIds = createdAssignments.stream()
                .map(GroupAssignment::getTeamId).collect(Collectors.toSet());
        assertEquals(Set.of(20L, 21L), teamIds);
        createdAssignments.forEach(a -> assertNull(a.getUserId()));
    }

    @Test
    void group_noParticipants_throws() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(registrationMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> groupingService.group(1L, GroupingStrategyType.SERPENTINE, 2));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verify(matchGroupMapper, never()).insert(any());
    }

    // ---------- reassign ----------

    @Test
    void reassign_unlocked_movesAssignmentToTargetGroup() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        GroupAssignment a = new GroupAssignment();
        a.setId(500L);
        a.setGroupId(10L);
        a.setEventId(1L);
        when(groupAssignmentMapper.selectById(500L)).thenReturn(a);
        MatchGroup target = new MatchGroup();
        target.setId(11L);
        target.setEventId(1L);
        when(matchGroupMapper.selectById(11L)).thenReturn(target);
        when(groupAssignmentMapper.update(any(), any())).thenReturn(1);

        groupingService.reassign(1L, 500L, 11L);

        verify(groupAssignmentMapper).update(any(), any());
    }

    @Test
    void reassign_locked_throws() {
        Event event = event(1L, "STAR", "SINGLES", true);
        when(eventMapper.selectById(1L)).thenReturn(event);

        BizException ex = assertThrows(BizException.class,
                () -> groupingService.reassign(1L, 500L, 11L));
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
        verify(groupAssignmentMapper, never()).update(any(), any());
    }

    // ---------- lock / unlock ----------

    @Test
    void lock_setsGroupingLockedTrue() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(any(), any())).thenReturn(1);

        groupingService.lock(1L);

        verify(eventMapper).update(isNull(), any());
    }

    @Test
    void unlock_clearsAssignmentsThenGroupsAndUnlocks() {
        Event event = event(1L, "STAR", "SINGLES", true);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(groupAssignmentMapper.delete(any())).thenReturn(4);
        when(matchGroupMapper.delete(any())).thenReturn(2);
        when(eventMapper.update(any(), any())).thenReturn(1);

        groupingService.unlock(1L);

        // child (assignments) deleted before parent (groups)
        verify(groupAssignmentMapper).delete(any());
        verify(matchGroupMapper).delete(any());
        verify(eventMapper).update(isNull(), any());
    }

    // ---------- getGroups ----------

    @Test
    void getGroups_resolvesMemberNamesForSingles() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        MatchGroup g = new MatchGroup();
        g.setId(10L);
        g.setEventId(1L);
        g.setGroupIndex(0);
        g.setName("A");
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(g));
        GroupAssignment a = new GroupAssignment();
        a.setId(500L);
        a.setGroupId(10L);
        a.setEventId(1L);
        a.setUserId(7L);
        a.setSeed(1);
        when(groupAssignmentMapper.selectList(any())).thenReturn(List.of(a));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7L, 50)));

        List<GroupVO> result = groupingService.getGroups(1L);

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).getName());
        assertEquals(1, result.get(0).getAssignments().size());
        assertEquals("U7", result.get(0).getAssignments().get(0).getDisplayName());
        assertNull(result.get(0).getAssignments().get(0).getTeamId());
    }

    @Test
    void getGroups_noGroups_returnsEmpty() {
        Event event = event(1L, "STAR", "SINGLES", false);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of());

        assertTrue(groupingService.getGroups(1L).isEmpty());
    }

    // ---------- helpers ----------

    private Event event(Long id, String type, String format, boolean locked) {
        Event e = new Event();
        e.setId(id);
        e.setType(type);
        e.setFormat(format);
        e.setGroupingLocked(locked);
        e.setStatus("OPEN");
        return e;
    }

    private Registration reg(Long userId, Long teamId) {
        Registration r = new Registration();
        r.setUserId(userId);
        r.setEventId(1L);
        r.setStatus("REGISTERED");
        r.setTeamId(teamId);
        return r;
    }

    private User user(Long id, int starPoints) {
        User u = new User();
        u.setId(id);
        u.setNickname("U" + id);
        u.setStarPoints(starPoints);
        u.setPartyPoints(starPoints);
        return u;
    }

    private Team team(Long id, Long m1, Long m2) {
        Team t = new Team();
        t.setId(id);
        t.setEventId(1L);
        t.setMember1UserId(m1);
        t.setMember2UserId(m2);
        t.setStatus("CONFIRMED");
        return t;
    }
}
