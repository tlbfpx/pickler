package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.entity.Notification;
import com.heypickler.mapper.NotificationMapper;
import com.heypickler.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification persistence. The {@link #push} method is intentionally
 * exception-swallowing: call sites are transactional (event/team state changes)
 * and a notification failure must not roll back a domain transition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int CONTENT_MAX = 1000;

    private final NotificationMapper notificationMapper;

    @Override
    public void push(Long userId, String type, String title, String content, String linkUrl) {
        if (userId == null || type == null || title == null) {
            // Caller mis-wired; log and bail rather than throw.
            log.warn("NotificationService.push skipped: userId/type/title required (userId={}, type={}, title={})", userId, type, title);
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title.length() > 128 ? title.substring(0, 128) : title);
            if (content != null) {
                n.setContent(content.length() > CONTENT_MAX ? content.substring(0, CONTENT_MAX) : content);
            }
            if (linkUrl != null && linkUrl.length() > 255) {
                n.setLinkUrl(linkUrl.substring(0, 255));
            } else {
                n.setLinkUrl(linkUrl);
            }
            n.setReadFlag(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.error("Failed to push notification (userId={}, type={}): {}", userId, type, e.getMessage(), e);
        }
    }

    @Override
    public IPage<Notification> listByUserId(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> w = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                // Newest first; id as tiebreaker for second-precision collisions.
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId);
        return notificationMapper.selectPage(new Page<>(page, size), w);
    }

    @Override
    public boolean markRead(Long id, Long userId) {
        if (id == null || userId == null) return false;
        int n = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
        return n > 0;
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) return 0;
        return notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
    }

    @Override
    public long unreadCount(Long userId) {
        if (userId == null) return 0;
        Long n = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
        return n == null ? 0L : n;
    }
}
