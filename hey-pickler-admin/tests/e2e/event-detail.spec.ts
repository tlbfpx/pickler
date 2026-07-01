import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  // Login form: scope to the form to avoid matching navbar / app-shell "登录" links.
  await page.locator('.login-container form').waitFor({ state: 'visible' })
  await page.locator('.login-container form input').first().fill('admin')
  await page.locator('.login-container form input[type="password"]').fill('admin123')
  await page
    .locator('.login-container form button.el-button--primary')
    .click()
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 })
})

test('event detail shows stepper and explicit status actions', async ({ page }) => {
  await page.goto('/events')
  // Wait until the table has at least one row loaded (events API returned).
  await page.locator('.el-table__row').first().waitFor({ state: 'visible' })

  // Click the first event title link. EventListView renders titles as
  // <router-link class="title-link" :to="/events/{id}"> so we use that
  // class (position-independent of column count changes).
  await page.locator('.el-table__row').first().locator('a.title-link').first().click()

  // Detail page loaded (URL became /events/:id).
  await page.waitForURL(/\/events\/\d+$/, { timeout: 15000 })

  // Element Plus renders <el-steps> as .el-steps; the wrapper div also has .stepper.
  await expect(page.locator('.el-steps').first()).toBeVisible()

  // Status actions block from EventDetailView.vue.
  await expect(page.locator('.status-actions').first()).toBeVisible()
})
