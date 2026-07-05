package com.heypickler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heypickler.entity.Notification;

/**
 * In-app notification service. MVP: admin-side only; wxapp reuses the same
 * underlying tables later.
 *
 * <p>{@link #push} is fire-and-forget — never throw out of {@code push}, the
 * callers are transactional and a notification miss should not roll back a
 * domain transition. Wrapped in try/catch in {@code NotificationServiceImpl}.
 */
public interface NotificationService {

    /**
     * Create a new notification row for {@code userId}.
     *
     * @param type     one of {@link com.heypickler.common.enums.NotificationType} — kept
     *                 as String so callers can pass the enum's {@code name()} directly.
     * @param title    short header shown in lists.
     * @param content  optional body text (truncated to fit {@code VARCHAR(1024)} at write).
     * @param linkUrl  optional in-app destination, e.g. {@code /events/123?tab=match}.
     */
    void push(Long userId, String type, String title, String content, String linkUrl);

    /** Paged history for a user (newest first). {@code page} is 1-based. */
    IPage<Notification> listByUserId(Long userId, int page, int size);

    /** Mark one notification read; returns true if a row was actually updated (caller-owned row). */
    boolean markRead(Long id, Long userId);

    /** Mark every unread notification for the user as read; returns count updated. */
    int markAllRead(Long userId);

    /** Number of unread rows (read_flag=0) for the user. */
    long unreadCount(Long userId);
}
