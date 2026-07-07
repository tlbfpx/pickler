package com.heypickler.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Loop-v13 — read-only aggregate of an event's operational state.
 * Returned by {@code GET /api/admin/events/{id}/summary}.
 *
 * <p>See {@code openspec/changes/event-summary-endpoint/specs/event-summary.md}
 * for the full field contract.
 */
@Data
public class EventSummaryVO {

    private Long eventId;
    private String title;
    private String type;             // SINGLES | DOUBLES | MIXED
    private String status;           // DRAFT | OPEN | FULL | IN_PROGRESS | COMPLETED | CANCELLED
    private LocalDateTime eventTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    /** currentParticipants / maxParticipants; 0.0 if max is null. */
    private Double fillRate;

    private RegistrationCountVO registration;
    private TeamCountVO teams;
    private MatchCountVO matches;
    private FeeSummaryVO fees;
    /** Statuses the event may transition to from its current state. */
    private List<String> transitionableStatuses;

    @Data
    public static class RegistrationCountVO {
        private Integer registered;
        private Integer checkedIn;
        private Integer withdrawn;
        /** checkedIn / registered; 0.0 if registered is 0. */
        private Double checkInRate;
    }

    @Data
    public static class TeamCountVO {
        private Integer pending;
        private Integer confirmed;
    }

    @Data
    public static class MatchCountVO {
        private Integer scheduled;
        private Integer inProgress;
        private Integer completed;
    }

    @Data
    public static class FeeSummaryVO {
        private Long totalCollected;
        /** Always {@code "CNY"} in v1. Future-proofing hook. */
        private String currency;
    }
}
