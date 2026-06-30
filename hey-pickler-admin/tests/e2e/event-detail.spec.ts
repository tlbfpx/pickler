import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  await page.fill('input[placeholder*="用户名"]', 'admin')
  await page.fill('input[placeholder*="密码"]', 'admin123')
  await page.click('button:has-text("登录")')
  await page.waitForURL('/')
})

test('event detail shows stepper and explicit status actions', async ({ page }) => {
  // 前置：需存在一个 DRAFT 事件（用 API 或已有种子）
  await page.goto('/events')
  await page.click('.el-table__row:first-child td:nth-child(3)') // 点标题进详情（按实际列调整）
  await expect(page.locator('.el-steps')).toBeVisible()
  // 状态推进按钮应出现且与后端规则一致
  await expect(page.locator('.status-actions')).toBeVisible()
})
