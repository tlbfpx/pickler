-- V14: per-event placement points table (match-grouping Spec 3)
--
-- When an event transitions to COMPLETED, PlacementService.issue reads this
-- table to map rank -> points. Admin configures it before completion via
-- PUT /api/admin/events/{id}/placement-points. If no row exists, the system
-- falls back to hey-pickler.placement.defaultPoints (configured per app).

CREATE TABLE event_placement_points (
  event_id BIGINT NOT NULL,
  points JSON NOT NULL,                  -- e.g. {"1":100, "2":60, "3":30}
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (event_id),
  FOREIGN KEY (event_id) REFERENCES event (id)
);