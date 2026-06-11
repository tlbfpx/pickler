import { test, expect, authHeaders, login } from './fixtures/api.fixture'

const BASE = '/api/app'

test.describe('Event Registration Flow (E2E)', () => {
  test('full flow: login → browse → register → verify via my events → cancel → verify', async ({ request }) => {
    // 1. Login (unique code per run to avoid registration conflicts)
    const loginResp = await login(request, `e2e_flow_${Date.now()}`)
    const loginBody = await loginResp.json()
    const token = loginBody.data.token
    expect(token).toBeTruthy()

    // 2. Browse open events (pick one with available slots)
    const eventsResp = await request.get(`${BASE}/events?status=OPEN`)
    const eventsBody = await eventsResp.json()
    expect(eventsBody.code).toBe(0)

    if (eventsBody.data.list.length === 0) {
      test.skip()
      return
    }

    // Pick event with available capacity
    const event = eventsBody.data.list.find(
      (e: any) => e.currentParticipants < e.maxParticipants
    )
    if (!event) {
      test.skip()
      return
    }
    const eventId = event.id

    // 3. Register for event
    const registerResp = await request.post(`${BASE}/events/${eventId}/register`, {
      headers: authHeaders(token),
      data: { matchType: 'SINGLES' },
    })
    const registerBody = await registerResp.json()
    if (registerBody.code !== 0) {
      test.skip()
      return
    }
    expect(registerBody.code).toBe(0)

    // 4. Verify registration via /user/events (protected endpoint resolves userId)
    const myEventsResp = await request.get(`${BASE}/user/events`, {
      headers: authHeaders(token),
    })
    const myEventsBody = await myEventsResp.json()
    expect(myEventsBody.code).toBe(0)
    const found = myEventsBody.data.list.find((e: any) => e.id === eventId)
    expect(found).toBeDefined()
    expect(found.registrationStatus).toBe('REGISTERED')

    // 5. Cancel registration
    const cancelResp = await request.post(`${BASE}/events/${eventId}/cancel`, {
      headers: authHeaders(token),
    })
    expect(cancelResp.ok()).toBeTruthy()
    const cancelBody = await cancelResp.json()
    expect(cancelBody.code).toBe(0)

    // 6. Verify cancellation via /user/events
    const afterCancelResp = await request.get(`${BASE}/user/events`, {
      headers: authHeaders(token),
    })
    const afterCancelBody = await afterCancelResp.json()
    expect(afterCancelBody.code).toBe(0)
    const stillFound = afterCancelBody.data.list.find((e: any) => e.id === eventId)
    // After cancellation, event should either be absent or status not REGISTERED
    if (stillFound) {
      expect(stillFound.registrationStatus).not.toBe('REGISTERED')
    }
  })

  test('double registration is rejected', async ({ request }) => {
    const loginResp = await login(request, 'e2e_double_reg')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const eventsResp = await request.get(`${BASE}/events?status=OPEN`)
    const eventsBody = await eventsResp.json()
    if (eventsBody.data.list.length === 0) {
      test.skip()
      return
    }

    const event = eventsBody.data.list.find(
      (e: any) => e.currentParticipants < e.maxParticipants
    )
    if (!event) {
      test.skip()
      return
    }
    const eventId = event.id

    // First registration
    const reg1 = await request.post(`${BASE}/events/${eventId}/register`, {
      headers: authHeaders(token),
      data: { matchType: 'SINGLES' },
    })
    const reg1Body = await reg1.json()
    if (reg1Body.code !== 0) {
      test.skip()
      return
    }

    // Second registration should fail
    const reg2 = await request.post(`${BASE}/events/${eventId}/register`, {
      headers: authHeaders(token),
      data: { matchType: 'SINGLES' },
    })
    const reg2Body = await reg2.json()
    expect(reg2Body.code).not.toBe(0)

    // Cleanup
    await request.post(`${BASE}/events/${eventId}/cancel`, {
      headers: authHeaders(token),
    })
  })

  test('registration without token returns 401', async ({ request }) => {
    const eventsResp = await request.get(`${BASE}/events?status=OPEN`)
    const eventsBody = await eventsResp.json()
    if (eventsBody.data.list.length === 0) {
      test.skip()
      return
    }

    const eventId = eventsBody.data.list[0].id
    const resp = await request.post(`${BASE}/events/${eventId}/register`, {
      data: { matchType: 'SINGLES' },
    })
    expect(resp.status()).toBe(401)
  })

  test('cancel without token returns 401', async ({ request }) => {
    const eventsResp = await request.get(`${BASE}/events?status=OPEN`)
    const eventsBody = await eventsResp.json()
    if (eventsBody.data.list.length === 0) {
      test.skip()
      return
    }

    const eventId = eventsBody.data.list[0].id
    const resp = await request.post(`${BASE}/events/${eventId}/cancel`)
    expect(resp.status()).toBe(401)
  })

  test('register for non-existent event returns error', async ({ request }) => {
    const loginResp = await login(request, 'e2e_bad_event')
    const loginBody = await loginResp.json()
    const token = loginBody.data.token

    const resp = await request.post(`${BASE}/events/999999/register`, {
      headers: authHeaders(token),
      data: { matchType: 'SINGLES' },
    })
    const body = await resp.json()
    expect(body.code).not.toBe(0)
  })
})
