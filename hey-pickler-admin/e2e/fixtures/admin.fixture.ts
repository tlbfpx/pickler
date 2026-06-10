import { test as base, expect } from '@playwright/test'

export const test = base.extend<{ adminPage: import('@playwright/test').Page }>({
  adminPage: async ({ page }, use) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')
    await use(page)
  },
})

export { expect }
