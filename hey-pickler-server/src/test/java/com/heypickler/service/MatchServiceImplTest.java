package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.enums.MatchStatus;
import com.heypickler.vo.MatchVO;
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
    @Mock private com.heypickler.service.NotificationService notificationService;
    // Loop-v4 D12 — generate() now opens a BATCH-mode SqlSession for bulk insert.
    @Mock private org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory;

    @InjectMocks private MatchServiceImpl matchService;

    @org.junit.jupiter.api.BeforeEach
    void wireBatchSession() {
        // Make the batch session return the same MatchMapper mock the test
        // already stubs, so `session.getMapper(MatchMapper.class).insert(m)`
        // reuses the existing matchMapper.insert(any()) expectations.
        org.apache.ibatis.session.SqlSession session =
                org.mockito.Mockito.mock(org.apache.ibatis.session.SqlSession.class);
        org.mockito.Mockito.when(sqlSessionFactory.openSession(
                org.apache.ibatis.session.ExecutorType.BATCH)).thenReturn(session);
        org.mockito.Mockito.when(session.getMapper(MatchMapper.class)).thenReturn(matchMapper);
        // Do not set strict stubs; flushStatements() must be a no-op.
        org.mockito.Mockito.when(session.flushStatements()).thenReturn(java.util.Collections.emptyList());
    }

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
    void submitScore_eventCompleted_matchScheduled_allowsReRecord() {
        // Fix-data path: operator who reset a match on a COMPLETED event
        // must be able to re-record. submitScore ALLOWS this; only the match's
        // own COMPLETED status still blocks re-recording (see test below).
        Event event = event(1L, "SINGLES", true, "COMPLETED");
        when(eventMapper.selectById(1L)).thenReturn(event);
        Match m = new Match();
        m.setId(50L);
        m.setEventId(1L);
        m.setSlotAUserId(1L);
        m.setSlotBUserId(2L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectById(50L)).thenReturn(m);

        matchService.submitScore(50L, 1L, List.of(game(1, 21, 15), game(2, 21, 18)), true);
        verify(matchMapper).updateById(any(Match.class));
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

    // ──────────────── Loop-v9 — MatchServiceImpl coverage sprint ────────────────

    @Test
    void listMyMatches_singlesPath_returnsVO() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "SINGLES", true, "OPEN"));
        Match m = new Match();
        m.setId(11L);
        m.setEventId(1L);
        m.setSlotAUserId(7L);
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));
        when(teamMapper.selectList(any())).thenReturn(List.of());

        List<MatchVO> result = matchService.listMyMatches(1L, 7L);
        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
    }

    @Test
    void listMyMatches_doublesPath_mergesUserAndTeamMatches() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "DOUBLES", true, "OPEN"));
        Match userM = new Match();
        userM.setId(11L);
        userM.setSlotAUserId(7L);
        userM.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any()))
                .thenReturn(List.of(userM))
                .thenReturn(List.of(userM));
        Team team = new Team();
        team.setId(99L);
        team.setMember1UserId(7L);
        when(teamMapper.selectList(any())).thenReturn(List.of(team));

        assertEquals(1, matchService.listMyMatches(1L, 7L).size());
    }

    @Test
    void listMyMatches_noUserOrTeamMatches_returnsEmpty() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "SINGLES", true, "OPEN"));
        when(matchMapper.selectList(any())).thenReturn(List.of());
        when(teamMapper.selectList(any())).thenReturn(List.of());
        assertTrue(matchService.listMyMatches(1L, 7L).isEmpty());
    }

    @Test
    void listEventMatches_groupsMatchesByMatchGroup() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "SINGLES", true, "OPEN"));
        com.heypickler.entity.MatchGroup group = new com.heypickler.entity.MatchGroup();
        group.setId(1L);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(group));
        Match m1 = new Match();
        m1.setId(11L);
        m1.setGroupId(1L);
        m1.setSlotAUserId(7L);
        m1.setStatus(MatchStatus.SCHEDULED);
        Match m2 = new Match();
        m2.setId(12L);
        m2.setGroupId(1L);
        m2.setSlotAUserId(8L);
        m2.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m1, m2));

        List<List<MatchVO>> result = matchService.listEventMatches(1L);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).size());
    }

    @Test
    void toVO_setsAllFields() {
        Match m = new Match();
        m.setId(11L);
        m.setEventId(1L);
        m.setGroupId(2L);
        m.setSlotAUserId(7L);
        m.setSlotBUserId(8L);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setGamesWonA(0);
        m.setGamesWonB(0);
        MatchVO vo = matchService.toVO(m);
        assertEquals(11L, vo.getId());
        assertEquals(1L, vo.getEventId());
        assertEquals(2L, vo.getGroupId());
        assertEquals(7L, vo.getSlotAUserId());
        assertEquals(8L, vo.getSlotBUserId());
    }

    @Test
    void toVO_withGames_returnsEmptyListForEmptyString() {
        Match m = new Match();
        m.setId(11L);
        m.setStatus(MatchStatus.SCHEDULED);
        m.setGames("");
        MatchVO vo = matchService.toVO(m);
        assertNotNull(vo.getGames());
        assertTrue(vo.getGames().isEmpty());
    }

    // ──────────────── Loop-v12 — 90% gate: complete + standings paths ────────────────

    @Test
    void complete_alreadyCompleted_isNoOp() {
        Event e = event(1L, "SINGLES", true, "COMPLETED");
        when(eventMapper.selectById(1L)).thenReturn(e);
        matchService.complete(1L);
        verify(matchMapper, never()).selectList(any());
    }

    @Test
    void complete_unfinishedMatches_throwsParam() {
        Event e = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(e);
        Match m = new Match();
        m.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));

        com.heypickler.common.exception.BizException ex =
                assertThrows(com.heypickler.common.exception.BizException.class,
                        () -> matchService.complete(1L));
        assertEquals(1001, ex.getCode());
        assertEquals(true, ex.getMessage().contains("1 场比赛未完成"));
    }

    @Test
    void complete_allFinished_callsPlacementAndNotifies() {
        Event e = event(1L, "SINGLES", true, "IN_PROGRESS");
        e.setCreatedBy(99L);
        e.setTitle("Cup");
        when(eventMapper.selectById(1L)).thenReturn(e);
        Match m = new Match();
        m.setStatus(MatchStatus.COMPLETED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));
        when(eventMapper.update(eq(null), any())).thenReturn(1);

        matchService.complete(1L);

        verify(placementService).issue(1L);
        verify(notificationService).push(99L, "EVENT_COMPLETED", "赛事已结束",
                "《Cup》已结束，名次积分已发放", "/events/1?tab=result");
    }

    @Test
    void complete_allFinished_noCreator_skipsNotify() {
        Event e = event(1L, "SINGLES", true, "IN_PROGRESS");
        e.setCreatedBy(null);
        when(eventMapper.selectById(1L)).thenReturn(e);
        Match m = new Match();
        m.setStatus(MatchStatus.COMPLETED);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));

        matchService.complete(1L);
        verify(notificationService, never()).push(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void standings_doublesWithTeamId_routesThroughTeamMap() {
        Event e = event(1L, "DOUBLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(e);
        MatchGroup g = new MatchGroup();
        g.setId(10L);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(g));
        Match m = new Match();
        m.setId(11L);
        m.setGroupId(10L);
        m.setSlotATeamId(99L);
        m.setSlotBTeamId(88L);
        m.setStatus(MatchStatus.COMPLETED);
        m.setGamesWonA(2);
        m.setGamesWonB(0);
        when(matchMapper.selectList(any())).thenReturn(List.of(m));
        Team t1 = new Team();
        t1.setId(99L);
        t1.setMember1UserId(7L);
        Team t2 = new Team();
        t2.setId(88L);
        t2.setMember1UserId(8L);
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of(t1, t2));
        User u1 = new User();
        u1.setId(7L);
        u1.setNickname("alice");
        User u2 = new User();
        u2.setId(8L);
        u2.setNickname("bob");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u1, u2));

        List<List<com.heypickler.vo.StandingVO>> result = matchService.standings(1L);
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).size());
        // Winning team (99) ranks first
        assertEquals(1, result.get(0).get(0).getRank());
        assertEquals(2, result.get(0).get(1).getRank());
    }

    @Test
    void standings_skipsMatchesWithoutStatusCompleted() {
        Event e = event(1L, "SINGLES", true, "IN_PROGRESS");
        when(eventMapper.selectById(1L)).thenReturn(e);
        MatchGroup g = new MatchGroup();
        g.setId(10L);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(g));
        Match scheduled = new Match();
        scheduled.setId(11L);
        scheduled.setGroupId(10L);
        scheduled.setSlotAUserId(7L);
        scheduled.setSlotBUserId(8L);
        scheduled.setStatus(MatchStatus.SCHEDULED);
        when(matchMapper.selectList(any())).thenReturn(List.of(scheduled));

        List<List<com.heypickler.vo.StandingVO>> result = matchService.standings(1L);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).size());  // SCHEDULED matches don't count
    }
}