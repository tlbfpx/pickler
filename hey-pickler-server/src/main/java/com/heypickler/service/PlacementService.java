package com.heypickler.service;

import com.heypickler.entity.EventPlacementPoints;
import com.heypickler.vo.PlacementPointsVO;

public interface PlacementService {

    /**
     * Issue placement point records for a completed event. Reads the event's
     * standings + placement-points table (per-event override or default), then
     * writes one point_record row per participant via
     * {@link PointService#issuePlacement}. Doubles rows are split 50/50
     * between team members.
     *
     * <p>Idempotency: throws {@code INVALID_STATUS_TRANSITION} if
     * {@code point_record} already has rows with {@code source=PLACEMENT} for
     * this event.
     */
    void issue(Long eventId);

    /** Returns the per-event override or the default table. */
    PlacementPointsVO getPoints(Long eventId);

    /** Persists a per-event override. Rejects if event is COMPLETED. */
    void setPoints(Long eventId, EventPlacementPoints override);
}