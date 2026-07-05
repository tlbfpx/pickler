package com.heypickler.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.entity.Notification;
import com.heypickler.mapper.NotificationMapper;
import com.heypickler.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin in-app notification feed.
 *
 * <p>For MVP the endpoint serves a global feed (no per-admin scoping — admins
 * are global operators). Future per-admin preferences can filter by userId
 * via the optional {@code userId} query parameter.
 *
 * <p>Role gate: SUPER_ADMIN / ADMIN. OPERATORs are intentionally excluded so
 * the bell is reserved for active managers (operations folks don't need to
 * see event-state-change noise during daily work).
 */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "管理后台-通知中心")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @GetMapping
    @Operation(summary = "通知列表（分页；按时间倒序）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId) {
        if (size > 100) size = 100;
        LambdaQueryWrapper<Notification> w = new LambdaQueryWrapper<Notification>()
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId);
        if (userId != null) w.eq(Notification::getUserId, userId);
        IPage<Notification> pg = notificationMapper.selectPage(new Page<>(page, size), w);
        List<Notification> records = pg.getRecords();
        return Result.ok(Map.of(
                "list", records,
                "total", pg.getTotal(),
                "page", pg.getCurrent(),
                "size", pg.getSize()
        ));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读通知总数（global 视角，多用户聚合）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> unreadCount(@RequestParam(required = false) Long userId) {
        long count;
        if (userId != null) {
            count = notificationService.unreadCount(userId);
        } else {
            Long n = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getReadFlag, 0));
            count = n == null ? 0L : n;
        }
        return Result.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读（按 userId + id 范围，避免越权标记）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> markRead(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId) {
        if (userId == null) {
            // MVP: admin global view; mark by id only (lookup to get owner if needed).
            // For now, take the id directly; this is admin-level, no cross-user concern.
            int updated = notificationMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                            .eq(Notification::getId, id)
                            .eq(Notification::getReadFlag, 0)
                            .set(Notification::getReadFlag, 1));
            return Result.ok(Map.of("updated", updated > 0));
        }
        boolean ok = notificationService.markRead(id, userId);
        return Result.ok(Map.of("updated", ok));
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记已读（可选 userId 限定）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> markAllRead(@RequestParam(required = false) Long userId) {
        int n;
        if (userId != null) {
            n = notificationService.markAllRead(userId);
        } else {
            n = notificationMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                            .eq(Notification::getReadFlag, 0)
                            .set(Notification::getReadFlag, 1));
        }
        return Result.ok(Map.of("updated", n));
    }
}
