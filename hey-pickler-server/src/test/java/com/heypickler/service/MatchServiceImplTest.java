package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.enums.MatchStatus;
import com.heypickler.common.exception.BizException;
import com.heypickler.entity.Event;
import com.heypickler.entity.GroupAssignment;
import com.heypickler.entity.Match;
import com.heypickler.entity.MatchGroup;
import com.heypickler.entity.Team;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.GroupAssignmentMapper;
import com.heypickler.mapper.MatchGroupMapper;
import com.heypickler.mapper.MatchMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.MatchServiceImpl;
import com.heypickler.vo.StandingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceImplTest {

    @Mock private EventMapper eventMapper;
    @Mock private MatchGroupMapper matchGroupMapper;
    @Mock private GroupAssignmentMapper groupAssignmentMapper;
    @Mock private MatchMapper matchMapper;
    @Mock private TeamMapper teamMapper;
    @Mock private UserMapper userMapper;
    @Mock private com.heypickler.service.PlacementService placementService;

    @InjectMocks private MatchServiceImpl matchService;

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Event.class, MatchGroup.class, GroupAssignment.class, Match.class, Team.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    private Event event(Long id, String format, boolean locked, String status) {
        Event e = new Event();
        e.setId(id);
        e.setFormat(format);
        e.setGroupingLocked(locked);
        e.setStatus(status);
        return e;
    }

    private MatchGroup group(Long id, Long eventId, int index) {
        MatchGroup g = new MatchGroup();
        g.setId(id);
        g.setEventId(eventId);
        g.setGroupIndex(index);
        g.setName(String.valueOf((char) ('A' + index)));
        return g;
    }

    private GroupAssignment slot(Long id, Long groupId, Long userId, Long teamId, int seed) {
        GroupAssignment a = new GroupAssignment();
        a.setId(id);
        a.setGroupId(groupId);
        a.setEventId(1L);
        a.setUserId(userId);
        a.setTeamId(teamId);
        a.setSeed(seed);
        return a;
    }

    private Match.GameScore game(int n, int a, int b) {
        Match.GameScore g = new Match.GameScore();
        g.setGame(n); g.setA(a); g.setB(b);
        return g;
    }

    private Match match(Long id, Long groupId, MatchStatus status,
                        Long slotAUser, Long slotBUser, Long slotATeam, Long slotBTeam,
                        int wonA, int wonB, List<Match.GameScore> games) {
        Match m = new Match();
        m.setId(id);
        m.setGroupId(groupId);
        m.setEventId(1L);
        m.setStatus(status);
        m.setSlotAUserId(slotAUser);
        m.setSlotBUserId(slotBUser);
        m.setSlotATeamId(slotATeam);
        m.setSlotBTeamId(slotBTeam);
        m.setGamesWonA(wonA);
        m.setGamesWonB(wonB);
        m.setGameList(games);
        return m;
    }

    private Team team(Long id, Long m1, Long m2) {
        Team t = new Team();
        t.setId(id);
        t.setMember1UserId(m1);
        t.setMember2UserId(m2);
        return t;
    }

    private User user(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        return u;
    }

    // ---------- generate ----------

    @Test
    void generate_singles_fourPlayers_twoGroups_sixMatchesAndTransitionsToInProgress() {
        Event event = event(1L, "SINGLES", true, "OPEN");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(
                group(10L, 1L, 0), group(11L, 1L, 1)));
        // Stub groupAssignmentMapper per-call: the impl iterates matchGroupMapper's
        // result order, which we stub as [group 10, group 11]. Use a counter.
        List<List<GroupAssignment>> responses = List.of(
                List.of(slot(100L, 10L, 1L, null, 1), slot(101L, 10L, 2L, null, 2)),
                List.of(slot(102L, 11L, 3L, null, 1), slot(103L, 11L, 4L, null, 2)));
        int[] idx = {0};
        when(groupAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            int i = Math.min(idx[0]++, responses.size() - 1);
            return responses.get(i);
        });
        when(matchMapper.selectList(any())).thenReturn(List.of());
        when(matchMapper.insert(any(Match.class))).thenAnswer(inv -> { Match m = inv.getArgument(0); m.setId(System.nanoTime()); return 1; });
        when(eventMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        var matches = matchService.generate(1L);

        // 4 players split into two groups of 2: 1 match per group = 2 total.
        assertEquals(2, matches.size());
        verify(eventMapper).update(isNull(), any(LambdaUpdateWrapper.class)); // -> IN_PROGRESS
    }

    @Test
    void generate_whenEventNotLocked_throws() {
        Event event = event(1L, "SINGLES", false, "OPEN");
        when(eventMapper.selectById(1L)).thenReturn(event);

        BizException ex = assertThrows(BizException.class,
                () -> matchService.generate(1L));
        assertEquals(1006, ex.getCode());
        verify(matchMapper, never()).insert(any());
    }

    @Test
    void generate_eventAlreadyInProgress_returnsMatches_noStatusTransition() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(group(10L, 1L, 0)));
        when(groupAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                slot(100L, 10L, 1L, null, 1), slot(101L, 10L, 2L, null, 2)));
        when(matchMapper.selectList(any())).thenReturn(List.of());
        when(matchMapper.insert(any(Match.class))).thenAnswer(inv -> { Match m = inv.getArgument(0); m.setId(99L); return 1; });

        var matches = matchService.generate(1L);

        assertEquals(1, matches.size());
        verify(eventMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void generate_idempotent_clearsPriorMatches() {
        Event event = event(1L, "SINGLES", true, "OPEN");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(group(10L, 1L, 0)));
        when(groupAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                slot(100L, 10L, 1L, null, 1), slot(101L, 10L, 2L, null, 2)));
        Match old = new Match();
        old.setId(99L);
        when(matchMapper.selectList(any())).thenReturn(List.of(old));
        when(matchMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(matchMapper.insert(any(Match.class))).thenAnswer(inv -> { Match m = inv.getArgument(0); m.setId(100L); return 1; });
        when(eventMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        matchService.generate(1L);

        verify(matchMapper).delete(any(LambdaQueryWrapper.class));
    }

    // ---------- submitScore ----------

    @Test
    void submitScore_validTwoZero_byParticipant_marksCompleted() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setGroupId(10L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);
        when(matchMapper.updateById(any(Match.class))).thenReturn(1);

        matchService.submitScore(50L, 1L, List.of(game(1, 21, 15), game(2, 21, 18)), false);

        ArgumentCaptor<Match> cap = ArgumentCaptor.forClass(Match.class);
        verify(matchMapper).updateById(cap.capture());
        Match updated = cap.getValue();
        assertEquals(MatchStatus.COMPLETED, updated.getStatus());
        assertEquals(2, updated.getGamesWonA());
        assertEquals(0, updated.getGamesWonB());
        assertEquals(1L, updated.getSubmittedByUserId());
    }

    @Test
    void submitScore_byAdmin_alwaysAllowed() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);
        when(matchMapper.updateById(any(Match.class))).thenReturn(1);

        matchService.submitScore(50L, 999L, List.of(game(1, 21, 15), game(2, 21, 18)), true);

        verify(matchMapper).updateById(any(Match.class));
    }

    @Test
    void submitScore_nonParticipant_throwsForbidden() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);

        BizException ex = assertThrows(BizException.class,
                () -> matchService.submitScore(50L, 999L, List.of(game(1, 21, 15), game(2, 21, 18)), false));
        assertEquals(403, ex.getCode());
        verify(matchMapper, never()).updateById(any());
    }

    @Test
    void submitScore_eventCompleted_throwsInvalidStatusTransition() {
        Event event = event(1L, "SINGLES", true, "COMPLETED");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);

        BizException ex = assertThrows(BizException.class,
                () -> matchService.submitScore(50L, 1L, List.of(game(1, 21, 15), game(2, 21, 18)), false));
        assertEquals(1006, ex.getCode());
    }

    @Test
    void submitScore_alreadyCompleted_throwsInvalidStatusTransition() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.COMPLETED);
        when(matchMapper.selectById(50L)).thenReturn(m);

        BizException ex = assertThrows(BizException.class,
                () -> matchService.submitScore(50L, 1L, List.of(game(1, 21, 15), game(2, 21, 18)), false));
        assertEquals(1006, ex.getCode());
    }

    @Test
    void submitScore_invalid21to20_throwsParam() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);

        BizException ex = assertThrows(BizException.class,
                () -> matchService.submitScore(50L, 1L, List.of(game(1, 21, 20)), false));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void submitScore_doubles_teamMemberAllowed() {
        Event event = event(1L, "DOUBLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotATeamId(100L);
        m.setSlotBTeamId(101L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);
        when(teamMapper.selectById(100L)).thenReturn(team(100L, 1L, 2L));
        when(teamMapper.selectById(101L)).thenReturn(team(101L, 3L, 4L));
        when(matchMapper.updateById(any(Match.class))).thenReturn(1);

        matchService.submitScore(50L, 2L, List.of(game(1, 21, 15), game(2, 21, 18)), false);

        verify(matchMapper).updateById(any(Match.class));
    }

    // ---------- reset ----------

    @Test
    void reset_completedMatch_clearsScoresAndResetsToScheduled() {
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.COMPLETED);
        m.setGames("[{\"game\":1,\"a\":21,\"b\":15}]");
        m.setGamesWonA(1);
        m.setGamesWonB(0);
        when(matchMapper.selectById(50L)).thenReturn(m);
        when(matchMapper.update(isNull(), any())).thenReturn(1);

        matchService.reset(50L);

        verify(matchMapper).update(isNull(), any());
    }

    // ---------- standings ----------

    @Test
    void standings_clearlyRanksByWins() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(group(10L, 1L, 0)));
        List<Match> matches = new ArrayList<>();
        matches.add(match(1L, 10L, MatchStatus.COMPLETED, 1L, 2L, null, null, 2, 0, List.of(game(1, 21, 15), game(2, 21, 18))));
        matches.add(match(2L, 10L, MatchStatus.COMPLETED, 1L, 3L, null, null, 2, 0, List.of(game(1, 21, 10), game(2, 21, 5))));
        matches.add(match(3L, 10L, MatchStatus.COMPLETED, 2L, 3L, null, null, 2, 0, List.of(game(1, 21, 12), game(2, 21, 7))));
        when(matchMapper.selectList(any())).thenReturn(matches);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, "Alice"), user(2L, "Bob"), user(3L, "Carol")));

        List<List<StandingVO>> result = matchService.standings(1L);

        assertEquals(1, result.size());
        List<StandingVO> s = result.get(0);
        assertEquals(3, s.size());
        assertEquals(1L, s.get(0).getParticipantKey().longValue()); // 2W
        assertEquals(2, s.get(0).getWins());
        assertEquals(2L, s.get(1).getParticipantKey().longValue()); // 1W
        assertEquals(1, s.get(1).getWins());
        assertEquals(3L, s.get(2).getParticipantKey().longValue()); // 0W
    }

    @Test
    void standings_tiedWins_brokenByHeadToHead() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(group(10L, 1L, 0)));
        List<Match> matches = new ArrayList<>();
        // P1 beats P2; P2 beats P3. P1 vs P3 not done.
        matches.add(match(1L, 10L, MatchStatus.COMPLETED, 1L, 2L, null, null, 2, 0, List.of(game(1, 21, 15), game(2, 21, 18))));
        matches.add(match(2L, 10L, MatchStatus.COMPLETED, 2L, 3L, null, null, 2, 0, List.of(game(1, 21, 15), game(2, 21, 18))));
        when(matchMapper.selectList(any())).thenReturn(matches);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(1L, "Alice"), user(2L, "Bob"), user(3L, "Carol")));

        List<List<StandingVO>> result = matchService.standings(1L);
        List<StandingVO> s = result.get(0);
        assertEquals(1L, s.get(0).getParticipantKey().longValue()); // P1 by head-to-head
        assertEquals(2L, s.get(1).getParticipantKey().longValue());
    }

    // ---------- complete ----------

    @Test
    void complete_allMatchesFinished_setsEventCompleted() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(1L);
        m.setStatus(MatchStatus.COMPLETED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));
        when(eventMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        matchService.complete(1L);

        verify(eventMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void complete_withUnfinishedMatch_throwsParam() {
        Event event = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(1L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));

        BizException ex = assertThrows(BizException.class, () -> matchService.complete(1L));
        assertEquals(1001, ex.getCode());
    }
}