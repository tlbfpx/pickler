ALTER TABLE event ADD COLUMN format VARCHAR(8) NOT NULL DEFAULT 'SINGLES';
ALTER TABLE event ADD COLUMN grouping_locked TINYINT(1) NOT NULL DEFAULT 0;
UPDATE event e SET format = COALESCE(
  (SELECT CASE match_type
            WHEN 'MIXED_DOUBLES' THEN 'MIXED'
            ELSE match_type
          END AS fmt
   FROM registration WHERE event_id = e.id
   GROUP BY match_type ORDER BY COUNT(*) DESC, match_type ASC LIMIT 1), 'SINGLES')
WHERE EXISTS (SELECT 1 FROM registration WHERE event_id = e.id);

CREATE TABLE team (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  member1_user_id BIGINT NOT NULL,
  member2_user_id BIGINT NOT NULL,
  name VARCHAR(64),
  status VARCHAR(12) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_member1 (event_id, member1_user_id),
  UNIQUE KEY uk_event_member2 (event_id, member2_user_id)
);

CREATE TABLE match_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  group_index INT NOT NULL,
  name VARCHAR(32),
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_index (event_id, group_index)
);

CREATE TABLE group_assignment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  team_id BIGINT NULL,
  seed INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_group (group_id),
  KEY idx_event (event_id),
  FOREIGN KEY (group_id) REFERENCES match_group(id) ON DELETE CASCADE,
  CHECK ((user_id IS NOT NULL AND team_id IS NULL) OR (user_id IS NULL AND team_id IS NOT NULL))
);

ALTER TABLE registration ADD COLUMN team_id BIGINT NULL;
