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

test.describe('排名管理', () => {
  test('排名页面展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('排名管理')

    // 使用精确的 tab 选择器
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()
    await expect(adminPage.getByRole('tab', { name: '派对排名' })).toBeVisible()
  })

  test('切换排名Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('tab', { name: '派对排名' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('tab', { name: '明星排名' }).click()
    await adminPage.waitForTimeout(1000)
  })

  test('刷新排名', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('button', { name: '刷新明星排名' }).click()
    await adminPage.waitForTimeout(3000)
  })

  test('打开积分录入弹窗', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('录入积分')

    await expect(adminPage.getByText('记录 #1')).toBeVisible()

    await adminPage.getByRole('button', { name: '添加记录' }).click()
    await expect(adminPage.getByText('记录 #2')).toBeVisible()
  })
})
