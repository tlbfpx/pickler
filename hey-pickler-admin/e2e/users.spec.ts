import { test as base, expect } from '@playwright/test'

const test = base.extend<{ adminPage: import('@playwright/test').Page }>({
  adminPage: async ({ page }, use) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')
    await use(page)
  },
})

test.describe('用户管理', () => {
  test('用户列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('用户管理')
    await adminPage.waitForTimeout(1000)

    await expect(adminPage.getByRole('columnheader', { name: '昵称' })).toBeVisible()
    await expect(adminPage.getByRole('columnheader', { name: '状态' })).toBeVisible()
  })

  test('搜索用户', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByPlaceholder('搜索手机号或昵称').fill('test')
    await adminPage.getByRole('button', { name: '搜索' }).click()
    await adminPage.waitForTimeout(1000)
  })
})
