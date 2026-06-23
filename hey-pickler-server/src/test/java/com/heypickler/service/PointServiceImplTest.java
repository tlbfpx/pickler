package com.heypickler.service;

import com.heypickler.common.enums.PointSource;
import com.heypickler.config.TierProperties;
import com.heypickler.entity.Season;
import com.heypickler.entity.User;
import com.heypickler.entity.PointRecord;
import com.heypickler.listener.PointChangeListener;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {
    @InjectMocks PointServiceImpl service;
    @Mock SeasonMapper seasonMapper;
    @Mock PointRecordMapper pointRecordMapper;
    @Mock UserMapper userMapper;
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
}
