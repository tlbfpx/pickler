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

// 全文件级清理：跑完一堆历史残留后，确保 DB 干净，断言才稳
test.beforeAll(() => {
  cleanupE2EData()
})

test.afterAll(() => {
  cleanupE2EData()
})

/**
 * STAR 赛事全流程：管理员创建 → 用户报名 → 状态推进到 COMPLETED → 录入积分 →
 * 校验成绩榜、用户积分、排名表 全部一致。
 *
 * 数据隔离策略：每次运行创建新赛事 + 新用户（wx dev mode：code 不同即新用户），
 * 不依赖任何预置数据，不影响其他测试。
 */
test.describe('STAR 赛事全流程', () => {
  test('创建→报名→完结→录入积分→成绩/排名一致', async ({ request }) => {
    const ctx: APIRequestContext = request

    const stamp = Date.now()
    const eventTitle = `E2E-STAR-${stamp}`

    // ───────── 1. Admin 创建 STAR 赛事 (DRAFT) ─────────
    const adminToken = await adminLogin(ctx)
    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: eventTitle,
      maxParticipants: 20,
    })

    // ───────── 2. 推进到 OPEN ─────────
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    // ───────── 3. 三个用户 dev-mode 登录 + 记录积分基线 ─────────
    const userA = await loginUser(ctx, `e2e_star_a_${stamp}`)
    const userB = await loginUser(ctx, `e2e_star_b_${stamp}`)
    const userC = await loginUser(ctx, `e2e_star_c_${stamp}`)

    const profileBefore = async (u: { token: string }) => {
      const r = await appCall<any>(ctx, u.token, 'GET', '/user/profile')
      expect(r.body.code).toBe(0)
      return r.body.data
    }
    const [aBefore, bBefore, cBefore] = await Promise.all([
      profileBefore(userA),
      profileBefore(userB),
      profileBefore(userC),
    ])
    // 新用户基线必须为 0，否则后续累加断言不可信
    expect(aBefore.starPoints, '新用户 A starPoints 基线').toBe(0)
    expect(bBefore.starPoints).toBe(0)
    expect(cBefore.starPoints).toBe(0)
    expect(aBefore.starTier).toBe('SHINING')

    // ───────── 4. 三人报名 ─────────
    await registerForEvent(ctx, userA.token, eventId, 'SINGLES')
    await registerForEvent(ctx, userB.token, eventId, 'SINGLES')
    await registerForEvent(ctx, userC.token, eventId, 'SINGLES')

    // ───────── 5. 校验：admin 端参赛者列表含三人 ─────────
    const participantsRes = await adminCall<any[]>(
      ctx,
      adminToken,
      'GET',
      `/events/${eventId}/participants`,
    )
    expect(participantsRes.body.code).toBe(0)
    const participantIds = participantsRes.body.data.map((p) => p.userId).sort()
    expect(participantIds).toEqual(
      [userA.userId, userB.userId, userC.userId].sort(),
    )
    // 报名状态都应为 REGISTERED
    for (const p of participantsRes.body.data) {
      expect(p.registrationStatus).toBe('REGISTERED')
    }

    // ───────── 6. 状态推进 OPEN → IN_PROGRESS → COMPLETED ─────────
    await setEventStatus(ctx, adminToken, eventId, 'IN_PROGRESS')
    await setEventStatus(ctx, adminToken, eventId, 'COMPLETED')

    // 校验赛事最终状态
    const eventDetailRes = await appCall<any>(
      ctx,
      userA.token,
      'GET',
      `/events/${eventId}`,
    )
    expect(eventDetailRes.body.code).toBe(0)
    expect(eventDetailRes.body.data.status).toBe('COMPLETED')
    expect(eventDetailRes.body.data.currentParticipants).toBe(3)

    // ───────── 7. Admin 录入积分：A=100 B=60 C=40 ─────────
    const enterRes = await adminCall(
      ctx,
      adminToken,
      'POST',
      `/events/${eventId}/points`,
      {
        eventId,
        type: 'STAR',
        records: [
          { userId: userA.userId, points: 100, reason: '冠军' },
          { userId: userB.userId, points: 60, reason: '亚军' },
          { userId: userC.userId, points: 40, reason: '季军' },
        ],
      },
    )
    expect(enterRes.status, `enterPoints HTTP ${enterRes.status}`).toBeLessThan(
      300,
    )
    expect(enterRes.body.code, `enterPoints code=${enterRes.body.code}`).toBe(0)

    // PointChangeListener 是 @Async(AFTER_COMMIT)，给排名刷新一点时间；
    // 同时手动触发一次同步刷新确保后续断言可见
    const refreshRes = await adminCall(
      ctx,
      adminToken,
      'POST',
      '/rankings/refresh',
      { type: 'STAR' },
    )
    expect(refreshRes.body.code, `manual refresh code=${refreshRes.body.code}`).toBe(0)
    await new Promise((r) => setTimeout(r, 800))

    // ───────── 8a. 校验：app 端赛事成绩榜 ─────────
    const resultsRes = await appCall<any[]>(
      ctx,
      userA.token,
      'GET',
      `/events/${eventId}/results`,
    )
    expect(resultsRes.body.code).toBe(0)
    const results = resultsRes.body.data
    expect(results.length, '成绩榜应含 3 人').toBe(3)
    // 按积分降序：A=100, B=60, C=40
    expect(results[0].userId).toBe(userA.userId)
    expect(results[0].points).toBe(100)
    expect(results[0].rank).toBe(1)
    expect(results[1].userId).toBe(userB.userId)
    expect(results[1].points).toBe(60)
    expect(results[1].rank).toBe(2)
    expect(results[2].userId).toBe(userC.userId)
    expect(results[2].points).toBe(40)
    expect(results[2].rank).toBe(3)

    // ───────── 8b. 校验：用户积分已累加到 profile ─────────
    const [aAfter, bAfter, cAfter] = await Promise.all([
      profileBefore(userA),
      profileBefore(userB),
      profileBefore(userC),
    ])
    expect(aAfter.starPoints).toBe(aBefore.starPoints + 100)
    expect(bAfter.starPoints).toBe(bBefore.starPoints + 60)
    expect(cAfter.starPoints).toBe(cBefore.starPoints + 40)
    // partyPoints 不受 STAR 赛事影响
    expect(aAfter.partyPoints).toBe(0)
    expect(bAfter.partyPoints).toBe(0)
    expect(cAfter.partyPoints).toBe(0)

    // ───────── 8c. 校验：point_record 落库（通过 user 端积分历史）─────────
    const historyA = await appCall<any>(
      ctx,
      userA.token,
      'GET',
      `/user/points?type=STAR`,
    )
    expect(historyA.body.code).toBe(0)
    const aRecord = historyA.body.data.list.find(
      (r: any) => r.eventId === eventId,
    )
    expect(aRecord, 'A 的 point_record 应已落库').toBeTruthy()
    expect(aRecord.points).toBe(100)
    expect(aRecord.type).toBe('STAR')

    // ───────── 8d. 校验：排名榜（admin 端，不走 cache 最稳）─────────
    const rankRes = await adminCall<any>(ctx, adminToken, 'GET', '/rankings/STAR')
    expect(rankRes.body.code).toBe(0)
    const rankingList = rankRes.body.data.list as any[]
    const aRank = rankingList.find((r) => r.userId === userA.userId)
    const bRank = rankingList.find((r) => r.userId === userB.userId)
    const cRank = rankingList.find((r) => r.userId === userC.userId)
    expect(aRank, 'A 应在 STAR 排名榜').toBeTruthy()
    expect(bRank, 'B 应在 STAR 排名榜').toBeTruthy()
    expect(cRank, 'C 应在 STAR 排名榜').toBeTruthy()
    // 三人积分关系：A > B > C
    expect(aRank.points).toBeGreaterThan(bRank.points)
    expect(bRank.points).toBeGreaterThan(cRank.points)
    // rank 连续
    expect(aRank.rank).toBeLessThan(bRank.rank)
    expect(bRank.rank).toBeLessThan(cRank.rank)

    // ───────── 9. 清理：删除赛事（逻辑删除），不影响后续运行 ─────────
    const delRes = await adminCall(
      ctx,
      adminToken,
      'DELETE',
      `/events/${eventId}`,
    )
    expect(delRes.body.code).toBe(0)

  })

  test('赛事未录入积分时，成绩榜列出参赛者且积分均为 0', async ({ request }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)
    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-STAR-EMPTY-${stamp}`,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    const user = await loginUser(ctx, `e2e_star_empty_${stamp}`)
    await registerForEvent(ctx, user.token, eventId)

    await setEventStatus(ctx, adminToken, eventId, 'IN_PROGRESS')
    await setEventStatus(ctx, adminToken, eventId, 'COMPLETED')

    const resultsRes = await appCall<any[]>(
      ctx,
      user.token,
      'GET',
      `/events/${eventId}/results`,
    )
    expect(resultsRes.body.code).toBe(0)
    expect(resultsRes.body.data.length).toBe(1)
    expect(resultsRes.body.data[0].userId).toBe(user.userId)
    expect(resultsRes.body.data[0].points).toBe(0)
    expect(resultsRes.body.data[0].rank).toBe(1)

    // 清理
    await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
  })

  test('同赛事同分用户共享排名（dense rank）', async ({ request }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)
    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-STAR-TIE-${stamp}`,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    const u1 = await loginUser(ctx, `e2e_star_tie1_${stamp}`)
    const u2 = await loginUser(ctx, `e2e_star_tie2_${stamp}`)
    const u3 = await loginUser(ctx, `e2e_star_tie3_${stamp}`)

    await registerForEvent(ctx, u1.token, eventId)
    await registerForEvent(ctx, u2.token, eventId)
    await registerForEvent(ctx, u3.token, eventId)

    await setEventStatus(ctx, adminToken, eventId, 'IN_PROGRESS')
    await setEventStatus(ctx, adminToken, eventId, 'COMPLETED')

    // 两人同分 50，一人 30
    await adminCall(ctx, adminToken, 'POST', `/events/${eventId}/points`, {
      eventId,
      records: [
        { userId: u1.userId, points: 50, reason: '并列' },
        { userId: u2.userId, points: 50, reason: '并列' },
        { userId: u3.userId, points: 30, reason: '落后' },
      ],
    })

    const resultsRes = await appCall<any[]>(
      ctx,
      u1.token,
      'GET',
      `/events/${eventId}/results`,
    )
    const results = resultsRes.body.data
    // 排序后前两人 rank=1，第三人 rank=2（dense rank）
    const r1 = results.find((r) => r.userId === u1.userId)
    const r2 = results.find((r) => r.userId === u2.userId)
    const r3 = results.find((r) => r.userId === u3.userId)
    expect(r1.rank).toBe(1)
    expect(r2.rank).toBe(1)
    expect(r3.rank).toBe(2)

    await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
  })
})
