import { test, expect } from './fixtures/api.fixture'

const BASE = '/api/app'

test.describe('Banners API (public)', () => {
  test('GET /banners returns list', async ({ request }) => {
    const resp = await request.get(`${BASE}/banners`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(Array.isArray(body.data)).toBeTruthy()
  })

  test('banner items have required fields', async ({ request }) => {
    const resp = await request.get(`${BASE}/banners`)
    const body = await resp.json()

    if (body.data.length > 0) {
      const banner = body.data[0]
      expect(banner.id).toBeDefined()
      expect(banner.imageUrl).toBeDefined()
      expect(banner.status).toBeDefined()
    }
  })
})

test.describe('Events API (public)', () => {
  test('GET /events returns paginated list', async ({ request }) => {
    const resp = await request.get(`${BASE}/events`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data).toBeDefined()
    expect(typeof body.data.total).toBe('number')
    expect(Array.isArray(body.data.list)).toBeTruthy()
  })

  test('GET /events with type filter', async ({ request }) => {
    const resp = await request.get(`${BASE}/events?type=STAR&status=OPEN`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    // All returned events should be STAR type
    for (const event of body.data.list) {
      expect(event.type).toBe('STAR')
    }
  })

  test('GET /events pagination works', async ({ request }) => {
    const resp = await request.get(`${BASE}/events?page=1&size=2`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data.list.length).toBeLessThanOrEqual(2)
  })

  test('event items have required fields', async ({ request }) => {
    const resp = await request.get(`${BASE}/events`)
    const body = await resp.json()

    if (body.data.list.length > 0) {
      const event = body.data.list[0]
      expect(event.id).toBeDefined()
      expect(event.title).toBeDefined()
      expect(event.type).toBeDefined()
      expect(event.status).toBeDefined()
      expect(event.currentParticipants).toBeDefined()
      expect(event.maxParticipants).toBeDefined()
    }
  })

  test('GET /events/{id} returns event detail', async ({ request }) => {
    // First get an event ID from the list
    const listResp = await request.get(`${BASE}/events`)
    const listBody = await listResp.json()

    if (listBody.data.list.length === 0) {
      test.skip()
      return
    }

    const eventId = listBody.data.list[0].id
    const resp = await request.get(`${BASE}/events/${eventId}`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data.id).toBe(eventId)
    expect(body.data.title).toBeDefined()
    expect(body.data.description).toBeDefined()
  })

  test('GET /events/{id} with token returns detail', async ({ request }) => {
    const loginResp = await request.post(`${BASE}/auth/login`, {
      data: { code: 'e2e_public_detail' },
    })
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const listResp = await request.get(`${BASE}/events`)
    const listBody = await listResp.json()
    if (listBody.data.list.length === 0) {
      test.skip()
      return
    }

    const eventId = listBody.data.list[0].id
    const resp = await request.get(`${BASE}/events/${eventId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()
    expect(body.code).toBe(0)
    // myRegistrationStatus is absent (non_null serialization) when not registered
    expect(body.data.id).toBe(eventId)
  })
})

test.describe('Rankings API (public)', () => {
  test('GET /rankings returns paginated list', async ({ request }) => {
    const resp = await request.get(`${BASE}/rankings`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(body.data).toBeDefined()
    expect(Array.isArray(body.data.list)).toBeTruthy()
  })

  test('GET /rankings with type and tier filter', async ({ request }) => {
    const resp = await request.get(`${BASE}/rankings?type=STAR&tier=SHINING`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
  })

  test('ranking items have required fields', async ({ request }) => {
    const resp = await request.get(`${BASE}/rankings`)
    const body = await resp.json()

    if (body.data.list.length > 0) {
      const ranking = body.data.list[0]
      expect(ranking.rank).toBeDefined()
      expect(ranking.nickname).toBeDefined()
      expect(ranking.points).toBeDefined()
      expect(ranking.tier).toBeDefined()
    }
  })

  test('GET /rankings/top5 returns list', async ({ request }) => {
    const resp = await request.get(`${BASE}/rankings/top5?type=STAR`)
    expect(resp.ok()).toBeTruthy()
    const body = await resp.json()

    expect(body.code).toBe(0)
    expect(Array.isArray(body.data)).toBeTruthy()
    expect(body.data.length).toBeLessThanOrEqual(5)
  })
})
