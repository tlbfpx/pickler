// 状态门控：纯函数式（无副作用、无状态）。组件在 computed 中调用，返回 true/false
// 用于按钮的 :disabled 绑定 + :title tooltip 解释原因。
// 单一事实源：本文件即规则。任何新增/修改门控，先改这里。

import type { Event } from '@/types'

// ---------------- Event-level ----------------

/** 编辑：未取消的赛事可编辑。 */
export const canEditEvent = (e: Event | null) => !!e && e.status !== 'CANCELLED'

/** 删除：只有草稿（DRAFT）可删。 */
export const canDeleteEvent = (e: Event | null) => !!e && e.status === 'DRAFT'

/** 取消赛事：未结束/未取消的赛事可被取消。 */
export const canCancelEvent = (e: Event | null) =>
  !!e && e.status !== 'COMPLETED' && e.status !== 'CANCELLED'

/** 编辑积分规则：未取消且未结束。COMPLETED 之后规则已下发，不可改。 */
export const canEditPoints = (e: Event | null) =>
  !!e && e.status !== 'CANCELLED' && e.status !== 'COMPLETED'

/** 浏览报名名单：仅 OPEN / FULL 阶段可看（用户可报名）。 */
export const canViewRegistrations = (e: Event | null) =>
  !!e && (e.status === 'OPEN' || e.status === 'FULL')

/** 发分 / 完成赛事：仅进行中可结赛并发分。 */
export const canIssuePoints = (e: Event | null) => !!e && e.status === 'IN_PROGRESS'

// ---------------- Match-level ----------------

/** 代录比分：未完成的比赛可代录。 */
export const canRecordScore = (m: { status?: string }) => m.status !== 'COMPLETED'

/** 重置比分：仅已完成的比赛可重置。 */
export const canResetScore = (m: { status?: string }) => m.status === 'COMPLETED'

// ---------------- Registration-level ----------------

/** 签到：仅 REGISTERED 状态可签到（CHECKED_IN / WITHDRAWN 不可重复签到）。 */
export const canCheckIn = (r: { status: string }) => r.status === 'REGISTERED'

/** 取消报名：除已退赛（WITHDRAWN）外都可取消。 */
export const canWithdraw = (r: { status: string }) => r.status !== 'WITHDRAWN'

/** 批量签到：所有选中项均为 REGISTERED 状态时可批量。 */
export const canBulkCheckIn = (rows: { status: string }[]) =>
  rows.length > 0 && rows.every(r => r.status === 'REGISTERED')

/** 批量签到的赛事前置：仅 OPEN / FULL 状态支持签到。 */
export const canBulkCheckInByEvent = (e: Event | null) =>
  !!e && (e.status === 'OPEN' || e.status === 'FULL')

// ---------------- GroupingPanel ----------------

/** 开始分组：OPEN/FULL 阶段且未锁定。 */
export const canStartGrouping = (e: Event | null) =>
  !!e && (e.status === 'OPEN' || e.status === 'FULL') && !e.groupingLocked

/** 解锁并清空：已锁定时可解锁。 */
export const canUnlockGroups = (e: Event | null) => !!e && !!e.groupingLocked

/** 锁定：未锁定时可锁定。 */
export const canLockGroups = (e: Event | null) => !!e && !e.groupingLocked

// ---------------- MatchesPanel ----------------

/** 生成对阵：分组已锁定 + 当前无对阵。 */
export const canGenerateMatches = (e: Event | null, hasMatches: boolean) =>
  !!e && !!e.groupingLocked && !hasMatches

// ---------------- Team management (admin side) ----------------

/** Event supports team operations: doubles/mixed + OPEN/FULL + !groupingLocked + not past deadline. */
export const isTeamEvent = (e: Event | null) => {
  if (!e) return false
  if (e.format !== 'DOUBLES' && e.format !== 'MIXED') return false
  if (e.status !== 'OPEN' && e.status !== 'FULL') return false
  if (e.groupingLocked) return false
  if (e.registrationDeadline && new Date(e.registrationDeadline).getTime() < Date.now()) return false
  return true
}

/** 建队：SINGLES 不可建；且当前行所属用户还没组队；OPEN + !groupingLocked + 未过截止。 */
export const canCreateTeam = (
  e: Event | null,
  existingTeam: { status: string } | null | undefined
) => isTeamEvent(e) && !existingTeam

/** 确认入队：当前行是被邀请方 (member2) + 队伍 PENDING + 事件支持组队。 */
export const canConfirmTeam = (
  e: Event | null,
  team: { status: string } | null | undefined
) => isTeamEvent(e) && !!team && team.status === 'PENDING'

/** 拒绝邀请：当前行是被邀请方 + 队伍 PENDING + 事件支持组队。 */
export const canDeclineTeam = (
  e: Event | null,
  team: { status: string } | null | undefined
) => isTeamEvent(e) && !!team && team.status === 'PENDING'

/** 解散队伍：当前行是任一成员 + 队伍 CONFIRMED + 事件未锁定分组。 */
export const canDissolveTeam = (
  e: Event | null,
  team: { status: string } | null | undefined
) => isTeamEvent(e) && !!team && team.status === 'CONFIRMED'
