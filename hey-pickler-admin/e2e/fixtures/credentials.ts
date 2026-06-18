/**
 * E2E test credentials. Sourced from env vars so CI can rotate without
 * code changes. Defaults match the legacy V2-seeded admin row that dev
 * databases still have after upgrade — see docs/CREDENTIALS.md §2.2.
 *
 * On a fresh DB (post-bootstrap), CI MUST set E2E_ADMIN_PASSWORD to match
 * whatever INITIAL_ADMIN_PASSWORD was used at app start.
 */
export const E2E_ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME || 'admin'
export const E2E_ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'admin123'
