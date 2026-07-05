package com.heypickler.service;

import com.heypickler.entity.Notification;
import com.heypickler.mapper.NotificationMapper;
import com.heypickler.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @InjectMocks NotificationServiceImpl service;
    @Mock NotificationMapper notificationMapper;

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
        // Notification failures must never break a calling transaction.
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
}
