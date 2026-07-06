package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.common.enums.MatchStatus;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.Participant;
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
import com.heypickler.service.GameValidator;
import com.heypickler.service.MatchService;
import com.heypickler.service.NotificationService;
import com.heypickler.service.PlacementService;
import com.heypickler.service.RoundRobinGenerator;
import com.heypickler.vo.MatchVO;
import com.heypickler.vo.StandingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final EventMapper eventMapper;
    private final MatchGroupMapper matchGroupMapper;
    private final GroupAssignmentMapper groupAssignmentMapper;
    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final PlacementService placementService;
    private final NotificationService notificationService;

    private final RoundRobinGenerator roundRobin = new RoundRobinGenerator();
    private final GameValidator gameValidator = new GameValidator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Match> generate(Long eventId) {
        Event event = requireEvent(eventId);
        if (!Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事尚未分组锁定，无法生成对阵；请先在「分组」Tab 完成分组并点「锁定分组」");
        }

        // Clear any prior matches (idempotent re-generation).
        matchMapper.delete(new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));

        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>().eq(MatchGroup::getEventId, eventId));
        List<Match> all = new ArrayList<>();
        for (MatchGroup g : groups) {
            List<GroupAssignment> slots = groupAssignmentMapper.selectList(
                    new LambdaQueryWrapper<GroupAssignment>().eq(GroupAssignment::getGroupId, g.getId()));
            List<Participant> parts = slots.stream()
                    .map(s -> s.getUserId() != null
                            ? Participant.singles(s.getUserId(), 0)
                            : Participant.team(s.getTeamId(), 0))
                    .collect(Collectors.toList());
            List<Match> matches = roundRobin.generate(parts, g.getId());
            // Loop-v3 D12 — generate() for large groups scales as N*(N-1)/2 INSERTs
            // (50 users ≈ 1225, 100 users ≈ 4950). Per-row insert is the project's
            // current convention; a real batch-insert would require switching
            // MatchMapper to extend IService<Match>. Defer to loop-v4 — for now,
            // document the per-row cost and recommend generation stays below N=50.
            for (Match m : matches) {
                m.setEventId(eventId);
                m.setStatus(MatchStatus.SCHEDULED);
                matchMapper.insert(m);
                all.add(m);
            }
        }

        if (!"IN_PROGRESS".equals(event.getStatus())) {
            eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                    .eq(Event::getId, eventId)
                    .set(Event::getStatus, "IN_PROGRESS"));
            // Notify event creator that match play has begun.
            if (event.getCreatedBy() != null) {
                notificationService.push(
                        event.getCreatedBy(),
                        "EVENT_IN_PROGRESS",
                        "赛事已开赛",
                        "《" + event.getTitle() + "》已生成全部对阵，比赛进行中",
                        "/events/" + event.getId() + "?tab=match");
            }
        }
        return all;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long matchId, Long userId, List<Match.GameScore> games, boolean isAdmin) {
        Match match = requireMatch(matchId);
        requireEvent(match.getEventId());  // 404 if event is gone (sanity)
        // No event-status check: a match reset back to SCHEDULED must be re-recordable
        // even if the event is COMPLETED (fix-data path). Placement points are NOT
        // re-issued on a re-record — the operator accepts that the leaderboard will
        // lag until the next event if they re-record post-completion.
        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "该比赛已记录比分，不能重复录入；如需重新录入请先在「对阵/比赛」Tab 点「重置」");
        }
        if (!isAdmin && !isParticipant(match, userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅参赛双方可提交比分");
        }
        GameValidator.Result v;
        try {
            v = gameValidator.validate(games);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, e.getMessage());
        }

        match.setGameList(games);
        match.setGamesWonA(v.gamesWonA());
        match.setGamesWonB(v.gamesWonB());
        match.setStatus(MatchStatus.COMPLETED);
        match.setSubmittedByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        match.setSubmittedAt(now);
        match.setCompletedAt(now);
        matchMapper.updateById(match);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset(Long matchId) {
        Match match = requireMatch(matchId);
        // updateById skips null fields (FieldStrategy.NOT_NULL default), so use a
        // LambdaUpdateWrapper to force-clear every score field.
        matchMapper.update(null, new LambdaUpdateWrapper<Match>()
                .eq(Match::getId, matchId)
                .set(Match::getStatus, MatchStatus.SCHEDULED)
                .set(Match::getGames, null)
                .set(Match::getGamesWonA, null)
                .set(Match::getGamesWonB, null)
                .set(Match::getSubmittedByUserId, null)
                .set(Match::getSubmittedAt, null)
                .set(Match::getCompletedAt, null));
    }

    @Override
    public List<List<StandingVO>> standings(Long eventId) {
        requireEvent(eventId);
        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>().eq(MatchGroup::getEventId, eventId));
        List<List<StandingVO>> result = new ArrayList<>();
        if (groups.isEmpty()) {
            return result;
        }
        List<Match> matches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));

        boolean doubles = !matches.isEmpty() && matches.get(0).getSlotATeamId() != null;

        Set<Long> userIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        if (doubles) {
            for (Match m : matches) {
                if (m.getSlotATeamId() != null) teamIds.add(m.getSlotATeamId());
                if (m.getSlotBTeamId() != null) teamIds.add(m.getSlotBTeamId());
            }
            Map<Long, Team> teams = teamMapper.selectBatchIds(teamIds).stream()
                    .collect(Collectors.toMap(Team::getId, t -> t));
            for (Team t : teams.values()) {
                userIds.add(t.getMember1UserId());
                userIds.add(t.getMember2UserId());
            }
        } else {
            for (Match m : matches) {
                if (m.getSlotAUserId() != null) userIds.add(m.getSlotAUserId());
                if (m.getSlotBUserId() != null) userIds.add(m.getSlotBUserId());
            }
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Team> teamMap = doubles ? teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, t -> t)) : Collections.emptyMap();

        Map<Long, List<Match>> byGroup = matches.stream()
                .collect(Collectors.groupingBy(Match::getGroupId));

        for (MatchGroup g : groups) {
            List<Match> gMatches = byGroup.getOrDefault(g.getId(), List.of());
            result.add(rankGroup(gMatches, doubles, users, teamMap));
        }
        return result;
    }

    private List<StandingVO> rankGroup(List<Match> matches, boolean doubles,
                                       Map<Long, User> users, Map<Long, Team> teamMap) {
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
            if (wa < wb) a[1]++; else if (wb < wa) b[1]++;
            a[2] += wa; b[2] += wb;
            a[3] += wb; b[3] += wa;
        }

        List<StandingVO> list = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : agg.entrySet()) {
            StandingVO vo = new StandingVO();
            vo.setParticipantKey(e.getKey());
            vo.setWins(e.getValue()[0]);
            vo.setLosses(e.getValue()[1]);
            vo.setGamesFor(e.getValue()[2]);
            vo.setGamesAgainst(e.getValue()[3]);
            vo.setDisplayName(resolveDisplayName(e.getKey(), doubles, users, teamMap));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long eventId) {
        Event event = requireEvent(eventId);
        // Idempotent re-completion: no-op.
        if ("COMPLETED".equals(event.getStatus())) {
            return;
        }
        List<Match> all = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));
        long unfinished = all.stream()
                .filter(m -> m.getStatus() != MatchStatus.COMPLETED).count();
        if (unfinished > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "还有 " + unfinished + " 场比赛未完成；请先在「对阵/比赛」Tab 录入所有比分后再结束赛事");
        }
        // Spec 3: issue placement points atomically. If this throws (e.g., duplicate),
        // the COMPLETED status update rolls back.
        placementService.issue(eventId);
        eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                .eq(Event::getId, eventId)
                .set(Event::getStatus, "COMPLETED"));
        // Notify event creator that the event is wrapped up.
        if (event.getCreatedBy() != null) {
            notificationService.push(
                    event.getCreatedBy(),
                    "EVENT_COMPLETED",
                    "赛事已结束",
                    "《" + event.getTitle() + "》已结束，名次积分已发放",
                    "/events/" + event.getId() + "?tab=result");
        }
    }

    @Override
    public List<MatchVO> listMyMatches(Long eventId, Long userId) {
        requireEvent(eventId);
        List<Match> userMatches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>()
                        .eq(Match::getEventId, eventId)
                        .and(w -> w.eq(Match::getSlotAUserId, userId)
                                .or().eq(Match::getSlotBUserId, userId)));
        Set<Long> teamIds = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getMember1UserId, userId)
                .or().eq(Team::getMember2UserId, userId))
                .stream().map(Team::getId).collect(Collectors.toSet());
        List<Match> teamMatches = teamIds.isEmpty() ? List.of() : matchMapper.selectList(
                new LambdaQueryWrapper<Match>()
                        .eq(Match::getEventId, eventId)
                        .and(w -> w.in(Match::getSlotATeamId, teamIds)
                                .or().in(Match::getSlotBTeamId, teamIds)));
        Map<Long, Match> dedup = new java.util.LinkedHashMap<>();
        for (Match m : userMatches) dedup.putIfAbsent(m.getId(), m);
        for (Match m : teamMatches) dedup.putIfAbsent(m.getId(), m);
        return dedup.values().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<List<MatchVO>> listEventMatches(Long eventId) {
        requireEvent(eventId);
        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>().eq(MatchGroup::getEventId, eventId));
        List<Match> matches = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));
        Map<Long, List<Match>> byGroup = matches.stream()
                .collect(Collectors.groupingBy(Match::getGroupId));
        List<List<MatchVO>> out = new ArrayList<>();
        for (MatchGroup g : groups) {
            List<MatchVO> vos = byGroup.getOrDefault(g.getId(), List.of()).stream()
                    .map(this::toVO).collect(Collectors.toList());
            populateDisplayNames(vos);
            out.add(vos);
        }
        return out;
    }

    @Override
    public MatchVO toVO(Match m) {
        MatchVO vo = new MatchVO();
        vo.setId(m.getId());
        vo.setEventId(m.getEventId());
        vo.setGroupId(m.getGroupId());
        vo.setSlotAUserId(m.getSlotAUserId());
        vo.setSlotATeamId(m.getSlotATeamId());
        vo.setSlotBUserId(m.getSlotBUserId());
        vo.setSlotBTeamId(m.getSlotBTeamId());
        vo.setStatus(m.getStatus() == null ? null : m.getStatus().name());
        vo.setGames(toVOGames(m.getGameList()));
        vo.setGamesWonA(m.getGamesWonA());
        vo.setGamesWonB(m.getGamesWonB());
        vo.setSubmittedAt(m.getSubmittedAt());
        vo.setCompletedAt(m.getCompletedAt());
        return vo;
    }

    /**
     * Batch-resolve slotADisplayName / slotBDisplayName for the given match VOs.
     *
     * <p>SINGLES: slotAUserId / slotBUserId → user.nickname.
     * DOUBLES/MIXED: slotATeamId / slotBTeamId → "m1Nick / m2Nick" via teamMember join.
     */
    private void populateDisplayNames(List<MatchVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        Set<Long> userIds = new HashSet<>();
        Set<Long> teamIds = new HashSet<>();
        for (MatchVO vo : vos) {
            if (vo.getSlotAUserId() != null) userIds.add(vo.getSlotAUserId());
            if (vo.getSlotBUserId() != null) userIds.add(vo.getSlotBUserId());
            if (vo.getSlotATeamId() != null) teamIds.add(vo.getSlotATeamId());
            if (vo.getSlotBTeamId() != null) teamIds.add(vo.getSlotBTeamId());
        }
        // SINGLES path: direct user lookup.
        Map<Long, String> userNicknames = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .filter(u -> u.getId() != null && u.getNickname() != null)
                        .collect(Collectors.toMap(u -> u.getId(), u -> u.getNickname(), (a, b) -> a));
        // DOUBLES/MIXED path: build "m1Nick / m2Nick" via Team.member1UserId + member2UserId.
        Map<Long, String> teamDisplayNames = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (Team team : teamMapper.selectBatchIds(teamIds)) {
                String m1 = userNicknames.get(team.getMember1UserId());
                String m2 = userNicknames.get(team.getMember2UserId());
                String joined = Stream.of(m1, m2)
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(" / "));
                if (!joined.isEmpty()) teamDisplayNames.put(team.getId(), joined);
            }
        }
        for (MatchVO vo : vos) {
            if (vo.getSlotAUserId() != null) {
                vo.setSlotADisplayName(userNicknames.get(vo.getSlotAUserId()));
            } else if (vo.getSlotATeamId() != null) {
                vo.setSlotADisplayName(teamDisplayNames.get(vo.getSlotATeamId()));
            }
            if (vo.getSlotBUserId() != null) {
                vo.setSlotBDisplayName(userNicknames.get(vo.getSlotBUserId()));
            } else if (vo.getSlotBTeamId() != null) {
                vo.setSlotBDisplayName(teamDisplayNames.get(vo.getSlotBTeamId()));
            }
        }
    }

    private List<MatchVO.GameScore> toVOGames(List<Match.GameScore> source) {
        if (source == null) return null;
        List<MatchVO.GameScore> out = new ArrayList<>(source.size());
        for (Match.GameScore g : source) {
            MatchVO.GameScore v = new MatchVO.GameScore();
            v.setGame(g.getGame());
            v.setA(g.getA());
            v.setB(g.getB());
            out.add(v);
        }
        return out;
    }

    // ---------- helpers ----------

    private Match requireMatch(Long matchId) {
        Match m = matchMapper.selectById(matchId);
        if (m == null) throw new BizException(ErrorCode.NOT_FOUND, "比赛不存在");
        return m;
    }

    private Event requireEvent(Long eventId) {
        Event e = eventMapper.selectById(eventId);
        if (e == null || e.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
        }
        return e;
    }

    private boolean isParticipant(Match m, Long userId) {
        if (m.getSlotAUserId() != null && m.getSlotAUserId().equals(userId)) return true;
        if (m.getSlotBUserId() != null && m.getSlotBUserId().equals(userId)) return true;
        if (m.getSlotATeamId() != null && isTeamMember(m.getSlotATeamId(), userId)) return true;
        if (m.getSlotBTeamId() != null && isTeamMember(m.getSlotBTeamId(), userId)) return true;
        return false;
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        Team t = teamMapper.selectById(teamId);
        if (t == null) return false;
        return userId.equals(t.getMember1UserId()) || userId.equals(t.getMember2UserId());
    }

    private String resolveDisplayName(Long key, boolean doubles, Map<Long, User> users, Map<Long, Team> teamMap) {
        if (doubles) {
            Team t = teamMap.get(key);
            if (t == null) return null;
            User m1 = users.get(t.getMember1UserId());
            User m2 = users.get(t.getMember2UserId());
            return n(m1) + " / " + n(m2);
        }
        return n(users.get(key));
    }

    private String n(User u) {
        return u == null ? "?" : u.getNickname();
    }
}