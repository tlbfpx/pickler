package com.heypickler.service;

import com.heypickler.common.enums.PointSource;
import com.heypickler.common.exception.BizException;
// TierResolver 注入见下方 @Mock（接口，Mockito 直接 mock）
import com.heypickler.entity.Event;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.entity.PointRecord;
import com.heypickler.listener.PointChangeListener;
import com.heypickler.mapper.EventMapper;
import com.heypickler.mapper.PointRecordMapper;
import com.heypickler.mapper.SeasonMapper;
import com.heypickler.mapper.UserMapper;
import com.heypickler.service.dto.PointEntry;
import com.heypickler.service.impl.PointServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {
    @InjectMocks PointServiceImpl service;
    @Mock SeasonMapper seasonMapper;
    @Mock PointRecordMapper pointRecordMapper;
    @Mock UserMapper userMapper;
    @Mock EventMapper eventMapper;
    @Mock TierResolver tierResolver;
    @Mock ApplicationEventPublisher eventPublisher;

    /**
     * P3 起 writeRecord 用 LambdaUpdateWrapper&lt;User&gt;（原子 setSql），纯单测无 MyBatis 启动流程，
     * User 的 lambda TableInfo 缓存为空 → "can not find lambda cache"。手动注册预热（同 RankingServiceTest）。
     */
    @org.junit.jupiter.api.BeforeAll
    static void warmLambdaCache() {
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("com.heypickler.mapper.UserMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @Test
    void enterPoints_writesSourceAndSeasonCode_andAccumulates_andPublishesSeasonEvent() {
        Season s = new Season();
        s.setType("STAR");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);
        User u = new User();
        u.setId(1L);
        u.setStarPoints(400);
        u.setStarTier("BRONZE");
        when(userMapper.selectBatchIds(any())).thenReturn(java.util.List.of(u));
        when(tierResolver.keyFor(500, "STAR")).thenReturn("SILVER");

        service.enterPoints(null, "STAR", List.of(new PointEntry(1L, 100, "手动")), PointSource.MANUAL, 9L);

        ArgumentCaptor<PointRecord> cap = ArgumentCaptor.forClass(PointRecord.class);
        verify(pointRecordMapper).insert(cap.capture());
        assertEquals("MANUAL", cap.getValue().getSource());
        assertEquals("2026-Q2", cap.getValue().getSeasonCode());
        assertEquals(500, u.getStarPoints());
        assertEquals("SILVER", u.getStarTier());
        var evt = ArgumentCaptor.forClass(PointChangeListener.PointChangeEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertEquals("2026-Q2", evt.getValue().seasonCode());
    }

    @Test
    void getBalance_returnsStarOrPartyPoints() {
        User u = new User();
        u.setId(1L);
        u.setStarPoints(1230);
        u.setPartyPoints(450);
        when(userMapper.selectById(1L)).thenReturn(u);

        assertEquals(1230, service.getBalance(1L, "STAR"));
        assertEquals(450, service.getBalance(1L, "PARTY"));
    }

    /**
     * Loop engineering D2 兜底：PlacementService.issue() 并发场景下，
     * 第二次 INSERT 会触发 uk_event_user_source 唯一约束。
     * PointServiceImpl.writeRecord 必须静默跳过，且不能加和 user points。
     */
    @Test
    void issuePlacement_idempotent_skipsDuplicateAndNoDoubleAccumulation() {
        User u = new User();
        u.setId(1L);
        u.setStarPoints(100);
        u.setStarTier("BRONZE");
        when(userMapper.selectById(1L)).thenReturn(u);
        // 没有 CURRENT season → placement 仍允许（容忍）
        when(seasonMapper.selectOne(any())).thenReturn(null);
        Event e = new Event();
        e.setId(7L);
        e.setType("STAR");
        when(eventMapper.selectById(7L)).thenReturn(e);

        // 模拟并发：第二次插入被 DB 拒绝
        doThrow(new DataIntegrityViolationException("uk_event_user_when_placement"))
                .when(pointRecordMapper).insert(any(PointRecord.class));

        // 不应抛异常；user points 不能被加和（仍是 100）
        service.issuePlacement(7L, 1L, 80, "test");

        assertEquals(100, u.getStarPoints(), "不应叠加，原因：DB 唯一约束已存在行");
        verify(userMapper, never()).updateById(any(User.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---------- revertPointRecord (ADJUST 撤销) ----------

    @Test
    void revertPointRecord_manual_writesAdjustNegatedRow_andPublishesEvent() {
        PointRecord original = new PointRecord();
        original.setId(42L);
        original.setUserId(1L);
        original.setType("STAR");
        original.setSource("MANUAL");
        original.setSeasonCode("2026-Q2");
        original.setPoints(100);
        original.setReason("手动奖励");
        when(pointRecordMapper.selectById(42L)).thenReturn(original);

        Season s = new Season();
        s.setType("STAR");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);

        User u = new User();
        u.setId(1L);
        u.setStarPoints(500);
        u.setStarTier("GOLD");
        when(userMapper.selectById(1L)).thenReturn(u);

        service.revertPointRecord(42L, 9L);

        ArgumentCaptor<PointRecord> cap = ArgumentCaptor.forClass(PointRecord.class);
        verify(pointRecordMapper).insert(cap.capture());
        PointRecord revert = cap.getValue();
        assertEquals("ADJUST", revert.getSource());
        assertEquals(-100, revert.getPoints());
        assertEquals("2026-Q2", revert.getSeasonCode());
        assertTrue(revert.getReason().contains("撤销 #42"), "原因须引用原记录 id；实际=" + revert.getReason());
        assertEquals(9L, revert.getOperatorId());
        assertEquals(400, u.getStarPoints(), "余额须扣减 100");
        var evt = ArgumentCaptor.forClass(PointChangeListener.PointChangeEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertEquals("2026-Q2", evt.getValue().seasonCode());
    }

    @Test
    void revertPointRecord_placement_rejected() {
        PointRecord original = new PointRecord();
        original.setId(42L);
        original.setUserId(1L);
        original.setType("STAR");
        original.setSource("PLACEMENT");
        original.setSeasonCode("2026-Q2");
        original.setPoints(100);
        when(pointRecordMapper.selectById(42L)).thenReturn(original);

        assertThrows(BizException.class, () -> service.revertPointRecord(42L, 9L));
        verify(pointRecordMapper, never()).insert(any(PointRecord.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void revertPointRecord_crossSeason_rejected() {
        PointRecord original = new PointRecord();
        original.setId(42L);
        original.setUserId(1L);
        original.setType("STAR");
        original.setSource("MANUAL");
        original.setSeasonCode("2026-Q1"); // 旧赛季
        original.setPoints(100);
        when(pointRecordMapper.selectById(42L)).thenReturn(original);

        Season current = new Season();
        current.setType("STAR");
        current.setCode("2026-Q2"); // 当前是 Q2
        current.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(current);

        assertThrows(BizException.class, () -> service.revertPointRecord(42L, 9L));
        verify(pointRecordMapper, never()).insert(any(PointRecord.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void revertPointRecord_adjust_canBeRevertedAgain() {
        // ADJUST 行本身也在允许集合内 → 撤销一条 ADJUST(负分) 写 +100 ADJUST
        PointRecord original = new PointRecord();
        original.setId(42L);
        original.setUserId(1L);
        original.setType("STAR");
        original.setSource("ADJUST");
        original.setSeasonCode("2026-Q2");
        original.setPoints(-100);
        original.setReason("撤销 #40: xxx");
        when(pointRecordMapper.selectById(42L)).thenReturn(original);

        Season s = new Season();
        s.setType("STAR");
        s.setCode("2026-Q2");
        s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);

        User u = new User();
        u.setId(1L);
        u.setStarPoints(400);
        u.setStarTier("GOLD");
        when(userMapper.selectById(1L)).thenReturn(u);

        service.revertPointRecord(42L, 9L);

        ArgumentCaptor<PointRecord> cap = ArgumentCaptor.forClass(PointRecord.class);
        verify(pointRecordMapper).insert(cap.capture());
        assertEquals("ADJUST", cap.getValue().getSource());
        assertEquals(100, cap.getValue().getPoints(), "撤销负分行须写正分 -(-100)=100");
        assertEquals(500, u.getStarPoints());
    }
}
