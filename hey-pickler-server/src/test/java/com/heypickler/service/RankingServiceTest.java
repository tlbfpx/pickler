package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.config.TierProperties;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.RankingServiceImpl;
import com.heypickler.vo.RankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RankingServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RankingMapper rankingMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private TierProperties tierProperties;
    @Mock
    private SeasonMapper seasonMapper;

    @InjectMocks
    private RankingServiceImpl rankingService;

    private static final String CURRENT_SEASON_CODE = "2026-Q2";
    private static final String ARCHIVED_SEASON_CODE = "2026-Q1";

    private User testUser;

    @org.junit.jupiter.api.BeforeAll
    static void warmLambdaCache() {
        // MyBatis-Plus LambdaQueryWrapper 在解析 SFunction→列名时依赖 TableInfo 的 lambda 缓存。
        // 纯单元测试（未走 MyBatis 启动流程）缓存为空，getSqlSegment() 会抛
        // "can not find lambda cache for this entity"。这里手动注册 Ranking 实体元数据。
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("com.heypickler.mapper.RankingMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Ranking.class);
    }

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setNickname("Test User");
        testUser.setAvatarUrl("http://example.com/avatar.jpg");
        testUser.setCity("Beijing");
        testUser.setStarPoints(0);
        testUser.setPartyPoints(0);
        testUser.setStarTier("BRONZE");
        testUser.setPartyTier("BRONZE");

        // 当前赛季 stub（refreshRankings(type) 旧签名会解析 CURRENT 赛季 code）
        Season currentStar = new Season();
        currentStar.setType("STAR");
        currentStar.setCode(CURRENT_SEASON_CODE);
        currentStar.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(currentStar);

        // tierProperties stubs——避免 NPE
        // keyFor：points>=1000 → MASTER, >=500 → GOLD, else BRONZE（仅用于断言匹配）
        when(tierProperties.keyFor(anyInt(), eq("STAR"))).thenAnswer(inv -> {
            int p = inv.getArgument(0);
            if (p >= 1000) return "MASTER";
            if (p >= 500) return "GOLD";
            return "BRONZE";
        });
        when(tierProperties.keyFor(anyInt(), eq("PARTY"))).thenAnswer(inv -> {
            int p = inv.getArgument(0);
            if (p >= 1000) return "MASTER";
            if (p >= 500) return "GOLD";
            return "BRONZE";
        });
        when(tierProperties.cacheKeysWithNull()).thenReturn(
                Arrays.asList("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", null));

        // nameFor 契约：英文 key → 中文档名。装配 RankingVO 时填充 tierName 必须走此方法。
        when(tierProperties.nameFor(eq("BRONZE"))).thenReturn("青铜");
        when(tierProperties.nameFor(eq("SILVER"))).thenReturn("白银");
        when(tierProperties.nameFor(eq("GOLD"))).thenReturn("黄金");
        when(tierProperties.nameFor(eq("PLATINUM"))).thenReturn("铂金");
        when(tierProperties.nameFor(eq("DIAMOND"))).thenReturn("钻石");
        when(tierProperties.nameFor(eq("MASTER"))).thenReturn("王者");
    }

    @Test
    void testRefreshRankings_NoUsers() {
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        verify(rankingMapper, never()).insert(any());
        verify(rankingMapper, never()).updateById(any());
    }

    @Test
    void testRefreshRankings_WithUsers() {
        User user1 = new User();
        user1.setId(1L);
        user1.setStarPoints(1000);

        User user2 = new User();
        user2.setId(2L);
        user2.setStarPoints(500);

        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(user1, user2));

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        verify(rankingMapper, times(2)).insert(argThat(ranking -> {
            return ranking.getType().equals("STAR") &&
                   ranking.getSeason().equals(CURRENT_SEASON_CODE);
        }));
    }

    @Test
    void testRefreshRankings_UpdateExisting() {
        User user1 = new User();
        user1.setId(1L);
        user1.setStarPoints(1000);

        Ranking existingRanking = new Ranking();
        existingRanking.setId(100L);
        existingRanking.setUserId(1L);
        existingRanking.setType("STAR");
        existingRanking.setSeason(CURRENT_SEASON_CODE);
        existingRanking.setRank(5);

        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(user1));
        // batchLoadRankings 用 rankingMapper.selectList 查现有 ranking
        when(rankingMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(existingRanking));

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        // 当前实现：delete-all + re-insert，不再 updateById
        verify(rankingMapper).delete(any(LambdaQueryWrapper.class));
        verify(rankingMapper).insert(argThat(ranking ->
            ranking.getUserId().equals(1L) &&
            ranking.getRank().equals(1) &&
            ranking.getChange().equals(4) // 5 - 1 = 4 (improved)
        ));
        verify(rankingMapper, never()).updateById(any());
    }

    @Test
    void testRefreshRankings_GroupByTier() {
        User user1 = new User();
        user1.setId(1L);
        user1.setStarPoints(1000); // MASTER

        User user2 = new User();
        user2.setId(2L);
        user2.setStarPoints(500); // GOLD

        User user3 = new User();
        user3.setId(3L);
        user3.setStarPoints(100); // BRONZE

        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(user1, user2, user3));

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        verify(rankingMapper, times(3)).insert(argThat(ranking -> {
            int points = ranking.getPoints();
            String tier = ranking.getTier();
            // tier 由 TierProperties.keyFor 决定；globalRank 自增（1/2/3）
            if (points >= 1000) return "MASTER".equals(tier) && ranking.getRank().equals(1);
            if (points >= 500) return "GOLD".equals(tier) && ranking.getRank().equals(2);
            return "BRONZE".equals(tier) && ranking.getRank().equals(3);
        }));
    }

    @Test
    void testRefreshRankings_ClearsRedisCache() {
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        // 6 档 tier key + null + 1 个 top5 = cacheKeysWithNull().size() + 1 = 8 次
        int expected = tierProperties.cacheKeysWithNull().size() + 1;
        verify(redisTemplate, times(expected)).delete(contains("ranking:STAR:"));
    }

    /**
     * 黑盒测试：refreshRankings(STAR, 当前赛季) 只删除当前赛季的 ranking 行，
     * 归档赛季的数据必须保留。通过 capture delete wrapper 的绑定参数值集合，
     * 断言 season code 被作为过滤条件传入（而非全表删除）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void refreshRankings_keepsArchivedSeason() {
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        rankingService.refreshRankings("STAR", CURRENT_SEASON_CODE);

        ArgumentCaptor<LambdaQueryWrapper<Ranking>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(rankingMapper).delete(captor.capture());

        // 渲染 wrapper SQL 片段——同时触发 lambda 列解析与参数绑定填充。
        // mock 的 rankingMapper.delete 不会执行 SQL，故 paramNameValuePairs 仍为空，
        // 必须主动 getSqlSegment() 才能拿到带绑定值的 map。
        LambdaQueryWrapper<Ranking> wrapper = captor.getValue();
        String sql = wrapper.getSqlSegment();
        java.util.Map<String, Object> params = wrapper.getParamNameValuePairs();

        // SQL 必须同时含 type 与 season 列过滤，归档赛季才不会被误删
        assertTrue(sql.toLowerCase().contains("season"),
                "delete wrapper 必须含 season 列过滤；实际 SQL=" + sql);
        assertTrue(sql.toLowerCase().contains("type"),
                "delete wrapper 必须含 type 列过滤；实际 SQL=" + sql);
        // 绑定值集合必须同时含 type 与 season 两个过滤值
        assertTrue(params.containsValue(CURRENT_SEASON_CODE),
                "delete wrapper 必须绑定 season code 作为过滤值；params=" + params);
        assertTrue(params.containsValue("STAR"),
                "delete wrapper 必须绑定 type 作为过滤值；params=" + params);
    }

    @Test
    void getRankings_ShouldFilterOutOrphanRows() {
        Ranking r1 = new Ranking();
        r1.setUserId(1L);
        r1.setType("STAR");
        r1.setTier("BRONZE");
        r1.setRank(1);
        r1.setPoints(100);
        r1.setChange(0);

        Ranking orphan = new Ranking();
        orphan.setUserId(999L);
        orphan.setType("STAR");
        orphan.setTier("BRONZE");
        orphan.setRank(2);
        orphan.setPoints(80);
        orphan.setChange(0);

        RankingQuery query = new RankingQuery();
        query.setType("STAR");
        query.setTier(null);
        query.setPage(1);
        query.setSize(20);

        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
            mock(org.springframework.data.redis.core.ValueOperations.class);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(rankingMapper.selectList(any())).thenReturn(Arrays.asList(r1, orphan));
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));

        com.heypickler.common.result.PageResult<RankingVO> result =
            rankingService.getRankings(query);

        assertEquals(1, result.getList().size(), "orphan ranking should be filtered out");
        assertEquals(1L, result.getList().get(0).getUserId());
        assertEquals("Test User", result.getList().get(0).getNickname());

        // Task 2.6: RankingVO 必须填充 tierName（中文档名），由 tierProperties.nameFor(tier) 推导
        RankingVO first = result.getList().get(0);
        assertEquals("青铜", first.getTierName(),
                "tierName 必须由 tierProperties.nameFor(tier) 装配；实际 tier=" + first.getTier());
        verify(tierProperties).nameFor("BRONZE");
    }

    @Test
    void getTop5_ShouldFilterOutOrphanRows() {
        Ranking r1 = new Ranking();
        r1.setUserId(1L);
        r1.setType("STAR");
        r1.setTier("BRONZE");
        r1.setRank(1);
        r1.setPoints(100);
        r1.setChange(0);

        Ranking orphan = new Ranking();
        orphan.setUserId(999L);
        orphan.setType("STAR");
        orphan.setTier("BRONZE");
        orphan.setRank(2);
        orphan.setPoints(80);
        orphan.setChange(0);

        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
            mock(org.springframework.data.redis.core.ValueOperations.class);
        when(valueOps.get(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(rankingMapper.selectList(any())).thenReturn(Arrays.asList(r1, orphan));
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));

        List<RankingVO> result = rankingService.getTop5("STAR");

        assertEquals(1, result.size(), "orphan ranking should be filtered out from top5");
        assertEquals(1L, result.get(0).getUserId());

        // Task 2.6: getTop5 装配路径同样必须填充 tierName
        assertEquals("青铜", result.get(0).getTierName(),
                "getTop5 tierName 必须由 nameFor(tier) 装配；实际 tier=" + result.get(0).getTier());
    }
}
