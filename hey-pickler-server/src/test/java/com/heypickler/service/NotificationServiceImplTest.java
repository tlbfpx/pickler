package com.heypickler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.aspect.NotificationPushMonitor;
import com.heypickler.entity.Notification;
import com.heypickler.mapper.NotificationMapper;
import com.heypickler.service.impl.NotificationServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    /**
     * LambdaCache warmup — required for unit tests that use LambdaUpdateWrapper
     * / LambdaQueryWrapper (see project memory on mybatis-plus).
     */
    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
        a.setCurrentNamespace("com.heypickler.mapper.NotificationMapper");
        TableInfoHelper.initTableInfo(a, Notification.class);
    }

    @InjectMocks NotificationServiceImpl service;
    @Mock NotificationMapper notificationMapper;
    @Mock NotificationPushMonitor monitor;

    @Test
    void push_persistsRowWithDefaults() {
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        service.push(7L, "EVENT_IN_PROGRESS", "开赛", "赛事 X 已开赛", "/events/1?tab=match");
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(cap.capture());
        Notification n = cap.getValue();
        assertEquals(7L, n.getUserId());
        assertEquals("EVENT_IN_PROGRESS", n.getType());
        assertEquals("开赛", n.getTitle());
        assertEquals("赛事 X 已开赛", n.getContent());
        assertEquals("/events/1?tab=match", n.getLinkUrl());
        assertEquals(0, n.getReadFlag());
    }

    @Test
    void push_skipsOnNullTitleAndDoesNotThrow() {
        service.push(7L, "EVENT_IN_PROGRESS", null, null, null);
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void push_truncatesLongTitleAndContent() {
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        String longTitle = "x".repeat(200);
        String longContent = "y".repeat(1200);
        service.push(1L, "SYSTEM", longTitle, longContent, null);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(cap.capture());
        Notification n = cap.getValue();
        assertEquals(128, n.getTitle().length());
        assertEquals(1000, n.getContent().length());
    }

    /**
     * Loop-v3 D15 — push persistence failure must increment the monitor
     * counter (so a Grafana alert can fire when failures climb) but must
     * NOT propagate the exception to the caller.
     */
    @Test
    void push_onInsertFailure_incrementsMonitorAndSwallowsException() {
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new RuntimeException("DB down"));
        // No exception expected
        service.push(7L, "EVENT_IN_PROGRESS", "开赛", "正文", null);
        verify(monitor).recordFailure(eq(7L), eq("EVENT_IN_PROGRESS"), any());
    }

    @Test
    void push_truncatesLongLinkUrl() {
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        String longUrl = "/events/".repeat(40);  // > 255 chars
        service.push(1L, "SYSTEM", "title", null, longUrl);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(cap.capture());
        assertEquals(255, cap.getValue().getLinkUrl().length());
    }

    @Test
    void listByUserId_passesThroughWithoutFilter() {
        IPage<Notification> stubbed = new Page<>(1, 10);
        doReturn(stubbed).when(notificationMapper).selectPage(any(Page.class), any());
        IPage<Notification> result = service.listByUserId(7L, 1, 10);
        assertEquals(stubbed, result);
        verify(notificationMapper).selectPage(any(Page.class), any());
    }

    @Test
    void markRead_returnsTrueOnlyWhenUnreadRowUpdated() {
        when(notificationMapper.update(eq(null), any())).thenReturn(0).thenReturn(1);
        assertFalse(service.markRead(99L, 7L));
        assertTrue(service.markRead(99L, 7L));
    }

    @Test
    void markRead_handlesNullIdOrUserId() {
        assertFalse(service.markRead(null, 7L));
        assertFalse(service.markRead(99L, null));
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void markAllRead_returnsCount() {
        when(notificationMapper.update(eq(null), any())).thenReturn(7);
        assertEquals(7, service.markAllRead(7L));
    }

    @Test
    void markAllRead_handlesNullUserId() {
        assertEquals(0, service.markAllRead(null));
        verifyNoInteractions(notificationMapper);
    }

    @Test
    void unreadCount_returnsMapperCountOrZero() {
        when(notificationMapper.selectCount(any())).thenReturn(5L).thenReturn(null);
        assertEquals(5L, service.unreadCount(7L));
        assertEquals(0L, service.unreadCount(7L));
        assertEquals(0L, service.unreadCount(null));
    }
}
