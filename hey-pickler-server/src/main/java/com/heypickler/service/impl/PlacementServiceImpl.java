package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.EventPlacementPointsMapper;
import com.heypickler.mapper.GroupAssignmentMapper;
import com.heypickler.mapper.MatchGroupMapper;
import com.heypickler.mapper.MatchMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.TeamMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.PlacementService;
import com.heypickler.service.PointService;
import com.heypickler.vo.PlacementDetailVO;
import com.heypickler.vo.PlacementPointsVO;
import com.heypickler.vo.StandingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Placement issuance (Spec 3). On event COMPLETED, read final standings + the
 * placement-points table and write one point_record row per participant with
 * source=PLACEMENT.
 *
 * <p>Standings are computed locally to avoid a circular dependency with
 * MatchService (complete() -> PlacementService.issue() -> standings()).
 */
@Service
@RequiredArgsConstructor
public class PlacementServiceImpl implements PlacementService {

    private final EventMapper eventMapper;
    private final EventPlacementPointsMapper pointsMapper;
    private final PointRecordMapper pointRecordMapper;
    private final MatchGroupMapper matchGroupMapper;
    private final GroupAssignmentMapper groupAssignmentMapper;
    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final PointService pointService;
    private final PlacementProperties defaultProps;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long eventId) {
        Event event = requireEvent(eventId);

        // Idempotency guard: refuse re-issue.
        Long existing = pointRecordMapper.selectCount(
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getEventId, eventId)
                        .eq(PointRecord::getSource, "PLACEMENT"));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "赛事已完成发分，请勿重复触发");
        }

        Map<Integer, Integer> table = resolveTable(eventId);

        List<List<StandingVO>> byGroup = computeStandings(eventId);
        // doubles 判定基于 match 的 team 槽位（与 computeStandings 一致），不能用 standings
        // 的 participantKey 查 team：singles 的 participantKey 是 userId，而 User/Team 都是
        // IdType.AUTO 自增、id 空间重叠，userId 撞上 team.id 会误判 doubles，把分发给错误的
        // team 成员、选手自己丢分（review #4 P1）。
        List<Match> sampleMatches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));
        boolean doubles = !sampleMatches.isEmpty() && sampleMatches.get(0).getSlotATeamId() != null;

        // 全局排名：跨组按真实 tie-break（wins desc → 净胜局 desc）统一排序 + 竞技式平分共享，
        // 修多组赛事点数取决于 HashMap 迭代序的随机 bug（原按组内 rank 拼接，两组冠军 rank=1 先后序随机）。
        List<StandingVO> ranked = assignGlobalRanks(byGroup);

        String title = event.getTitle() == null ? "" : event.getTitle();
        for (StandingVO s : ranked) {
            int globalRank = s.getRank() == null ? Integer.MAX_VALUE : s.getRank();
            int points = table.getOrDefault(globalRank, 0);
            String reason = "PLACEMENT: 赛事《" + title + "》第" + globalRank + "名";
            if (doubles && s.getParticipantKey() != null) {
                // Doubles: split points between team members.
                Team team = teamMapper.selectById(s.getParticipantKey());
                if (team != null) {
                    int half = points / 2;
                    int otherHalf = points - half;
                    pointService.issuePlacement(eventId, team.getMember1UserId(), half, reason);
                    pointService.issuePlacement(eventId, team.getMember2UserId(), otherHalf, reason);
                }
            } else if (s.getParticipantKey() != null) {
                pointService.issuePlacement(eventId, s.getParticipantKey(), points, reason);
            }
        }
    }

    /**
     * 跨组全局排名：合并所有组 standings，按 wins desc → (gamesFor - gamesAgainst) desc 排序，
     * 竞技式平分（同记录同名次，跳过后续名次）。返回的 {@link StandingVO#getRank()} 为全局名次，
     * 供 placement 点数表查询。纯函数（无 DB）→ 可单测。
     */
    static List<StandingVO> assignGlobalRanks(List<List<StandingVO>> byGroup) {
        List<StandingVO> flat = new ArrayList<>();
        for (List<StandingVO> group : byGroup) {
            flat.addAll(group);
        }
        flat.sort(Comparator.<StandingVO>comparingInt(PlacementServiceImpl::wins).reversed()
                .thenComparingInt(s -> -gamesDiff(s)));
        int rank = 0;
        for (int i = 0; i < flat.size(); i++) {
            boolean tiedPrev = i > 0
                    && wins(flat.get(i - 1)) == wins(flat.get(i))
                    && gamesDiff(flat.get(i - 1)) == gamesDiff(flat.get(i));
            if (!tiedPrev) {
                rank = i + 1;
            }
            flat.get(i).setRank(rank);
        }
        return flat;
    }

    private static int wins(StandingVO s) {
        return s.getWins() == null ? 0 : s.getWins();
    }

    private static int gamesDiff(StandingVO s) {
        int gf = s.getGamesFor() == null ? 0 : s.getGamesFor();
        int ga = s.getGamesAgainst() == null ? 0 : s.getGamesAgainst();
        return gf - ga;
    }

    @Override
    public PlacementPointsVO getPoints(Long eventId) {
        requireEvent(eventId);
        EventPlacementPoints row = pointsMapper.selectById(eventId);
        if (row != null) {
            PlacementPointsVO vo = new PlacementPointsVO();
            vo.setPoints(row.getPointsMap());
            vo.setSource("custom");
            return vo;
        }
        PlacementPointsVO vo = new PlacementPointsVO();
        vo.setPoints(new HashMap<>(defaultProps.getDefaultPoints()));
        vo.setSource("default");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPoints(Long eventId, EventPlacementPoints override) {
        Event event = requireEvent(eventId);
        if ("COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "赛事已完成，不可修改积分表");
        }
        if (override.getPointsMap() == null || override.getPointsMap().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "积分表不能为空");
        }
        for (Map.Entry<Integer, Integer> e : override.getPointsMap().entrySet()) {
            if (e.getValue() == null || e.getValue() < 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "名次 " + e.getKey() + " 积分必须 >= 0");
            }
        }
        // delete-then-insert so re-PUT on the same event updates instead of
        // colliding on the event_id primary key.
        pointsMapper.delete(
                new LambdaQueryWrapper<EventPlacementPoints>()
                        .eq(EventPlacementPoints::getEventId, eventId));
        EventPlacementPoints row = new EventPlacementPoints();
        row.setEventId(eventId);
        row.setPointsMap(override.getPointsMap());
        pointsMapper.insert(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearPoints(Long eventId) {
        Event event = requireEvent(eventId);
        if ("COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "赛事已完成，不可修改积分表");
        }
        pointsMapper.delete(
                new LambdaQueryWrapper<EventPlacementPoints>()
                        .eq(EventPlacementPoints::getEventId, eventId));
    }

    @Override
    public List<PlacementDetailVO> listByEventId(Long eventId) {
        requireEvent(eventId);
        List<PointRecord> records = pointRecordMapper.selectList(
                new LambdaQueryWrapper<PointRecord>()
                        .eq(PointRecord::getEventId, eventId)
                        .eq(PointRecord::getSource, "PLACEMENT")
                        .orderByDesc(PointRecord::getPoints)
                        .orderByAsc(PointRecord::getId));
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        for (PointRecord r : records) {
            if (r.getUserId() != null) userIds.add(r.getUserId());
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<PlacementDetailVO> out = new ArrayList<>(records.size());
        int rank = 1;
        for (PointRecord r : records) {
            PlacementDetailVO vo = new PlacementDetailVO();
            vo.setRank(rank++);
            vo.setUserId(r.getUserId());
            User u = r.getUserId() == null ? null : users.get(r.getUserId());
            vo.setNickname(u == null ? null : u.getNickname());
            vo.setPoints(r.getPoints());
            vo.setReason(r.getReason());
            vo.setCreatedAt(r.getCreatedAt());
            out.add(vo);
        }
        return out;
    }

    private Map<Integer, Integer> resolveTable(Long eventId) {
        EventPlacementPoints row = pointsMapper.selectById(eventId);
        if (row != null) return row.getPointsMap();
        return new HashMap<>(defaultProps.getDefaultPoints());
    }

    /**
     * Local copy of MatchService.standings aggregation. Reads matches + groups
     * directly to avoid the circular bean dependency. Behavior:
     * wins desc -> game-diff desc -> ties share rank.
     */
    private List<List<StandingVO>> computeStandings(Long eventId) {
        List<Match> matches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));
        if (matches.isEmpty()) return Collections.emptyList();

        boolean doubles = matches.get(0).getSlotATeamId() != null;
        Set<Long> userIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        if (doubles) {
            for (Match m : matches) {
                if (m.getSlotATeamId() != null) teamIds.add(m.getSlotATeamId());
                if (m.getSlotBTeamId() != null) teamIds.add(m.getSlotBTeamId());
            }
            List<Team> teams = teamMapper.selectBatchIds(teamIds);
            for (Team t : teams) {
                userIds.add(t.getMember1UserId());
                userIds.add(t.getMember2UserId());
            }
        } else {
            for (Match m : matches) {
                if (m.getSlotAUserId() != null) userIds.add(m.getSlotAUserId());
                if (m.getSlotBUserId() != null) userIds.add(m.getSlotBUserId());
            }
        }
        // Names are not required for placement (only participantKey is used).

        Map<Long, List<Match>> byGroup = new HashMap<>();
        for (Match m : matches) byGroup.computeIfAbsent(m.getGroupId(), k -> new ArrayList<>()).add(m);

        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>().eq(MatchGroup::getEventId, eventId));
        if (groups.isEmpty()) {
            // Fallback: no explicit MatchGroup rows yet — synthesize one bucket
            // per observed groupId so placement can still rank from the matches
            // we have (e.g., test fixtures that skip group creation).
            List<List<StandingVO>> fallback = new ArrayList<>();
            for (List<Match> gMatches : byGroup.values()) {
                fallback.add(rankGroup(gMatches, doubles));
            }
            return fallback;
        }

        List<List<StandingVO>> result = new ArrayList<>();
        for (MatchGroup g : groups) {
            List<Match> gMatches = byGroup.getOrDefault(g.getId(), Collections.emptyList());
            result.add(rankGroup(gMatches, doubles));
        }
        return result;
    }

    private List<StandingVO> rankGroup(List<Match> matches, boolean doubles) {
        Map<Long, int[]> agg = new HashMap<>();
        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.COMPLETED) continue;
            Long keyA = doubles ? m.getSlotATeamId() : m.getSlotAUserId();
            Long keyB = doubles ? m.getSlotBTeamId() : m.getSlotBUserId();
            if (keyA == null || keyB == null) continue;
            int wa = m.getGamesWonA() == null ? 0 : m.getGamesWonA();
            int wb = m.getGamesWonB() == null ? 0 : m.getGamesWonB();
            int[] a = agg.computeIfAbsent(keyA, k -> new int[4]);
            int[] b = agg.computeIfAbsent(keyB, k -> new int[4]);
            if (wa > wb) a[0]++; else if (wb > wa) b[0]++;
            a[2] += wa; b[2] += wb;
            a[3] += wb; b[3] += wa;
        }
        List<StandingVO> list = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : agg.entrySet()) {
            StandingVO vo = new StandingVO();
            vo.setParticipantKey(e.getKey());
            vo.setWins(e.getValue()[0]);
            vo.setGamesFor(e.getValue()[2]);
            vo.setGamesAgainst(e.getValue()[3]);
            list.add(vo);
        }
        list.sort(Comparator.<StandingVO>comparingInt(StandingVO::getWins).reversed()
                .thenComparingInt(vo -> -(vo.getGamesFor() - vo.getGamesAgainst())));
        int rank = 0;
        for (int i = 0; i < list.size(); i++) {
            boolean tiedPrev = i > 0
                    && list.get(i - 1).getWins() == list.get(i).getWins()
                    && (list.get(i - 1).getGamesFor() - list.get(i - 1).getGamesAgainst())
                       == (list.get(i).getGamesFor() - list.get(i).getGamesAgainst());
            if (!tiedPrev) rank = i + 1;
            list.get(i).setRank(rank);
        }
        return list;
    }

    private Event requireEvent(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null || event.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
        }
        return event;
    }
}