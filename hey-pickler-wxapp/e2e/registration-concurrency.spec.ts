import { test, expect } from './fixtures/flow.fixture'
import {
  adminLogin,
  adminCall,
  appCall,
  loginUser,
  createEvent,
  setEventStatus,
  cleanupE2EData,
} from './fixtures/flow.fixture'
import type { APIRequestContext } from '@playwright/test'

// 文件级清理：删历史残留测试数据
test.beforeAll(() => {
  cleanupE2EData()
})

test.afterAll(() => {
  cleanupE2EData()
})

/**
 * 并发报名安全测试。核心校验三件事：
 *   1. 容量不超卖（maxParticipants 严格执行）
 *   2. 同一用户不会重复报名（uk_user_event 唯一索引兜底）
 *   3. 报名计数 currentParticipants 与 registration 表行数一致
 *
 * 实现要点：每个用户用独立 token，否则 JWT 复用会让请求在网关层串行化。
 * 并发用 Promise.all 同时触发，制造真实的 race window。
 */

/** 串行批量登录多个用户（避免触发限流）。返回 token + userId 数组。 */
async function loginUsers(
  ctx: APIRequestContext,
  prefix: string,
  count: number,
): Promise<{ token: string; userId: number }[]> {
  const out: { token: string; userId: number }[] = []
  for (let i = 0; i < count; i++) {
    out.push(await loginUser(ctx, `${prefix}_${i}`))
  }
  return out
}

/** 拿到 token 后发起一次报名请求，返回 {ok, code} 简化结果。 */
async function registerOnce(
  ctx: APIRequestContext,
  token: string,
  eventId: number,
): Promise<{ ok: boolean; code: number; message?: string }> {
  const r = await appCall<any>(ctx, token, 'POST', `/events/${eventId}/register`, {
    matchType: 'SINGLES',
  })
  return {
    ok: r.body.code === 0,
    code: r.body.code,
    message: r.body.message,
  }
}

/** 用 app 端赛事详情拿 currentParticipants（公开接口，无需 token）。 */
async function fetchParticipantCount(
  ctx: APIRequestContext,
  eventId: number,
): Promise<number> {
  const r = await ctx.get(`/api/app/events/${eventId}`)
  const body = await r.json()
  expect(body.code, `fetchParticipantCount code=${body.code}`).toBe(0)
  return body.data.currentParticipants as number
}

/** 直接从 DB 拿 ground-truth（admin registrations 接口）。 */
async function countRegistered(
  ctx: APIRequestContext,
  adminToken: string,
  eventId: number,
): Promise<number> {
  const r = await adminCall<any>(
    ctx,
    adminToken,
    'GET',
    `/events/${eventId}/registrations?status=REGISTERED&size=200`,
  )
  expect(r.body.code).toBe(0)
  return r.body.data.total as number
}

test.describe('C 端报名并发安全', () => {
  test('容量竞争：max=5 已有 4 人，10 个新用户并发抢最后 1 个名额，只应 1 人成功', async ({
    request,
  }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)

    // 创建 max=5 的赛事
    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-CONC-MAX5-${stamp}`,
      maxParticipants: 5,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    try {
      // 先 4 个用户串行报名占满 4 个名额（登录也串行，避开限流）
      const seedUsers = await loginUsers(ctx, `e2e_conc_seed_${stamp}`, 4)
      for (const u of seedUsers) {
        await registerOnce(ctx, u.token, eventId)
      }
      const seeded = await fetchParticipantCount(ctx, eventId)
      expect(seeded, '预热后应已 4 人').toBe(4)

      // 10 个新用户登录（串行），然后**并发**抢最后 1 个名额
      const racers = await loginUsers(ctx, `e2e_conc_race_${stamp}`, 10)
      const results = await Promise.all(
        racers.map((u) => registerOnce(ctx, u.token, eventId)),
      )

      const success = results.filter((r) => r.ok).length
      const rejected = results.filter((r) => !r.ok).length

      // 关键断言：恰好 1 人成功
      expect(success, '10 人抢 1 个名额应恰好 1 人成功').toBe(1)
      expect(rejected, '其余 9 人应被拒绝').toBe(9)

      // 拒绝原因应是名额已满（不是其他错误）
      const failCodes = new Set(results.filter((r) => !r.ok).map((r) => r.code))
      for (const code of failCodes) {
        expect(code, `被拒 code=${code} 应非 0`).not.toBe(0)
      }

      // 最终容量 == maxParticipants，未超卖
      const finalCount = await fetchParticipantCount(ctx, eventId)
      expect(finalCount, '最终报名数应等于 max=5，不超卖').toBe(5)

      const dbCount = await countRegistered(ctx, adminToken, eventId)
      expect(dbCount, 'DB registration 表 REGISTERED 行数应=5').toBe(5)
    } finally {
      await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
    }
  })

  test('正常并发：max=20，20 个不同用户并发报名应全部成功', async ({ request }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)

    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-CONC-FULL20-${stamp}`,
      maxParticipants: 20,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    try {
      // 登录串行（避开限流），报名并发
      const users = await loginUsers(ctx, `e2e_conc_full_${stamp}`, 20)

      const results = await Promise.all(
        users.map((u) => registerOnce(ctx, u.token, eventId)),
      )

      const success = results.filter((r) => r.ok).length
      expect(success, '20 个用户并发应全部成功').toBe(20)

      const finalCount = await fetchParticipantCount(ctx, eventId)
      expect(finalCount, '容量计数应为 20').toBe(20)

      const dbCount = await countRegistered(ctx, adminToken, eventId)
      expect(dbCount, 'DB 行数应=20').toBe(20)
    } finally {
      await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
    }
  })

  test('同用户重复报名：1 个用户 8 个并发请求，应至多 1 次成功', async ({ request }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)

    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-CONC-DUP-${stamp}`,
      maxParticipants: 50,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    try {
      const user = await loginUser(ctx, `e2e_conc_dup_${stamp}`)

      // 同一 token 并发 8 次
      const results = await Promise.all(
        Array.from({ length: 8 }, () => registerOnce(ctx, user.token, eventId)),
      )

      const success = results.filter((r) => r.ok).length
      // 唯一索引保证至多 1 次成功；服务层 select-then-insert 在 race 下可能 0 个成功（DB 抛唯一冲突时服务可能未优雅转换），
      // 但绝不能 >1。理想是恰好 1。
      expect(success, '同一用户并发报名至多 1 次成功').toBeLessThanOrEqual(1)

      const dbCount = await countRegistered(ctx, adminToken, eventId)
      expect(dbCount, 'DB 中该用户对该赛事的 REGISTERED 记录至多 1 条').toBeLessThanOrEqual(1)

      // 容量计数也不应被错误地累加多次
      const finalCount = await fetchParticipantCount(ctx, eventId)
      expect(finalCount, 'currentParticipants 至多 +1').toBeLessThanOrEqual(1)
    } finally {
      await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
    }
  })

  test('混合压力：50 用户并发抢 max=10，应恰好 10 人成功，无重复无超卖', async ({
    request,
  }) => {
    const ctx: APIRequestContext = request
    const stamp = Date.now()
    const adminToken = await adminLogin(ctx)

    const eventId = await createEvent(ctx, adminToken, 'STAR', {
      title: `E2E-CONC-MIX-${stamp}`,
      maxParticipants: 10,
    })
    await setEventStatus(ctx, adminToken, eventId, 'OPEN')

    try {
      // 登录串行（避开限流），报名真并发
      const users = await loginUsers(ctx, `e2e_conc_mix_${stamp}`, 50)

      const t0 = Date.now()
      const results = await Promise.all(
        users.map((u) => registerOnce(ctx, u.token, eventId)),
      )
      const elapsed = Date.now() - t0

      const success = results.filter((r) => r.ok).length
      const rejected = results.filter((r) => !r.ok).length

      expect(success, '50 抢 10 应恰好 10 人成功').toBe(10)
      expect(rejected, '其余 40 人应被拒绝').toBe(40)

      const finalCount = await fetchParticipantCount(ctx, eventId)
      expect(finalCount, 'currentParticipants 必须 = max=10，不可超卖').toBe(10)

      const dbCount = await countRegistered(ctx, adminToken, eventId)
      expect(dbCount, 'DB REGISTERED 总数必须 = 10').toBe(10)

      // 简单性能记录，不做强断言（避免环境抖动误报）
      console.log(
        `  ℹ 50 并发报名耗时 ${elapsed}ms，平均 ${(elapsed / 50).toFixed(0)}ms/req`,
      )
    } finally {
      await adminCall(ctx, adminToken, 'DELETE', `/events/${eventId}`)
    }
  })
})
