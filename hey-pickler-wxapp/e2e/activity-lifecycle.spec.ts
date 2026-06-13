import { test, expect } from './fixtures/flow.fixture'
import {
  adminLogin,
  adminCall,
  appCall,
  loginUser,
  createEvent,
  setEventStatus,
  registerForEvent,
  cleanupE2EData,
} from './fixtures/flow.fixture'
import type { APIRequestContext } from '@playwright/test'

test.beforeAll(() => {
  cleanupE2EData()
})

test.afterAll(() => {
  cleanupE2EData()
})

/**
 * PARTY 活动全流程。重点在：
 *  - type=PARTY 的赛事走 partyPoints/partyTier，不影响 starPoints
 *  - partyTier 阈值低（LEGEND≥500, SUPER≥200, SHINING<200），可以覆盖三档
 */
test.describe('PARTY 活动全流程', () => {
  test('创建→报名→完结→录入积分→成绩/排名/tier 全部正确', async ({ request }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)

    // ───────── 1. 创建 PARTY 活动 ─────────
    const eventId = await createEvent(ctx, adminToken, 'PARTY', {
      title: `E2E-PARTY-${stamp}`,
      maxParticipants: 30,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    // ───────── 2. 三用户登录 + 报名 ─────────
    const u1 = await loginUser(ctx, `e2e_party1_${stamp}`)
    const u2 = await loginUser(ctx, `e2e_party2_${stamp}`)
    const u3 = await loginUser(ctx, `e2e_party3_${stamp}`)

    const profileBefore = async (t: string) => {
      const r = await appCall<any>(ctx, t, 'GET', '/user/profile')
      expect(r.body.code).toBe(0)
      return r.body.data
    }
    const [p1, p2, p3] = await Promise.all([
      profileBefore(u1.token),
      profileBefore(u2.token),
      profileBefore(u3.token),
    ])
    expect(p1.partyPoints, '新用户 partyPoints 基线').toBe(0)
    expect(p1.starPoints).toBe(0)
    expect(p1.partyTier).toBe('SHINING')

    await registerForEvent(ctx, u1.token, eventId, 'SINGLES')
    await registerForEvent(ctx, u2.token, eventId, 'SINGLES')
    await registerForEvent(ctx, u3.token, eventId, 'SINGLES')

    // ───────── 3. 状态推进到 COMPLETED ─────────
    await setEventStatus(ctx, adminToken, eventId, 'IN_PROGRESS')
    await setEventStatus(ctx, adminToken, eventId, 'COMPLETED')

    // ───────── 4. 录入积分：u1=550 (LEGEND), u2=250 (SUPER), u3=50 (SHINING) ─────────
    const enter = await adminCall(
      ctx,
      adminToken,
      'POST',
      `/events/${eventId}/points`,
      {
        eventId,
        records: [
          { userId: u1.userId, points: 550, reason: '活动冠军' },
          { userId: u2.userId, points: 250, reason: '活动亚军' },
          { userId: u3.userId, points: 50, reason: '参与奖' },
        ],
      },
    )
    expect(enter.body.code).toBe(0)

    // 显式同步刷新（保险），同时给异步监听器一点时间
    const refreshRes = await adminCall(
      ctx,
      adminToken,
      'POST',
      '/rankings/refresh',
      { type: 'PARTY' },
    )
    expect(refreshRes.body.code, `manual refresh code=${refreshRes.body.code}`).toBe(0)
    await new Promise((r) => setTimeout(r, 800))

    // ───────── 5a. 成绩榜 ─────────
    const resultsRes = await appCall<any[]>(
      ctx,
      u1.token,
      'GET',
      `/events/${eventId}/results`,
    )
    const results = resultsRes.body.data
    expect(results.length).toBe(3)
    expect(results[0]).toMatchObject({ userId: u1.userId, points: 550, rank: 1 })
    expect(results[1]).toMatchObject({ userId: u2.userId, points: 250, rank: 2 })
    expect(results[2]).toMatchObject({ userId: u3.userId, points: 50, rank: 3 })

    // ───────── 5b. 用户 partyPoints/tier ─────────
    const [a1, a2, a3] = await Promise.all([
      profileBefore(u1.token),
      profileBefore(u2.token),
      profileBefore(u3.token),
    ])
    expect(a1.partyPoints).toBe(p1.partyPoints + 550)
    expect(a2.partyPoints).toBe(p2.partyPoints + 250)
    expect(a3.partyPoints).toBe(p3.partyPoints + 50)
    expect(a1.partyTier, '550 分应为 LEGEND').toBe('LEGEND')
    expect(a2.partyTier, '250 分应为 SUPER').toBe('SUPER')
    expect(a3.partyTier, '50 分应为 SHINING').toBe('SHINING')
    // starPoints 不受 PARTY 影响
    expect(a1.starPoints).toBe(0)
    expect(a2.starPoints).toBe(0)
    expect(a3.starPoints).toBe(0)

    // ───────── 5c. 排名榜（admin 端，size=100，覆盖所有积分用户）─────────
    const rankRes = await adminCall<any>(
      ctx,
      adminToken,
      'GET',
      '/rankings/PARTY',
    )
    const rankings = rankRes.body.data.list as any[]
    const r1 = rankings.find((r) => r.userId === u1.userId)
    const r2 = rankings.find((r) => r.userId === u2.userId)
    const r3 = rankings.find((r) => r.userId === u3.userId)
    expect(r1, 'u1 应在 PARTY 排名榜').toBeTruthy()
    expect(r2, 'u2 应在 PARTY 排名榜').toBeTruthy()
    expect(r3, 'u3 应在 PARTY 排名榜').toBeTruthy()
    expect(r1.tier).toBe('LEGEND')
    expect(r2.tier).toBe('SUPER')
    expect(r3.tier).toBe('SHINING')
    expect(r1.rank).toBeLessThan(r2.rank)
    expect(r2.rank).toBeLessThan(r3.rank)

    // ───────── 6. 清理 ─────────
    await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
  })
})
