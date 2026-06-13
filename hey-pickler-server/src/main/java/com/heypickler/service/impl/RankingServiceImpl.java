package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingVO;
import com.heypickler.listener.PointChangeListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final EventMapper eventMapper;
    private final PointRecordMapper pointRecordMapper;
    private final UserMapper userMapper;
    private final RankingMapper rankingMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String CURRENT_SEASON = "2026-Q2";
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional
    public void enterPoints(Long eventId, PointEntryRequest request, Long operatorId) {
        String type = "STAR";
        String eventTitle = null;
        if (eventId != null && eventId > 0) {
            Event event = eventMapper.selectById(eventId);
            if (event == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "赛事不存在");
            }
            type = event.getType();
            eventTitle = event.getTitle();
        } else if (request.getType() != null) {
            type = request.getType();
        }

        // Batch load all users needed
        List<Long> userIds = request.getRecords().stream()
                .map(PointEntryRequest.PointRecordItem::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        for (PointEntryRequest.PointRecordItem item : request.getRecords()) {
            User user = userMap.get(item.getUserId());
            if (user == null) continue;

            PointRecord record = new PointRecord();
            record.setUserId(item.getUserId());
            record.setEventId(eventId != null && eventId > 0 ? eventId : null);
            record.setType(type);
            record.setPoints(item.getPoints());
            record.setReason(eventTitle != null
                    ? "[" + eventTitle + "] " + item.getReason()
                    : item.getReason());
            record.setOperatorId(operatorId);
            pointRecordMapper.insert(record);

            int newPoints;
            if ("STAR".equals(type)) {
                int current = user.getStarPoints() != null ? user.getStarPoints() : 0;
                newPoints = Math.max(0, current + item.getPoints());
                user.setStarPoints(newPoints);
                user.setStarTier(calculateTier(newPoints, type));
            } else {
                int current = user.getPartyPoints() != null ? user.getPartyPoints() : 0;
                newPoints = Math.max(0, current + item.getPoints());
                user.setPartyPoints(newPoints);
                user.setPartyTier(calculateTier(newPoints, type));
            }
            userMapper.updateById(user);
        }

        eventPublisher.publishEvent(new PointChangeListener.PointChangeEvent(type));
    }

    private String calculateTier(int points, String type) {
        if ("STAR".equals(type)) {
            if (points >= 1000) return "LEGEND";
            if (points >= 500) return "SUPER";
            return "SHINING";
        } else {
            if (points >= 500) return "LEGEND";
            if (points >= 200) return "SUPER";
            return "SHINING";
        }
    }

    @Override
    @Transactional
    public void refreshRankings(String type) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if ("STAR".equals(type)) {
            queryWrapper.gt(User::getStarPoints, 0).orderByDesc(User::getStarPoints);
        } else {
            queryWrapper.gt(User::getPartyPoints, 0).orderByDesc(User::getPartyPoints);
        }

        List<User> users = userMapper.selectList(queryWrapper);

        // Batch load existing rankings for all users
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Ranking> existingRankingMap = batchLoadRankings(userIds, type);

        // Delete old rankings for this type+season
        rankingMapper.delete(new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, type));

        List<Ranking> toInsert = new ArrayList<>();
        int globalRank = 1;
        for (User user : users) {
            int points = "STAR".equals(type) ? user.getStarPoints() : user.getPartyPoints();
            String tier = calculateTier(points, type);

            Ranking existing = existingRankingMap.get(user.getId());
            int change = existing != null ? existing.getRank() - globalRank : 0;

            Ranking ranking = new Ranking();
            ranking.setUserId(user.getId());
            ranking.setType(type);
            ranking.setTier(tier);
            ranking.setRank(globalRank);
            ranking.setPoints(points);
            ranking.setChange(change);
            ranking.setSeason(CURRENT_SEASON);
            toInsert.add(ranking);
            globalRank++;
        }

        // Batch insert
        for (Ranking ranking : toInsert) {
            rankingMapper.insert(ranking);
        }

        // Clear caches after DB is updated.
        // NOTE: getRankings 缓存 key 为 ranking:{type}:{tier}，tier=null 时 key 形如
        // "ranking:STAR:null"，必须一并清理，否则不指定 tier 的查询会读到旧数据。
        for (String tier : Arrays.asList("LEGEND", "SUPER", "SHINING", null)) {
            redisTemplate.delete(RedisKey.ranking(type, tier));
        }
        redisTemplate.delete(RedisKey.rankingTop5(type));
    }

    @Override
    public PageResult<RankingVO> getRankings(RankingQuery query) {
        // Clamp page size
        int size = Math.min(query.getSize(), MAX_PAGE_SIZE);
        int page = Math.max(query.getPage(), 1);

        String cacheKey = RedisKey.ranking(query.getType(), query.getTier());

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            int start = (page - 1) * size;
            int end = Math.min(start + size, cached.size());
            if (start >= cached.size()) {
                return PageResult.of(cached.size(), page, size, Collections.emptyList());
            }
            return PageResult.of(cached.size(), page, size, cached.subList(start, end));
        }

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, query.getType())
                .eq(query.getTier() != null, Ranking::getTier, query.getTier())
                .orderByDesc(Ranking::getPoints);

        List<Ranking> rankings = rankingMapper.selectList(queryWrapper);

        // Batch load users to fix N+1
        List<Long> userIds = rankings.stream().map(Ranking::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        List<RankingVO> result = rankings.stream().map(ranking -> {
            RankingVO vo = new RankingVO();
            vo.setRank(ranking.getRank());
            vo.setChange(ranking.getChange());
            vo.setUserId(ranking.getUserId());
            vo.setPoints(ranking.getPoints());
            vo.setTier(ranking.getTier());

            User user = userMap.get(ranking.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }
            return vo;
        }).collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);

        int start = (page - 1) * size;
        int end = Math.min(start + size, result.size());
        if (start >= result.size()) {
            return PageResult.of(result.size(), page, size, Collections.emptyList());
        }
        return PageResult.of(result.size(), page, size, result.subList(start, end));
    }

    @Override
    public List<RankingVO> getTop5(String type) {
        String cacheKey = RedisKey.rankingTop5(type);

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, type)
                .orderByAsc(Ranking::getRank)
                .last("LIMIT 5");

        List<Ranking> rankings = rankingMapper.selectList(queryWrapper);

        // Batch load users
        List<Long> userIds = rankings.stream().map(Ranking::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        List<RankingVO> result = rankings.stream().map(ranking -> {
            RankingVO vo = new RankingVO();
            vo.setRank(ranking.getRank());
            vo.setChange(ranking.getChange());
            vo.setUserId(ranking.getUserId());
            vo.setPoints(ranking.getPoints());
            vo.setTier(ranking.getTier());

            User user = userMap.get(ranking.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }
            return vo;
        }).collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    private Map<Long, User> batchLoadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, Ranking> batchLoadRankings(List<Long> userIds, String type) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<Ranking> rankings = rankingMapper.selectList(
                new LambdaQueryWrapper<Ranking>()
                        .eq(Ranking::getType, type)
                        .in(Ranking::getUserId, userIds));
        return rankings.stream().collect(Collectors.toMap(Ranking::getUserId, r -> r, (a, b) -> a));
    }
}
