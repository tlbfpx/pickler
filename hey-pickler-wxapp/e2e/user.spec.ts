import { test, expect, authHeaders, login } from './fixtures/api.fixture'

const BASE = '/api/app'

test.describe('User API (protected)', () => {
  test('GET /user/profile returns profile with token', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_profile')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const resp = await request.get(`${BASE}/user/profile`, {
      headers: authHeaders(token),
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data.id).toBeDefined()
    expect(body.data.starPoints).toBeDefined()
    expect(body.data.partyPoints).toBeDefined()
    expect(body.data.starTier).toBeDefined()
    expect(body.data.partyTier).toBeDefined()
  })

  test('GET /user/profile without token returns 401', async ({ request }) => {
    const resp = await request.get(`${BASE}/user/profile`)
    expect(resp.status()).toBe(401)
  })

  test('PUT /user/profile updates nickname', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_update')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const nickname = `测试昵称_${Date.now()}`
    const resp = await request.put(`${BASE}/user/profile`, {
      headers: authHeaders(token),
      data: { nickname },
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()
    expect(body.code).toBe(0)

    // Verify update took effect
    const profileResp = await request.get(`${BASE}/user/profile`, {
      headers: authHeaders(token),
    })
    const profileBody = await profileResp.json()
    expect(profileBody.data.nickname).toBe(nickname)
  })

  test('PUT /user/profile updates city', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_city')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const city = `上海_${Date.now()}`
    const resp = await request.put(`${BASE}/user/profile`, {
      headers: authHeaders(token),
      data: { city },
    })
    expect(resp.ok()).toBeTruthy()

    const profileResp = await request.get(`${BASE}/user/profile`, {
      headers: authHeaders(token),
    })
    const profileBody = await profileResp.json()
    expect(profileBody.data.city).toBe(city)
  })

  test('GET /user/events returns my events', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_events')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const resp = await request.get(`${BASE}/user/events`, {
      headers: authHeaders(token),
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data).toBeDefined()
    expect(Array.isArray(body.data.list)).toBeTruthy()
  })

  test('GET /user/events with type filter', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_events_filter')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const resp = await request.get(`${BASE}/user/events?type=STAR`, {
      headers: authHeaders(token),
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()
    expect(body.code).toBe(0)
  })

  test('GET /user/events without token returns 401', async ({ request }) => {
    const resp = await request.get(`${BASE}/user/events`)
    expect(resp.status()).toBe(401)
  })

  test('GET /user/points returns point history', async ({ request }) => {
    const loginResp = await login(request, 'e2e_user_points')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const resp = await request.get(`${BASE}/user/points`, {
      headers: authHeaders(token),
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data).toBeDefined()
    expect(Array.isArray(body.data.list)).toBeTruthy()
  })

  test('GET /user/points without token returns 401', async ({ request }) => {
    const resp = await request.get(`${BASE}/user/points`)
    expect(resp.status()).toBe(401)
  })
})
