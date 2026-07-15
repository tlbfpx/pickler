package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.service.TierResolver;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.RankingService;
import com.heypickler.vo.RankingPageVO;
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
    private final TierResolver tierResolver;
    private final SeasonMapper seasonMapper;

    private static final int MAX_PAGE_SIZE = 100;
    /** tier_code 固定顺序，构建 tierColorMap 时按此遍历保证图例顺序稳定 */
    private static final List<String> TIER_CODE_ORDER = Arrays.asList(
            "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER");

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
            String tier = tierResolver.keyFor(points, type);

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
        // NOTE: getRankings 缓存 key 为 ranking:{type}:{tier}:{seasonCode}，tier=null 时 key 形如
        // "ranking:STAR:null:2026-Q2"，必须一并清理，否则不指定 tier 的查询会读到旧数据。
        // 档位由 TierResolver 驱动（tier_config 表 V19，6 档 + null），参数化避免硬编码。
        for (String tier : tierResolver.cacheKeysWithNull()) {
            redisTemplate.delete(RedisKey.ranking(type, tier, seasonCode));
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

    @Override
    public PageResult<RankingVO> getRankings(RankingQuery query) {
        // Clamp page size
        int size = Math.min(query.getSize(), MAX_PAGE_SIZE);
        int page = Math.max(query.getPage(), 1);

        // 必须按当前赛季过滤：赛季轮转后 ranking 表同时存在当前+归档赛季行
        // (uk_user_type_season 允许同用户跨赛季各一行)，不过滤会导致用户重复出现。
        Season current = resolveCurrentSeason(query.getType());
        if (current == null) {
            return PageResult.of(0, page, size, Collections.emptyList());
        }
        String seasonCode = current.getCode();

        String cacheKey = RedisKey.ranking(query.getType(), query.getTier(), seasonCode);

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            // 缓存存全量，命中时同样需按 keyword 过滤——否则搜索在热缓存下静默失效。不回写。
            return filterAndPaginate(cached, query.getKeyword(), page, size);
        }

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, query.getType())
                .eq(Ranking::getSeason, seasonCode)
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
                    vo.setTierName(tierResolver.nameFor(ranking.getType(), ranking.getTier()));
                    vo.setTierColor(tierResolver.colorFor(ranking.getType(), ranking.getTier()));
                    vo.setTierIcon(tierResolver.iconFor(ranking.getType(), ranking.getTier()));

                    User user = userMap.get(ranking.getUserId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatarUrl(user.getAvatarUrl());
                    vo.setCity(user.getCity());
                    return vo;
                }).collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);

        return filterAndPaginate(result, query.getKeyword(), page, size);
    }

    @Override
    public RankingPageVO getRankingPage(RankingQuery query) {
        Season current = resolveCurrentSeason(query.getType());
        if (current == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + query.getType());
        }
        PageResult<RankingVO> page = getRankings(query);
        RankingPageVO vo = new RankingPageVO();
        vo.setPage(page);
        vo.setTierDistribution(countTierDistribution(query.getType(), current.getCode()));
        vo.setTierColorMap(buildTierColorMap(query.getType()));
        vo.setTierNameMap(buildTierNameMap(query.getType()));
        vo.setTierIconMap(buildTierIconMap(query.getType()));
        vo.setSeasonCode(current.getCode());
        vo.setSeasonName(current.getName());
        vo.setSeasonStatus(current.getStatus());
        return vo;
    }

    /** 段位分布：仅含有行的段位，前端对缺失段位补 0。 */
    private Map<String, Integer> countTierDistribution(String type, String seasonCode) {
        List<Map<String, Object>> rows = rankingMapper.countByTier(type, seasonCode);
        Map<String, Integer> dist = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object tier = row.get("tier");
            Object cnt = row.get("cnt");
            if (tier != null && cnt instanceof Number) {
                dist.put(tier.toString(), ((Number) cnt).intValue());
            }
        }
        return dist;
    }

    /** 当前 track 全 6 档 tier_code→color，供前端图例/徽章染色。tier_code 顺序固定 BRONZE..MASTER。 */
    private Map<String, String> buildTierColorMap(String type) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) {
            map.put(code, tierResolver.colorFor(type, code));
        }
        return map;
    }

    @Override
    public Map<String, String> tierNameMap(String type) {
        return buildTierNameMap(type);
    }

    /** 当前 track 全 6 档 tier_code→name（per-track，避免前端 TIER_NAME 单套 fallback 让 PARTY 轨显示青铜..王者）。 */
    private Map<String, String> buildTierNameMap(String type) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) {
            map.put(code, tierResolver.nameFor(type, code));
        }
        return map;
    }

    /** 当前 track 全 6 档 tier_code→icon（per-track，供前端图例/徽章渲染图标）。 */
    private Map<String, String> buildTierIconMap(String type) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String code : TIER_CODE_ORDER) {
            map.put(code, tierResolver.iconFor(type, code));
        }
        return map;
    }

    @Override
    public List<RankingVO> getTop5(String type) {
        Season current = resolveCurrentSeason(type);
        if (current == null) return Collections.emptyList();

        String cacheKey = RedisKey.rankingTop5(type);

        List<RankingVO> cached = (List<RankingVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        LambdaQueryWrapper<Ranking> queryWrapper = new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, type)
                .eq(Ranking::getSeason, current.getCode())
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
                    vo.setTierName(tierResolver.nameFor(ranking.getType(), ranking.getTier()));
                    vo.setTierColor(tierResolver.colorFor(ranking.getType(), ranking.getTier()));
                    vo.setTierIcon(tierResolver.iconFor(ranking.getType(), ranking.getTier()));

                    User user = userMap.get(ranking.getUserId());
                    vo.setNickname(user.getNickname());
                    vo.setAvatarUrl(user.getAvatarUrl());
                    vo.setCity(user.getCity());
                    return vo;
                }).collect(Collectors.toList());

        redisTemplate.opsForValue().set(cacheKey, result, 5, TimeUnit.MINUTES);
        return result;
    }

    /** 按 keyword 在已含 nickname 的列表上内存过滤，再分页。keyword 为空时原样分页。 */
    private PageResult<RankingVO> filterAndPaginate(List<RankingVO> source, String keyword, int page, int size) {
        List<RankingVO> filtered = source;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lower = keyword.trim().toLowerCase();
            filtered = source.stream()
                .filter(vo -> vo.getNickname() != null && vo.getNickname().toLowerCase().contains(lower))
                .collect(Collectors.toList());
        }
        int start = (page - 1) * size;
        int end = Math.min(start + size, filtered.size());
        List<RankingVO> pageList = start >= filtered.size()
            ? Collections.emptyList() : filtered.subList(start, end);
        return PageResult.of(filtered.size(), page, size, pageList);
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

    /** 解析当前赛季（可能为 null，调用方决定如何处理）。 */
    private Season resolveCurrentSeason(String type) {
        return seasonMapper.selectOne(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, type)
                .eq(Season::getStatus, "CURRENT"));
    }

    private String resolveCurrentSeasonCode(String type) {
        Season season = resolveCurrentSeason(type);
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前赛季不存在: " + type);
        }
        return season.getCode();
    }
}
