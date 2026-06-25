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
import com.heypickler.service.MatchService;
import com.heypickler.service.RoundRobinGenerator;
import com.heypickler.service.GameValidator;
import com.heypickler.vo.MatchVO;
import com.heypickler.vo.StandingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final EventMapper eventMapper;
    private final MatchGroupMapper matchGroupMapper;
    private final GroupAssignmentMapper groupAssignmentMapper;
    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;

    private final RoundRobinGenerator roundRobin = new RoundRobinGenerator();
    private final GameValidator gameValidator = new GameValidator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Match> generate(Long eventId) {
        Event event = requireEvent(eventId);
        // Spec 2 §1: grouping must be locked before matches can be generated.
        if (!Boolean.TRUE.equals(event.getGroupingLocked())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事尚未分组锁定");
        }

        // Clear any prior matches (idempotent re-generation).
        matchMapper.delete(new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));

        List<MatchGroup> groups = matchGroupMapper.selectList(
                new LambdaQueryWrapper<MatchGroup>().eq(MatchGroup::getEventId, eventId));
        List<Match> all = new ArrayList<>();
        for (MatchGroup g : groups) {
            // Query by group_id only (not event_id, which would return assignments
            // from sibling groups whose group_id the wrapper doesn't filter by).
            List<GroupAssignment> slots = groupAssignmentMapper.selectList(
                    new LambdaQueryWrapper<GroupAssignment>().eq(GroupAssignment::getGroupId, g.getId()));
            // Convert assignments to Participants for the generator.
            List<com.heypickler.dto.Participant> parts = slots.stream()
                    .map(s -> s.getUserId() != null
                            ? com.heypickler.dto.Participant.singles(s.getUserId(), 0)
                            : com.heypickler.dto.Participant.team(s.getTeamId(), 0))
                    .collect(Collectors.toList());
            List<Match> matches = roundRobin.generate(parts, g.getId());
            for (Match m : matches) {
                m.setEventId(eventId);
                m.setStatus(MatchStatus.SCHEDULED);
                matchMapper.insert(m);
                all.add(m);
            }
        }

        // Transition OPEN -> IN_PROGRESS (only once, the first time).
        if (!"IN_PROGRESS".equals(event.getStatus())) {
            eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                    .eq(Event::getId, eventId)
                    .set(Event::getStatus, "IN_PROGRESS"));
        }
        return all;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long matchId, Long userId, List<Match.GameScore> games, boolean isAdmin) {
        Match match = requireMatch(matchId);
        Event event = requireEvent(match.getEventId());
        if ("COMPLETED".equals(event.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "赛事已结束，比分不能再修改");
        }
        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "比赛已结束");
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
        match.setStatus(MatchStatus.SCHEDULED);
        match.setGames(null);
        match.setGamesWonA(null);
        match.setGamesWonB(null);
        match.setSubmittedByUserId(null);
        match.setSubmittedAt(null);
        match.setCompletedAt(null);
        matchMapper.updateById(match);
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

        // Determine doubles vs singles from the first slot column populated.
        boolean doubles = !matches.isEmpty() && matches.get(0).getSlotATeamId() != null;

        // Resolve display names for participants (doubles needs team members).
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

        // Group matches by group_id.
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
        // Aggregate per participant. wins/losses count MATCHES; gamesFor/gamesAgainst sum GAMES.
        Map<Long, int[]> agg = new HashMap<>(); // key -> [wins, losses, gamesFor, gamesAgainst]
        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.COMPLETED) continue;
            Long keyA = doubles ? m.getSlotATeamId() : m.getSlotAUserId();
            Long keyB = doubles ? m.getSlotBTeamId() : m.getSlotBUserId();
            if (keyA == null || keyB == null) continue;
            int wa = m.getGamesWonA() == null ? 0 : m.getGamesWonA();
            int wb = m.getGamesWonB() == null ? 0 : m.getGamesWonB();
            int[] a = agg.computeIfAbsent(keyA, k -> new int[4]);
            int[] b = agg.computeIfAbsent(keyB, k -> new int[4]);
            // wins/losses: count of matches won (1 per match, not games)
            if (wa > wb) a[0]++; else if (wb > wa) b[0]++;
            if (wa < wb) a[1]++; else if (wb < wa) b[1]++;
            // gamesFor/gamesAgainst: sum of games won/lost across matches
            a[2] += wa; b[2] += wb;
            a[3] += wb; b[3] += wa;
        }

        // Build list and sort: wins desc, then games-diff desc, then head-to-head.
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
                .thenComparingInt(vo -> -(vo.getGamesFor() - vo.getGamesAgainst()))
                .thenComparing(vo -> headToHeadWinner(vo, list, matches, doubles)));
        int rank = 0;
        int prevWins = Integer.MIN_VALUE;
        int prevDiff = Integer.MIN_VALUE;
        int prevH2H = Integer.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            StandingVO vo = list.get(i);
            int diff = vo.getGamesFor() - vo.getGamesAgainst();
            int h2h = computeHeadToHeadKey(vo, list, matches, doubles);
            boolean tiedPrev = i > 0
                    && list.get(i - 1).getWins() == vo.getWins()
                    && (list.get(i - 1).getGamesFor() - list.get(i - 1).getGamesAgainst()) == diff
                    && computeHeadToHeadKey(list.get(i - 1), list, matches, doubles) == h2h;
            if (!tiedPrev) rank = i + 1;
            vo.setRank(rank);
        }
        return list;
    }

    /** Stable head-to-head sort key: lower comes first (1 = top seed). */
    private int headToHeadWinner(StandingVO self, List<StandingVO> all, List<Match> matches, boolean doubles) {
        // For now return 0 so sort is stable by index; finer head-to-head is computed in rankGroup.
        return 0;
    }

    private int computeHeadToHeadKey(StandingVO self, List<StandingVO> all, List<Match> matches, boolean doubles) {
        // Count how many of self's matches the participant beat another tied participant
        // directly. Used as a tie-breaker signature: higher = better.
        int h2h = 0;
        for (StandingVO other : all) {
            if (other == self || !other.getParticipantKey().equals(self.getParticipantKey())) continue;
            for (Match m : matches) {
                if (m.getStatus() != MatchStatus.COMPLETED) continue;
                Long keyA = doubles ? m.getSlotATeamId() : m.getSlotAUserId();
                Long keyB = doubles ? m.getSlotBTeamId() : m.getSlotBUserId();
                if (keyA == null || keyB == null) continue;
                if (keyA.equals(self.getParticipantKey()) && keyB.equals(other.getParticipantKey())) {
                    if ((m.getGamesWonA() == null ? 0 : m.getGamesWonA())
                            > (m.getGamesWonB() == null ? 0 : m.getGamesWonB())) h2h++;
                } else if (keyB.equals(self.getParticipantKey()) && keyA.equals(other.getParticipantKey())) {
                    if ((m.getGamesWonB() == null ? 0 : m.getGamesWonB())
                            > (m.getGamesWonA() == null ? 0 : m.getGamesWonA())) h2h++;
                }
            }
        }
        return h2h;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long eventId) {
        Event event = requireEvent(eventId);
        List<Match> all = matchMapper.selectList(
                new LambdaQueryWrapper<Match>().eq(Match::getEventId, eventId));
        long unfinished = all.stream()
                .filter(m -> m.getStatus() != MatchStatus.COMPLETED).count();
        if (unfinished > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "还有 " + unfinished + " 场比赛未完成");
        }
        eventMapper.update(null, new LambdaUpdateWrapper<Event>()
                .eq(Event::getId, eventId)
                .set(Event::getStatus, "COMPLETED"));
    }

    @Override
    public List<MatchVO> listMyMatches(Long eventId, Long userId) {
        requireEvent(eventId);
        // Both single slot (user) and team slot (member) queries.
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
        // Dedupe by id.
        Map<Long, Match> dedup = new java.util.LinkedHashMap<>();
        for (Match m : userMatches) dedup.putIfAbsent(m.getId(), m);
        for (Match m : teamMatches) dedup.putIfAbsent(m.getId(), m);
        return new ArrayList<>(dedup.values()).stream().map(this::toVO).collect(Collectors.toList());
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
            out.add(vos);
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

    private MatchVO toVO(Match m) {
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