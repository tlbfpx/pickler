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

test.describe('侧边栏导航', () => {
  const pages = [
    { menu: '控制台', url: '/', title: 'Dashboard' },
    { menu: '用户管理', url: '/users', title: '用户管理' },
    { menu: '赛事管理', url: '/events', title: '赛事管理' },
    { menu: '排名管理', url: '/rankings', title: '排名管理' },
    { menu: 'Banner管理', url: '/banners', title: 'Banner管理' },
    { menu: '管理员管理', url: '/admins', title: '管理员管理' },
  ]

  for (const p of pages) {
    test(`导航到${p.menu}`, async ({ adminPage }) => {
      await adminPage.locator('.el-menu-item').filter({ hasText: p.menu }).click()
      await expect(adminPage).toHaveURL(p.url)
      await expect(adminPage.locator('h1')).toContainText(p.title)
    })
  }
})

test.describe('控制台', () => {
  test('显示统计数据', async ({ adminPage }) => {
    await expect(adminPage.locator('.stat-card').first()).toBeVisible({ timeout: 5000 })
    await expect(adminPage.getByText('用户总数')).toBeVisible()
    await expect(adminPage.getByText('进行中赛事')).toBeVisible()
    await expect(adminPage.getByText('最近报名')).toBeVisible()
  })
})
