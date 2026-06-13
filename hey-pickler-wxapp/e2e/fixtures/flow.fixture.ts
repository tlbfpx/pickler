import { test as base, expect, APIRequestContext } from '@playwright/test'
import { execSync } from 'child_process'
import { resolve } from 'path'

export { expect }

const APP_BASE = '/api/app'
const ADMIN_BASE = '/api/admin'

const ADMIN_USERNAME = process.env.ADMIN_USERNAME || 'admin'
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123'

/**
 * 清掉所有 E2E 测试残留数据（dev_e2e_* 用户 + E2E-* 赛事及其关联记录）。
 *
 * 为啥需要：每次跑 E2E 都会创建用户/赛事，跑久了 DB 里堆上千个测试用户，
 * 它们带积分、在排名榜里，会让后续测试的预期越来越脆。
 *
 * 用法：在测试文件的 beforeAll 里调用一次。
 *  - 找不到 mysql 或 sql 文件时静默跳过（不阻塞测试）
 *  - 默认走 monorepo 根目录的 scripts/cleanup-e2e-data.sql
 */
export function cleanupE2EData(): void {
  const scriptPath = resolve(__dirname, '../../../scripts/cleanup-e2e-data.sql')
  const dbUrl = process.env.DATABASE_URL || 'mysql://root:root@localhost/hey_pickler'
  try {
    execSync(`mysql -u root -proot hey_pickler < ${scriptPath}`, {
      stdio: 'ignore',
    })
  } catch (e) {
    // 静默：mysql 不在 PATH / DB 凭据不对 / sql 文件不存在时直接跳过
    // 测试仍可运行，只是不保证 DB 干净
    void dbUrl
    void e
  }
}

/** Result of an API call: HTTP status + decoded JSON body. */
export type ApiResult<T = any> = { status: number; body: T }

/** Helper for calling admin endpoints. Token is required at every call site. */
export async function adminCall<T = any>(
  request: APIRequestContext,
  token: string,
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  data?: unknown,
): Promise<ApiResult<T>> {
  const resp = await request.fetch(`${ADMIN_BASE}${path}`, {
    method,
    data: data as any,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  })
  return { status: resp.status(), body: await decodeBody<T>(resp) }
}

/** Helper for calling app endpoints. Token is required at every call site. */
export async function appCall<T = any>(
  request: APIRequestContext,
  token: string,
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  data?: unknown,
): Promise<ApiResult<T>> {
  const resp = await request.fetch(`${APP_BASE}${path}`, {
    method,
    data: data as any,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  })
  return { status: resp.status(), body: await decodeBody<T>(resp) }
}

async function decodeBody<T>(resp: any): Promise<T> {
  const text = await resp.text()
  if (!text) return null as any
  try {
    return JSON.parse(text) as T
  } catch {
    return text as unknown as T
  }
}

/** Login as admin, return bearer token. */
export async function adminLogin(
  request: APIRequestContext,
): Promise<string> {
  const resp = await request.post(`${ADMIN_BASE}/auth/login`, {
    data: { username: ADMIN_USERNAME, password: ADMIN_PASSWORD },
  })
  expect(resp.ok(), `admin login HTTP ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  expect(body.code, `admin login code=${body.code}`).toBe(0)
  expect(body.data.token).toBeTruthy()
  return body.data.token as string
}

/** Login a fresh wxapp user (dev mode: distinct code → distinct user). */
export async function loginUser(
  request: APIRequestContext,
  code: string,
): Promise<{ token: string; userId: number }> {
  const resp = await request.post(`${APP_BASE}/auth/login`, { data: { code } })
  expect(resp.ok(), `app login HTTP ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  expect(body.code, `app login code=${body.code} for ${code}`).toBe(0)
  return {
    token: body.data.token as string,
    userId: body.data.user?.id as number,
  }
}

/** Common API envelope — all backend responses follow this shape. */
export interface ApiEnvelope<T> {
  code: number
  message?: string
  data: T
}

/** Create an event via admin API. Returns the new event id. */
export async function createEvent(
  request: APIRequestContext,
  adminToken: string,
  type: 'STAR' | 'PARTY',
  overrides: Partial<{
    title: string
    description: string
    location: string
    eventTime: string
    registrationDeadline: string
    maxParticipants: number
    status: string
  }> = {},
): Promise<number> {
  const stamp = Date.now()
  const baseBody = {
    type,
    title: overrides.title ?? `E2E-${type}-${stamp}`,
    description: overrides.description ?? 'Playwright E2E 全流程测试赛事',
    location: overrides.location ?? 'E2E-测试球馆',
    eventTime: overrides.eventTime ?? '2026-09-01T10:00:00',
    registrationDeadline:
      overrides.registrationDeadline ?? '2026-08-25T18:00:00',
    maxParticipants: overrides.maxParticipants ?? 50,
    fee: 0,
    status: overrides.status ?? 'DRAFT',
  }
  const { status, body } = await adminCall<{ id: number }>(
    request,
    adminToken,
    'POST',
    '/events',
    baseBody,
  )
  expect(status, `createEvent HTTP ${status}`).toBeLessThan(300)
  expect(body.code).toBe(0)
  expect(body.data.id).toBeTruthy()
  return body.data.id
}

/** Patch event status (DRAFT → OPEN → IN_PROGRESS → COMPLETED). */
export async function setEventStatus(
  request: APIRequestContext,
  adminToken: string,
  eventId: number,
  status: 'DRAFT' | 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED',
): Promise<void> {
  const { status: http, body } = await adminCall(
    request,
    adminToken,
    'PATCH',
    `/events/${eventId}/status`,
    { status },
  )
  expect(http, `setEventStatus(${status}) HTTP ${http}`).toBeLessThan(300)
  expect(body.code).toBe(0)
}

/** Register a wxapp user for an event. Returns registrationStatus string. */
export async function registerForEvent(
  request: APIRequestContext,
  userToken: string,
  eventId: number,
  matchType: 'SINGLES' | 'DOUBLES' | 'MIXED' = 'SINGLES',
): Promise<void> {
  const { status, body } = await appCall(
    request,
    userToken,
    'POST',
    `/events/${eventId}/register`,
    { matchType },
  )
  expect(status, `register HTTP ${status}`).toBeLessThan(300)
  expect(body.code, `register code=${body.code} ${body.message}`).toBe(0)
}

export const test = base
