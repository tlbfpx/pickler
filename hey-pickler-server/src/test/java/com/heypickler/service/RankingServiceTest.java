package com.heypickler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.PointEntryRequest;
import com.heypickler.dto.admin.PointEntryRequest.PointRecordItem;
import com.heypickler.dto.app.RankingQuery;
import com.heypickler.entity.Event;
import com.heypickler.entity.PointRecord;
import com.heypickler.entity.Ranking;
import com.heypickler.entity.User;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.RankingMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.impl.RankingServiceImpl;
import com.heypickler.vo.RankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
    private EventMapper eventMapper;
    @Mock
    private PointRecordMapper pointRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RankingMapper rankingMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RankingServiceImpl rankingService;

    private Event testEvent;
    private User testUser;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setId(1L);
        testEvent.setType("STAR");
        testEvent.setTitle("Test Event");

        testUser = new User();
        testUser.setId(1L);
        testUser.setNickname("Test User");
        testUser.setAvatarUrl("http://example.com/avatar.jpg");
        testUser.setCity("Beijing");
        testUser.setStarPoints(0);
        testUser.setPartyPoints(0);
        testUser.setStarTier("SHINING");
        testUser.setPartyTier("SHINING");
    }


    private PointEntryRequest.PointRecordItem createItem(Long userId, int points, String reason) {
        PointEntryRequest.PointRecordItem item = new PointEntryRequest.PointRecordItem();
        item.setUserId(userId);
        item.setPoints(points);
        item.setReason(reason);
        return item;
    }

    @Test
    void testCalculateTier_Star_Shining() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 0, "STAR");
        assertEquals("SHINING", tier);
    }

    @Test
    void testCalculateTier_Star_Super() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 500, "STAR");
        assertEquals("SUPER", tier);
    }

    @Test
    void testCalculateTier_Star_Legend() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 1000, "STAR");
        assertEquals("LEGEND", tier);
    }

    @Test
    void testCalculateTier_Party_Shining() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 0, "PARTY");
        assertEquals("SHINING", tier);
    }

    @Test
    void testCalculateTier_Party_Super() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 200, "PARTY");
        assertEquals("SUPER", tier);
    }

    @Test
    void testCalculateTier_Party_Legend() {
        String tier = (String) ReflectionTestUtils.invokeMethod(rankingService, "calculateTier", 500, "PARTY");
        assertEquals("LEGEND", tier);
    }

    @Test
    void testEnterPoints_EventNotFound() {
        when(eventMapper.selectById(1L)).thenReturn(null);

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(createItem(1L, 100, "Test")));

        BizException exception = assertThrows(BizException.class, () -> {
            rankingService.enterPoints(1L, request, 1L);
        });

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(eventMapper).selectById(1L);
        verify(pointRecordMapper, never()).insert(any());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    void testEnterPoints_Success() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(pointRecordMapper.insert(any(PointRecord.class))).thenReturn(1);
        doNothing().when(eventPublisher).publishEvent(any(com.heypickler.listener.PointChangeListener.PointChangeEvent.class));

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(createItem(1L, 100, "Test reason")));

        rankingService.enterPoints(1L, request, 100L);

        verify(pointRecordMapper).insert(argThat(record -> {
            return record.getUserId().equals(1L) &&
                   record.getEventId().equals(1L) &&
                   record.getType().equals("STAR") &&
                   record.getPoints().equals(100) &&
                   record.getReason().equals("[Test Event] Test reason") &&
                   record.getOperatorId().equals(100L);
        }));

        verify(userMapper).updateById(argThat(user -> {
            return user.getStarPoints().equals(100) &&
                   user.getStarTier().equals("SHINING");
        }));
    }

    @Test
    void testEnterPoints_MultipleRecords() {
        User user2 = new User();
        user2.setId(2L);
        user2.setStarPoints(0);
        user2.setStarTier("SHINING");

        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(userMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(testUser, user2));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(pointRecordMapper.insert(any(PointRecord.class))).thenReturn(1);
        doNothing().when(eventPublisher).publishEvent(any());

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(
            createItem(1L, 100, "Test 1"),
            createItem(2L, 200, "Test 2")
        ));

        rankingService.enterPoints(1L, request, 100L);

        verify(pointRecordMapper, times(2)).insert(any());
        verify(userMapper, times(2)).updateById(any(User.class));
    }

    @Test
    void testEnterPoints_PartyType() {
        testEvent.setType("PARTY");
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(pointRecordMapper.insert(any(PointRecord.class))).thenReturn(1);
        doNothing().when(eventPublisher).publishEvent(any(com.heypickler.listener.PointChangeListener.PointChangeEvent.class));

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(createItem(1L, 100, "Test")));

        rankingService.enterPoints(1L, request, 100L);

        verify(pointRecordMapper).insert(argThat(record ->
            record.getType().equals("PARTY")
        ));

        verify(userMapper).updateById(argThat(user -> {
            return user.getPartyPoints().equals(100) &&
                   user.getPartyTier().equals("SHINING");
        }));
    }

    @Test
    void testEnterPoints_NegativePoints_FloorAtZero() {
        testUser.setStarPoints(50);
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(pointRecordMapper.insert(any(PointRecord.class))).thenReturn(1);
        doNothing().when(eventPublisher).publishEvent(any(com.heypickler.listener.PointChangeListener.PointChangeEvent.class));

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(createItem(1L, -100, "Penalty")));

        rankingService.enterPoints(1L, request, 100L);

        verify(userMapper).updateById(argThat(user ->
            user.getStarPoints().equals(0) // Floor at 0
        ));
    }

    @Test
    void testEnterPoints_TierUpgrade() {
        when(eventMapper.selectById(1L)).thenReturn(testEvent);
        when(userMapper.selectBatchIds(anyList())).thenReturn(Collections.singletonList(testUser));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(pointRecordMapper.insert(any(PointRecord.class))).thenReturn(1);
        doNothing().when(eventPublisher).publishEvent(any(com.heypickler.listener.PointChangeListener.PointChangeEvent.class));

        PointEntryRequest request = new PointEntryRequest();
        request.setRecords(Arrays.asList(createItem(1L, 500, "Test")));

        rankingService.enterPoints(1L, request, 100L);

        verify(userMapper).updateById(argThat(user ->
            user.getStarPoints().equals(500) &&
            user.getStarTier().equals("SUPER")
        ));
    }

    @Test
    void testRefreshRankings_NoUsers() {
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        rankingService.refreshRankings("STAR");

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
        when(rankingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        rankingService.refreshRankings("STAR");

        verify(rankingMapper, times(2)).insert(argThat(ranking -> {
            return ranking.getType().equals("STAR") &&
                   ranking.getSeason().equals("2026-Q2");
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
        existingRanking.setSeason("2026-Q2");
        existingRanking.setRank(5);

        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(user1));
        // batchLoadRankings 用 rankingMapper.selectList 查现有 ranking
        when(rankingMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(existingRanking));

        rankingService.refreshRankings("STAR");

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
        user1.setStarPoints(1000); // LEGEND

        User user2 = new User();
        user2.setId(2L);
        user2.setStarPoints(500); // SUPER

        User user3 = new User();
        user3.setId(3L);
        user3.setStarPoints(100); // SHINING

        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(user1, user2, user3));
        when(rankingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        rankingService.refreshRankings("STAR");

        verify(rankingMapper, times(3)).insert(argThat(ranking -> {
            int points = ranking.getPoints();
            String tier = ranking.getTier();
            // 当前实现：globalRank 自增（1/2/3），tier 由 points 决定
            if (points >= 1000) return "LEGEND".equals(tier) && ranking.getRank().equals(1);
            if (points >= 500) return "SUPER".equals(tier) && ranking.getRank().equals(2);
            return "SHINING".equals(tier) && ranking.getRank().equals(3);
        }));
    }

    @Test
    void testRefreshRankings_ClearsRedisCache() {
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        rankingService.refreshRankings("STAR");

        // 当前实现清理 4 个 tier key（LEGEND/SUPER/SHINING/null）+ 1 个 top5 = 5 次
        verify(redisTemplate, times(5)).delete(contains("ranking:STAR:"));
    }
}
