import { test as base, expect } from '@playwright/test'
import { E2E_ADMIN_USERNAME, E2E_ADMIN_PASSWORD } from './credentials'

export const test = base.extend<{ adminPage: import('@playwright/test').Page }>({
  adminPage: async ({ page }, use) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/', { timeout: 10000 })
    await use(page)
  },
})

export { expect }
