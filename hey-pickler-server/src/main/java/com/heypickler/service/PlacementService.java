package com.heypickler.service;

import com.heypickler.entity.EventPlacementPoints;
import com.heypickler.vo.PlacementDetailVO;
import com.heypickler.vo.PlacementPointsVO;

import java.util.List;

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

    /** Removes the per-event override so future GETs return the default table.
     *  Rejects if event is COMPLETED. No-op if no override exists. */
    void clearPoints(Long eventId);

    /**
     * Lists the PLACEMENT point_record rows for an event, ordered by points DESC,
     * id ASC. rank is the row index + 1. Nickname is loaded via a single
     * selectBatchIds. Throws NOT_FOUND if event is missing; returns an empty
     * list if no placements have been issued yet.
     */
    List<PlacementDetailVO> listByEventId(Long eventId);
}