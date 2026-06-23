package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.config.TierProperties;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserMapper userMapper;
    private final RankingMapper rankingMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TierProperties tierProperties;
    private final SeasonMapper seasonMapper;

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional
    public void refreshRankings(String type) {
        // 兼容旧签名：取当前赛季 code 委托新签名。手动刷新路径优先用 controller 解析的 code。
        refreshRankings(type, resolveCurrentSeasonCode(type));
    }

    @Override
    @Transactional
    public void refreshRankings(String type, String seasonCode) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if ("STAR".equals(type)) {
            queryWrapper.gt(User::getStarPoints, 0).orderByDesc(User::getStarPoints);
        } else {
            queryWrapper.gt(User::getPartyPoints, 0).orderByDesc(User::getPartyPoints);
        }

        List<User> users = userMapper.selectList(queryWrapper);

        // Batch load existing rankings for all users
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Ranking> existingRankingMap = batchLoadRankings(userIds, type, seasonCode);

        // Delete old rankings ONLY for this type+season (保留归档赛季)
        deleteRankingsByTypeAndSeason(type, seasonCode);

        List<Ranking> toInsert = new ArrayList<>();
        int globalRank = 1;
        for (User user : users) {
            int points = "STAR".equals(type) ? user.getStarPoints() : user.getPartyPoints();
            String tier = tierProperties.keyFor(points, type);

            Ranking existing = existingRankingMap.get(user.getId());
            int change = existing != null ? existing.getRank() - globalRank : 0;

            Ranking ranking = new Ranking();
            ranking.setUserId(user.getId());
            ranking.setType(type);
            ranking.setTier(tier);
            ranking.setRank(globalRank);
            ranking.setPoints(points);
            ranking.setChange(change);
            ranking.setSeason(seasonCode);
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
        // 档位由 TierProperties 配置驱动（6 档 + null），参数化避免硬编码。
        for (String tier : tierProperties.cacheKeysWithNull()) {
            redisTemplate.delete(RedisKey.ranking(type, tier));
        }
        redisTemplate.delete(RedisKey.rankingTop5(type));
    }

    /**
     * 仅删除当前赛季的 ranking 行，保留归档赛季数据。
     */
    protected void deleteRankingsByTypeAndSeason(String type, String seasonCode) {
        rankingMapper.delete(new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, type)
                .eq(Ranking::getSeason, seasonCode));
    }

    private String resolveCurrentSeasonCode(String type) {
        Season season = seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, type)
                .eq(Season::getStatus, "CURRENT"));
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + type);
        }
        return season.getCode();
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

        // 过滤孤儿行（user 已被软删/物理删除）
        List<RankingVO> result = rankings.stream()
                .filter(ranking -> userMap.containsKey(ranking.getUserId()))
                .map(ranking -> {
                    RankingVO vo = new RankingVO();
                    vo.setRank(ranking.getRank());
                    vo.setChange(ranking.getChange());
                    vo.setUserId(ranking.getUserId());
                    vo.setPoints(ranking.getPoints());
                    vo.setTier(ranking.getTier());
                    vo.setTierName(tierProperties.nameFor(ranking.getTier()));

                    User user = userMap.get(ranking.getUserId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatarUrl(user.getAvatarUrl());
                    vo.setCity(user.getCity());
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

        // 过滤孤儿行
        List<RankingVO> result = rankings.stream()
                .filter(ranking -> userMap.containsKey(ranking.getUserId()))
                .map(ranking -> {
                    RankingVO vo = new RankingVO();
                    vo.setRank(ranking.getRank());
                    vo.setChange(ranking.getChange());
                    vo.setUserId(ranking.getUserId());
                    vo.setPoints(ranking.getPoints());
                    vo.setTier(ranking.getTier());
                    vo.setTierName(tierProperties.nameFor(ranking.getTier()));

                    User user = userMap.get(ranking.getUserId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatarUrl(user.getAvatarUrl());
                    vo.setCity(user.getCity());
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

    private Map<Long, Ranking> batchLoadRankings(List<Long> userIds, String type, String seasonCode) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<Ranking> rankings = rankingMapper.selectList(
                new LambdaQueryWrapper<Ranking>()
                        .eq(Ranking::getType, type)
                        .eq(Ranking::getSeason, seasonCode)
                        .in(Ranking::getUserId, userIds));
        return rankings.stream().collect(Collectors.toMap(Ranking::getUserId, r -> r, (a, b) -> a));
    }
}
