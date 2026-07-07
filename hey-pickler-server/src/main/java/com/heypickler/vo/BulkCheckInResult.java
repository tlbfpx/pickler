package com.heypickler.vo;

import lombok.Data;

import java.util.List;

/**
 * Loop-v14 — result of {@code POST /api/admin/events/{eventId}/registrations/bulk-check-in}.
 */
@Data
public class BulkCheckInResult {

    private Long eventId;
    private Integer requested;
    private Integer updated;
    private Skipped skipped;
    private List<Long> updatedRegistrationIds;

    @Data
    public static class Skipped {
        /** Ids not in registration table for this event. */
        private List<Long> notFound;
        /** Ids whose current status is WITHDRAWN. */
        private List<Long> withdrawn;
    }
}
