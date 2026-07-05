-- V17: PlacementService.issue 幂等兜底（final 版本）
--
-- Problem: PlacementServiceImpl.issue() 通过「select count(*) > 0 → 抛错」
-- 来防止同一赛事重复发分，但 two concurrent admins 调用 /complete 都会通过
-- 计数检查（两个事务同时看到 0），然后两份 PLACEMENT point_record 写入。
-- Result: user 被发双倍分。
--
-- 修复策略 —— MySQL 8+ functional unique index：
-- 仅在 source = 'PLACEMENT' 时启用 (event_id, user_id) 唯一性，
-- 其他来源（MANUAL / REGISTRATION / CHECK_IN 等）的合法多行写入不受影响。
-- 该索引被 PointServiceImpl.writeRecord 的 DataIntegrityViolationException
-- catch 路径兜底：插入并发失败时静默跳过，不向上层冒泡。
--
-- 验证：已有 43 行 PLACEMENT 历史数据无重复，函数索引直接生效。
-- PointServiceImplTest.issuePlacement_idempotent_skipsDuplicateAndNoDoubleAccumulation
-- 负责应用层兜底的回归覆盖。

ALTER TABLE point_record
  ADD UNIQUE INDEX uk_event_user_when_placement (
    (CASE WHEN source = 'PLACEMENT' THEN CONCAT(event_id, '-', user_id) ELSE NULL END)
  );
