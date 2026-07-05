package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public-ish view of a team for sharing / invite purposes.
 *
 * <p>The {@code teamId} itself acts as the invite code: a partner joining the
 * event can identify the team by entering the numeric ID in their wxapp.
 * Fields cover everything a sharing surface needs without exposing PII beyond
 * the captain's display name.
 */
@Data
public class TeamInviteVO {
    private Long teamId;
    private Long eventId;
    private String eventTitle;
    private String captainName;
    /** Always equal to {@link #getExpiry()} — kept distinct so clients can format explicitly. */
    private LocalDateTime expiresAt;
}
