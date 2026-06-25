-- V13: 比赛对阵 + 比分记录 (match-grouping Spec 2)
--
-- A "match" is one round-robin pairing within a group: two slots (slotA, slotB)
-- each holding either a user (SINGLES) or a team (DOUBLES/MIXED), mutually exclusive.
-- Score is stored as a JSON array of {game, a, b} (1..3 games per match).
-- games_won_a / games_won_b are derived columns for fast ranking.

CREATE TABLE `match` (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,             -- redundant with group_id -> match_group.event_id, for fast lookup
  group_id BIGINT NOT NULL,             -- FK to match_group
  -- Slot A: exactly one of user/team
  slot_a_user_id BIGINT NULL,
  slot_a_team_id BIGINT NULL,
  -- Slot B: exactly one of user/team
  slot_b_user_id BIGINT NULL,
  slot_b_team_id BIGINT NULL,
  -- Lifecycle
  status VARCHAR(16) NOT NULL,          -- SCHEDULED | IN_PROGRESS | COMPLETED
  -- Score
  games JSON NULL,                       -- e.g. [{"game":1,"a":21,"b":15}, ...]
  games_won_a TINYINT NULL,              -- 0..3 derived; null until score submitted
  games_won_b TINYINT NULL,
  -- Audit
  submitted_by_user_id BIGINT NULL,
  submitted_at DATETIME NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_slot (group_id, slot_a_user_id, slot_b_user_id),
  KEY idx_event (event_id),
  KEY idx_group (group_id),
  KEY idx_slot_a_user (slot_a_user_id),
  KEY idx_slot_a_team (slot_a_team_id),
  KEY idx_slot_b_user (slot_b_user_id),
  KEY idx_slot_b_team (slot_b_team_id),
  FOREIGN KEY (group_id) REFERENCES match_group (id) ON DELETE CASCADE,
  CONSTRAINT chk_match_slot_a CHECK (
    (slot_a_user_id IS NOT NULL AND slot_a_team_id IS NULL)
    OR (slot_a_user_id IS NULL AND slot_a_team_id IS NOT NULL)
  ),
  CONSTRAINT chk_match_slot_b CHECK (
    (slot_b_user_id IS NOT NULL AND slot_b_team_id IS NULL)
    OR (slot_b_user_id IS NULL AND slot_b_team_id IS NOT NULL)
  ),
  CONSTRAINT chk_match_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED'))
);