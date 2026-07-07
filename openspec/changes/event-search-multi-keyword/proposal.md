# Proposal: 赛事搜索增强（multi-keyword + 描述字段）

## Why

`EventServiceImpl.adminListEvents` 当前搜索限制：
1. **单 keyword**：只支持一个 keyword，且**只搜 `title` 字段**。description 完全不可搜
2. **没有空白分割**：用户输「周六 双打」只能匹配到「周六双打」整体字符串
3. **无 sort 选项**：固定 `event_time DESC`

实际运营场景：找"最近一周的双打赛事"——需要 keyword 命中 title **或** description 多个独立词。

## What Changes

### 后端
- **`EventServiceImpl.adminListEvents`** 行为变化：
  - keyword 用空白 split 成数组
  - 任一 keyword 命中 title OR description 即匹配（`OR` 条件，效率用 `like '%w1%' or like '%w2%' or (title like ... or description like ...)`）
  - 实际：每个 keyword 单独对 (title OR description) 命中，所有 keyword 之间 AND
- 新增 `sort` 参数：`event_time`（默认 desc）/ `created_at` / `current_participants`

### Non-Goals
- 不改 keyword 单字符串兼容（仍支持）
- 不重写查询（用 MyBatis LambdaWrapper）
- 不加 ES / 全文搜索

## Risks
- 性能：多 keyword + 2 字段 like 可能扫表
- 已 cap 200 行结果的现有行为不变
- 边界：空 keyword 不动
