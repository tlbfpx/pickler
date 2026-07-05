package com.heypickler.service;

import com.heypickler.common.enums.PointSource;
import com.heypickler.config.TierProperties;
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
    @Mock TierProperties tierProperties;
    @Mock ApplicationEventPublisher eventPublisher;

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
        when(tierProperties.keyFor(500, "STAR")).thenReturn("SILVER");

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
}
