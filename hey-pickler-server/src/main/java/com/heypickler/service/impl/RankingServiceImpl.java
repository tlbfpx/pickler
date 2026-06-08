package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.constant.RedisKey;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

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

    @Override
    @Transactional
    public void enterPoints(Long eventId, PointEntryRequest request, Long operatorId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        String type = event.getType();

        for (PointEntryRequest.PointRecordItem item : request.getRecords()) {
            User user = userMapper.selectById(item.getUserId());
            if (user == null) {
                continue;
            }

            PointRecord record = new PointRecord();
            record.setUserId(item.getUserId());
            record.setEventId(eventId);
            record.setType(type);
            record.setPoints(item.getPoints());
            record.setReason(item.getReason());
            record.setOperatorId(operatorId);
            pointRecordMapper.insert(record);

            int currentPoints;
            if ("STAR".equals(type)) {
                currentPoints = user.getStarPoints() != null ? user.getStarPoints() : 0;
                int newPoints = Math.max(0, currentPoints + item.getPoints());
                user.setStarPoints(newPoints);
                user.setStarTier(calculateTier(newPoints, type));
            } else {
                currentPoints = user.getPartyPoints() != null ? user.getPartyPoints() : 0;
                int newPoints = Math.max(0, currentPoints + item.getPoints());
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
        String season = "S1";

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if ("STAR".equals(type)) {
            queryWrapper.gt(User::getStarPoints, 0)
                       .orderByDesc(User::getStarPoints);
        } else {
            queryWrapper.gt(User::getPartyPoints, 0)
                       .orderByDesc(User::getPartyPoints);
        }

        List<User> users = userMapper.selectList(queryWrapper);

        Map<String, List<User>> tierGroups = users.stream()
            .collect(Collectors.groupingBy(u -> {
                int points = "STAR".equals(type) ? u.getStarPoints() : u.getPartyPoints();
                return calculateTier(points, type);
            }));

        for (Map.Entry<String, List<User>> entry : tierGroups.entrySet()) {
            String tier = entry.getKey();
            List<User> tierUsers = entry.getValue();
            int rank = 1;

            for (User user : tierUsers) {
                int points = "STAR".equals(type) ? user.getStarPoints() : user.getPartyPoints();

                LambdaQueryWrapper<Ranking> rankingQuery = new LambdaQueryWrapper<Ranking>()
                    .eq(Ranking::getUserId, user.getId())
                    .eq(Ranking::getType, type)
                    .eq(Ranking::getSeason, season);

                Ranking existing = rankingMapper.selectOne(rankingQuery);

                int change = 0;
                if (existing != null) {
                    change = existing.getRank() - rank;
                }

                Ranking ranking = new Ranking();
                ranking.setUserId(user.getId());
                ranking.setType(type);
                ranking.setTier(tier);
                ranking.setRank(rank);
                ranking.setPoints(points);
                ranking.setChange(change);
                ranking.setSeason(season);

                if (existing != null) {
                    ranking.setId(existing.getId());
                    rankingMapper.updateById(ranking);
                } else {
                    rankingMapper.insert(ranking);
                }
                rank++;
            }
        }

        for (String tier : Arrays.asList("LEGEND", "SUPER", "SHINING")) {
            redisTemplate.delete(RedisKey.ranking(type, tier));
        }
        redisTemplate.delete(RedisKey.rankingTop5(type));
    }

    @Override
    public PageResult<RankingVO> getRankings(RankingQuery query) {
        String cacheKey = RedisKey.ranking(query.getType(), query.getTier());

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            int start = (query.getPage() - 1) * query.getSize();
            int end = Math.min(start + query.getSize(), cached.size());
            List<RankingVO> pageData = cached.subList(start, end);
            return PageResult.of(cached.size(), query.getPage(), query.getSize(), pageData);
        }

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
            .eq(Ranking::getType, query.getType())
            .eq(Ranking::getTier, query.getTier())
            .eq(Ranking::getSeason, "S1")
            .orderByAsc(Ranking::getRank);

        List<Ranking> rankings = rankingMapper.selectList(queryWrapper);

        List<RankingVO> result = rankings.stream().map(ranking -> {
            RankingVO vo = new RankingVO();
            vo.setRank(ranking.getRank());
            vo.setChange(ranking.getChange());
            vo.setUserId(ranking.getUserId());
            vo.setPoints(ranking.getPoints());
            vo.setTier(ranking.getTier());

            User user = userMapper.selectById(ranking.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
                vo.setCity(user.getCity());
            }

            return vo;
        }).collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);

        int start = (query.getPage() - 1) * query.getSize();
        int end = Math.min(start + query.getSize(), result.size());
        List<RankingVO> pageData = result.subList(start, end);
        return PageResult.of(result.size(), query.getPage(), query.getSize(), pageData);
    }

    @Override
    public List<RankingVO> getTop5(String type) {
        String cacheKey = RedisKey.rankingTop5(type);

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
            .eq(Ranking::getType, type)
            .eq(Ranking::getSeason, "S1")
            .orderByAsc(Ranking::getRank)
            .last("LIMIT 5");

        List<Ranking> rankings = rankingMapper.selectList(queryWrapper);

        List<RankingVO> result = rankings.stream().map(ranking -> {
            RankingVO vo = new RankingVO();
            vo.setRank(ranking.getRank());
            vo.setChange(ranking.getChange());
            vo.setUserId(ranking.getUserId());
            vo.setPoints(ranking.getPoints());
            vo.setTier(ranking.getTier());

            User user = userMapper.selectById(ranking.getUserId());
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
}
