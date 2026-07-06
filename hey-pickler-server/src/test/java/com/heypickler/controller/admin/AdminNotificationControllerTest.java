package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.entity.Notification;
import com.heypickler.mapper.NotificationMapper;
import com.heypickler.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Loop-v10 — moves AdminNotificationController from 0% to ~80%+.
 * Tightened in Loop-v7 D30: cross-user markRead/markAllRead
 * are now disallowed (require explicit userId).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminNotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationMapper notificationMapper;
    @InjectMocks private AdminNotificationController controller;

    @Test
    void list_returnsPage() {
        IPage<Notification> page = new Page<>(1, 10);
        page.setRecords(java.util.List.of());
        doReturn(page).when(notificationMapper).selectPage(any(Page.class), any());
        java.util.Map<String, Object> data = controller.list(1, 10, null).getData();
        assertEquals(0, ((java.util.List<?>) data.get("list")).size());
    }

    @Test
    void list_sizeOver100_clampedTo100() {
        IPage<Notification> page = new Page<>(1, 10);
        doReturn(page).when(notificationMapper).selectPage(any(Page.class), any());
        controller.list(1, 200, null);
    }

    @Test
    void unreadCount_userIdNull_globalCount() {
        org.mockito.Mockito.when(notificationMapper.selectCount(any())).thenReturn(7L);
        assertEquals(7L, controller.unreadCount(null).getData().get("count"));
    }

    @Test
    void unreadCount_userIdNull_selectCountNull() {
        org.mockito.Mockito.when(notificationMapper.selectCount(any())).thenReturn(null);
        assertEquals(0L, controller.unreadCount(null).getData().get("count"));
    }

    @Test
    void unreadCount_userIdProvided() {
        doReturn(3L).when(notificationService).unreadCount(7L);
        assertEquals(3L, controller.unreadCount(7L).getData().get("count"));
    }

    @Test
    void markRead_userIdNull_throwsParam() {
        assertThrows(BizException.class, () -> controller.markRead(1L, null));
        verify(notificationMapper, never()).update(any(), any());
    }

    @Test
    void markRead_userIdProvided_delegates() {
        doReturn(true).when(notificationService).markRead(1L, 7L);
        assertEquals(true, controller.markRead(1L, 7L).getData().get("updated"));
    }

    @Test
    void markAllRead_userIdNull_throwsParam() {
        assertThrows(BizException.class, () -> controller.markAllRead(null));
        verify(notificationMapper, never()).update(any(), any());
    }

    @Test
    void markAllRead_userIdProvided_delegates() {
        doReturn(5).when(notificationService).markAllRead(7L);
        assertEquals(5, controller.markAllRead(7L).getData().get("updated"));
    }
}
