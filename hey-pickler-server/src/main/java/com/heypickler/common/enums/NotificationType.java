package com.heypickler.common.enums;

/**
 * Allowed {@code Notification.type} values. Persisted as plain VARCHAR —
 * keep new values additive; this enum is a compile-time guard, not a DB-level
 * constraint.
 */
public enum NotificationType {
    EVENT_IN_PROGRESS,
    EVENT_COMPLETED,
    TEAM_INVITED,
    BANNER_PUBLISHED,
    SYSTEM
}
