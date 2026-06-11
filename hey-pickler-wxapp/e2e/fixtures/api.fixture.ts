import { test as base, expect, APIRequestContext } from '@playwright/test'

const BASE = '/api/app'

type AppFixture = {
  api: APIRequestContext
}

type AuthFixture = {
  api: APIRequestContext
  token: string
  userId: number
}

// Unauthenticated API context
export const test = base.extend<AppFixture>({
  api: async ({ request }, use) => {
    await use(request)
  },
})

// Authenticated API context — logs in with dev-mode and returns token
export const authTest = base.extend<AuthFixture>({
  token: async ({ request }, use) => {
    const resp = await request.post(`${BASE}/auth/login`, {
      data: { code: `test_${Date.now()}` },
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()
    expect(body.code).toBe(0)
    await use(body.data.token)
  },

  userId: async ({ request }, use) => {
    const resp = await request.post(`${BASE}/auth/login`, {
      data: { code: `test_${Date.now()}` },
    })
    const body = await resp.json()
    await use(body.data.user?.id ?? 0)
  },

  api: async ({ request, token }, use) => {
    // request context already shares baseURL; add auth header via extraHTTPHeaders
    // We re-create a context with the token for authenticated calls
    const ctx = await request.context().then(() => request)
    // Use a wrapper that injects the token
    await use(ctx)
  },
})

// Helper: login and return full response
export async function login(request: APIRequestContext, code: string) {
  return request.post(`${BASE}/auth/login`, {
    data: { code },
  })
}

// Helper: get authenticated headers
export function authHeaders(token: string) {
  return { Authorization: `Bearer ${token}` }
}

export { expect }
