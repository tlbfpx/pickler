import { test, expect } from './fixtures/admin.fixture'

test.describe('安全与权限', () => {
  test('未登录访问受保护页面重定向', async ({ page }) => {
    await page.goto('/users')
    await expect(page).toHaveURL('/login')
  })

  test('未登录访问多个页面重定向', async ({ page }) => {
    const protectedPaths = ['/events', '/rankings', '/admins']

    for (const path of protectedPaths) {
      await page.goto(path)
      await expect(page).toHaveURL('/login')
    }
  })

  test('登录后侧边栏菜单可见', async ({ adminPage }) => {
    const menuItems = [
      '控制台',
      '用户管理',
      '赛事管理',
      '活动管理',
      '排名管理',
      'Banner管理',
      '管理员管理',
      '操作日志',
    ]

    for (const item of menuItems) {
      await expect(
        adminPage.locator('.el-menu-item').filter({ hasText: item })
      ).toBeVisible()
    }
  })

  test('分页组件中文化', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage).toHaveURL('/users')

    // Wait for the table/pagination to load
    await expect(adminPage.locator('.el-pagination')).toBeVisible()

    // Assert Chinese localization text in pagination
    await expect(adminPage.locator('.el-pagination').getByText('共')).toBeVisible()
    await expect(adminPage.locator('.el-pagination').getByText('条/页')).toBeVisible()
  })

  test('退出后token清除', async ({ page }) => {
    // Login
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')

    // Verify token exists before logout
    const tokenBefore = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(tokenBefore).not.toBeNull()

    // Logout via header dropdown
    await page.locator('.user-info').click()
    await page.getByRole('listitem').filter({ hasText: 'Logout' }).click()
    await page.getByRole('button', { name: 'OK' }).click()
    await expect(page).toHaveURL('/login')

    // Verify token is cleared
    const tokenAfter = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(tokenAfter).toBeNull()
  })

  test('登录页无侧边栏', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveURL('/login')

    await expect(page.locator('.el-menu')).not.toBeVisible()
  })
})
