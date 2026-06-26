package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.enums.MatchStatus;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.config.PlacementProperties;
import com.heypickler.entity.Event;
import com.heypickler.entity.EventPlacementPoints;
import com.heypickler.entity.Match;
import com.heypickler.entity.MatchGroup;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Team;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.EventPlacementPointsMapper;
import com.heypickler.mapper.GroupAssignmentMapper;
import com.heypickler.mapper.MatchGroupMapper;
import com.heypickler.mapper.MatchMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.service.impl.PlacementServiceImpl;
import com.heypickler.vo.PlacementPointsVO;
import com.heypickler.vo.StandingVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlacementServiceImplTest {

    @Mock private EventMapper eventMapper;
    @Mock private EventPlacementPointsMapper pointsMapper;
    @Mock private PointRecordMapper pointRecordMapper;
    @Mock private MatchGroupMapper matchGroupMapper;
    @Mock private GroupAssignmentMapper groupAssignmentMapper;
    @Mock private MatchMapper matchMapper;
    @Mock private TeamMapper teamMapper;
    @Mock private PointService pointService;

    private final PlacementProperties defaultProps = new PlacementProperties();

    @InjectMocks private PlacementServiceImpl placementService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        try {
            java.lang.reflect.Field f = PlacementServiceImpl.class.getDeclaredField("defaultProps");
            f.setAccessible(true);
            f.set(placementService, defaultProps);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Event.class, EventPlacementPoints.class, PointRecord.class, Team.class, Match.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    private Event event(Long id, String title) {
        Event e = new Event();
        e.setId(id);
        e.setTitle(title);
        e.setType("STAR");
        e.setStatus("IN_PROGRESS");
        return e;
    }

    private StandingVO standing(Long key, int rank, String displayName) {
        StandingVO vo = new StandingVO();
        vo.setParticipantKey(key);
        vo.setRank(rank);
        vo.setWins(0);
        vo.setLosses(0);
        vo.setGamesFor(0);
        vo.setGamesAgainst(0);
        vo.setDisplayName(displayName);
        return vo;
    }

    // ---------- getPoints ----------

    @Test
    void getPoints_noOverride_returnsDefault() {
        defaultProps.setDefaultPoints(new HashMap<>(Map.of(1, 100, 2, 60)));
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        when(pointsMapper.selectById(1L)).thenReturn(null);
        PlacementPointsVO vo = placementService.getPoints(1L);
        assertEquals("default", vo.getSource());
        assertEquals(100, vo.getPoints().get(1));
    }

    @Test
    void getPoints_withOverride_returnsCustom() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(1L);
        row.setPointsMap(Map.of(1, 200));
        when(pointsMapper.selectById(1L)).thenReturn(row);

        PlacementPointsVO vo = placementService.getPoints(1L);
        assertEquals("custom", vo.getSource());
        assertEquals(200, vo.getPoints().get(1));
    }

    // ---------- setPoints ----------

    @Test
    void setPoints_persistsOverride() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(1L);
        row.setPointsMap(Map.of(1, 50));

        placementService.setPoints(1L, row);

        verify(pointsMapper).insert(any(EventPlacementPoints.class));
    }

    @Test
    void setPoints_completedEvent_throwsInvalidStatusTransition() {
        Event e = event(1L, "Cup");
        e.setStatus("COMPLETED");
        when(eventMapper.selectById(1L)).thenReturn(e);

        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(1L);
        row.setPointsMap(Map.of(1, 50));

        BizException ex = assertThrows(BizException.class,
                () -> placementService.setPoints(1L, row));
        assertEquals(1006, ex.getCode());
        verify(pointsMapper, never()).insert(any());
    }

    @Test
    void setPoints_negativeValue_throwsParam() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(1L);
        row.setPointsMap(Map.of(1, -10));

        BizException ex = assertThrows(BizException.class,
                () -> placementService.setPoints(1L, row));
        assertEquals(1001, ex.getCode());
    }

    @Test
    void setPoints_empty_throwsParam() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(1L);
        row.setPointsMap(new HashMap<>());

        BizException ex = assertThrows(BizException.class,
                () -> placementService.setPoints(1L, row));
        assertEquals(1001, ex.getCode());
    }

    // ---------- issue ----------

    @Test
    void issue_singles_writesOneRowPerRank() {
        defaultProps.setDefaultPoints(Map.of(1, 100, 2, 60, 3, 30));
        Event e = event(1L, "Cup");
        when(eventMapper.selectById(1L)).thenReturn(e);
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(matchGroup(10L, 1L)));
        when(matchMapper.selectList(any())).thenReturn(List.of(
                match(1L, 10L, MatchStatus.COMPLETED, 11L, 22L, null, null, 2, 0),
                match(2L, 10L, MatchStatus.COMPLETED, 11L, 33L, null, null, 2, 1),
                match(3L, 10L, MatchStatus.COMPLETED, 22L, 33L, null, null, 2, 0)));
        when(pointRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(pointsMapper.selectById(1L)).thenReturn(null);

        placementService.issue(1L);

        verify(pointService).issuePlacement(1L, 11L, 100, "PLACEMENT: 赛事《Cup》第1名");
        verify(pointService).issuePlacement(1L, 22L, 60, "PLACEMENT: 赛事《Cup》第2名");
        verify(pointService).issuePlacement(1L, 33L, 30, "PLACEMENT: 赛事《Cup》第3名");
    }

    @Test
    void issue_doubles_splitsPointsFiftyFifty() {
        defaultProps.setDefaultPoints(Map.of(1, 100));
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        when(matchGroupMapper.selectList(any())).thenReturn(List.of());
        when(matchMapper.selectList(any())).thenReturn(List.of(
                match(1L, 10L, MatchStatus.COMPLETED, null, null, 99L, 88L, 2, 0)));
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of());
        when(pointRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(pointsMapper.selectById(1L)).thenReturn(null);
        Team team = new Team();
        team.setId(99L);
        team.setMember1UserId(11L);
        team.setMember2UserId(22L);
        when(teamMapper.selectById(99L)).thenReturn(team);

        placementService.issue(1L);

        // 100 / 2 = 50 / 50
        verify(pointService).issuePlacement(1L, 11L, 50, "PLACEMENT: 赛事《Cup》第1名");
        verify(pointService).issuePlacement(1L, 22L, 50, "PLACEMENT: 赛事《Cup》第1名");
    }

    @Test
    void issue_doubles_oddPoints_splitUnevenly() {
        defaultProps.setDefaultPoints(Map.of(1, 101));  // 101 = 51 + 50
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        when(matchGroupMapper.selectList(any())).thenReturn(List.of());
        when(matchMapper.selectList(any())).thenReturn(List.of(
                match(1L, 10L, MatchStatus.COMPLETED, null, null, 99L, 88L, 2, 0)));
        when(teamMapper.selectBatchIds(any())).thenReturn(List.of());
        when(pointRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(pointsMapper.selectById(1L)).thenReturn(null);
        Team team = new Team();
        team.setId(99L);
        team.setMember1UserId(11L);
        team.setMember2UserId(22L);
        when(teamMapper.selectById(99L)).thenReturn(team);

        placementService.issue(1L);

        // Splits 101 unevenly (half = 101/2 = 50, otherHalf = 101 - 50 = 51).
        verify(pointService).issuePlacement(eq(1L), eq(11L), eq(50), anyString());
        verify(pointService).issuePlacement(eq(1L), eq(22L), eq(51), anyString());
    }

    @Test
    void issue_rankBeyondTable_writesZeroPointRows() {
        defaultProps.setDefaultPoints(Map.of(1, 100, 2, 60));  // only top 2
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        when(matchGroupMapper.selectList(any())).thenReturn(List.of(matchGroup(10L, 1L)));
        when(matchMapper.selectList(any())).thenReturn(List.of(
                match(1L, 10L, MatchStatus.COMPLETED, 11L, 22L, null, null, 2, 0),
                match(2L, 10L, MatchStatus.COMPLETED, 11L, 33L, null, null, 2, 0),
                match(3L, 10L, MatchStatus.COMPLETED, 22L, 33L, null, null, 2, 0)));
        when(pointRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(pointsMapper.selectById(1L)).thenReturn(null);

        placementService.issue(1L);

        verify(pointService).issuePlacement(1L, 11L, 100, "PLACEMENT: 赛事《Cup》第1名");
        verify(pointService).issuePlacement(1L, 22L, 60, "PLACEMENT: 赛事《Cup》第2名");
        verify(pointService).issuePlacement(1L, 33L, 0, "PLACEMENT: 赛事《Cup》第3名");
    }

    @Test
    void issue_alreadyIssued_throws() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L, "Cup"));
        when(pointRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BizException ex = assertThrows(BizException.class,
                () -> placementService.issue(1L));
        assertEquals(1006, ex.getCode());
        verify(pointService, never()).issuePlacement(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void issue_eventNotFound_throws() {
        when(eventMapper.selectById(1L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> placementService.issue(1L));
        assertEquals(404, ex.getCode());
    }

    // ---------- helpers ----------

    private MatchGroup matchGroup(Long id, Long eventId) {
        MatchGroup g = new MatchGroup();
        g.setId(id);
        g.setEventId(eventId);
        g.setGroupIndex(0);
        g.setName("A");
        return g;
    }

    private Match match(Long id, Long groupId, MatchStatus status,
                       Long slotAUser, Long slotBUser, Long slotATeam, Long slotBTeam,
                       int wonA, int wonB) {
        Match m = new Match();
        m.setId(id);
        m.setEventId(1L);
        m.setGroupId(groupId);
        m.setStatus(status);
        m.setSlotAUserId(slotAUser);
        m.setSlotBUserId(slotBUser);
        m.setSlotATeamId(slotATeam);
        m.setSlotBTeamId(slotBTeam);
        m.setGamesWonA(wonA);
        m.setGamesWonB(wonB);
        return m;
    }
}