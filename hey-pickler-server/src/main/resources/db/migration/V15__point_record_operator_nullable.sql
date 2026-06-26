-- V15: allow point_record.operator_id to be NULL.
--
-- Spec 3 introduces PLACEMENT as a system-driven source — MatchService.complete()
-- triggers PlacementService.issue() which writes point_record rows without an
-- admin operator. V1 originally declared operator_id NOT NULL for the manual
-- entry path; relax it now so both flows can share the same table.

ALTER TABLE point_record MODIFY COLUMN operator_id BIGINT UNSIGNED NULL;