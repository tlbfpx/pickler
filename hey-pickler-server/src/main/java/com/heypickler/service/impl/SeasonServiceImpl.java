package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.service.TierResolver;
import com.heypickler.dto.admin.SeasonCreateRequest;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.RankingService;
import com.heypickler.service.SeasonService;
import com.heypickler.vo.RankingPageVO;
import com.heypickler.vo.RankingVO;
import com.heypickler.vo.SeasonVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonServiceImpl implements SeasonService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SeasonMapper seasonMapper;
    private final RankingMapper rankingMapper;
    private final UserMapper userMapper;
    private final TierResolver tierResolver;
    private final RankingService rankingService;

    @Override
    public List<SeasonVO> list(String type) {
        LambdaQueryWrapper<Season> wrapper = new LambdaQueryWrapper<Season>()
                .eq(type != null, Season::getType, type)
                .orderByDesc(Season::getCreatedAt);
        return seasonMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public SeasonVO create(SeasonCreateRequest req) {
        Season season = new Season();
        season.setType(req.getType());
        season.setCode(req.getCode());
        season.setName(req.getName());
        season.setStartDate(req.getStartDate());
        season.setEndDate(req.getEndDate());
        // 新建赛季默认 ARCHIVED，需 activate 才会变 CURRENT
        season.setStatus("ARCHIVED");
        seasonMapper.insert(season);
        return toVO(season);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Season target = seasonMapper.selectById(id);
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "赛季不存在: " + id);
        }

        // 归档同 type 下所有当前 CURRENT（保证唯一）
        List<Season> currents = seasonMapper.selectList(new LambdaQueryWrapper<Season>()
                .eq(Season::getType, target.getType())
                .eq(Season::getStatus, "CURRENT"));
        for (Season current : currents) {
            current.setStatus("ARCHIVED");
            seasonMapper.updateById(current);
        }

        // 置目标为 CURRENT
        target.setStatus("CURRENT");
        seasonMapper.updateById(target);

        // 激活后给新赛季播种排名快照（从当前 user 余额重算）并清缓存。
        // 不刷新则新赛季 ranking 表无行，RankingServiceImpl.getRankings 按当前赛季过滤后查不到数据。
        // refreshRankings 只删/重算 (type, newSeasonCode)，归档赛季不受影响。
        rankingService.refreshRankings(target.getType(), target.getCode());
    }

    @Override
    public RankingPageVO getRankings(Long seasonId, RankingQuery query) {
        Season season = seasonMapper.selectById(seasonId);
        if (season == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "赛季不存在: " + seasonId);
        }

        // 归档查询走 DB（不读缓存）
        int size = Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
        int page = Math.max(query.getPage(), 1);

        LambdaQueryWrapper<Ranking> wrapper = new LambdaQueryWrapper<Ranking>()
                .eq(Ranking::getType, season.getType())
                .eq(Ranking::getSeason, season.getCode())
                .eq(query.getTier() != null, Ranking::getTier, query.getTier())
                .orderByAsc(Ranking::getRank);

        List<Ranking> rankings = rankingMapper.selectList(wrapper);

        List<Long> userIds = rankings.stream().map(Ranking::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        // 过滤孤儿行（user 已被软删/物理删除）
        List<RankingVO> list = rankings.stream()
                .filter(r -> userMap.containsKey(r.getUserId()))
                .map(r -> {
                    RankingVO vo = new RankingVO();
                    vo.setRank(r.getRank());
                    vo.setChange(r.getChange());
                    vo.setUserId(r.getUserId());
                    vo.setPoints(r.getPoints());
                    vo.setTier(r.getTier());
                    vo.setTierName(tierResolver.nameFor(r.getType(), r.getTier()));
                    User u = userMap.get(r.getUserId());
                    vo.setNickname(u.getNickname());
                    vo.setAvatarUrl(u.getAvatarUrl());
                    vo.setCity(u.getCity());
                    return vo;
                }).collect(Collectors.toList());

        int start = (page - 1) * size;
        int end = Math.min(start + size, list.size());
        List<RankingVO> pageList = start >= list.size()
                ? Collections.emptyList() : list.subList(start, end);
        PageResult<RankingVO> pageResult = PageResult.of(list.size(), page, size, pageList);

        RankingPageVO vo = new RankingPageVO();
        vo.setPage(pageResult);
        vo.setTierDistribution(countTierDistribution(season.getType(), season.getCode()));
        vo.setSeasonCode(season.getCode());
        vo.setSeasonName(season.getName());
        vo.setSeasonStatus(season.getStatus());
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

    private Map<Long, User> batchLoadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private SeasonVO toVO(Season season) {
        SeasonVO vo = new SeasonVO();
        vo.setId(season.getId());
        vo.setType(season.getType());
        vo.setCode(season.getCode());
        vo.setName(season.getName());
        vo.setStartDate(season.getStartDate());
        vo.setEndDate(season.getEndDate());
        vo.setStatus(season.getStatus());
        vo.setCreatedAt(season.getCreatedAt());
        return vo;
    }
}
