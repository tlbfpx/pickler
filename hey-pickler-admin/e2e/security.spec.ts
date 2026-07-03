import { test, expect } from './fixtures/admin.fixture'
import { E2E_ADMIN_USERNAME, E2E_ADMIN_PASSWORD } from './fixtures/credentials'

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
    // Items grouped under each <el-sub-menu> are only rendered when the
    // group is expanded. The sidebar only auto-opens the group that
    // contains the active route (here 工作台 → 运营管理), so expand every
    // other group ("积分与赛季", "内容运营", "系统") before asserting.
    const groupTitles = ['运营管理', '积分与赛季', '内容运营', '系统']
    for (const group of groupTitles) {
      const title = adminPage
        .locator('.el-sub-menu__title')
        .filter({ hasText: group })
        .first()
      const isExpanded = await title
        .locator('xpath=..')
        .evaluate((el: Element) => el.classList.contains('is-opened'))
      if (!isExpanded) {
        await title.click()
      }
    }

    const menuItems = [
      '工作台',
      '用户管理',
      '竞技赛事',
      '社交活动',
      '排名管理',
      '赛季管理',
      'Banner 管理',
      '管理员管理',
      '封禁记录',
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

    await expect(adminPage.locator('.el-pagination')).toBeVisible()
    await expect(adminPage.locator('.el-pagination').getByText('共')).toBeVisible()
    await expect(adminPage.locator('.el-pagination').getByText('条/页')).toBeVisible()
  })

  test('退出后token清除', async ({ page }) => {
    await page.goto('/login')
    // Login form selectors aligned with current LoginView.vue (also used by
    // tests/e2e/event-detail.spec.ts) — the previous getByPlaceholder
    // selectors were flaky when both username/password fields happened to
    // share the same aria attributes.
    await page.locator('.login-container form input').first().fill(E2E_ADMIN_USERNAME)
    await page.locator('.login-container form input[type="password"]').fill(E2E_ADMIN_PASSWORD)
    await page.locator('.login-container form button.el-button--primary').click()
    await expect(page).toHaveURL('/', { timeout: 10000 })

    const tokenBefore = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(tokenBefore).not.toBeNull()

    // Open the user dropdown (header right side, AppHeader.vue) and click
    // the logout item — the label is "退出登录" (renamed from "Logout" in PR #20).
    await page.locator('.user-info').click()
    await page
      .locator('.el-dropdown-menu__item')
      .filter({ hasText: '退出登录' })
      .click()

    // Confirm dialog opened by ElMessageBox.confirm — primary button is "确定".
    const confirmBtn = page
      .locator('.el-message-box__btns')
      .getByRole('button', { name: '确定' })
    await confirmBtn.click()

    await expect(page).toHaveURL('/login', { timeout: 10000 })

    const tokenAfter = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(tokenAfter).toBeNull()
  })

  test('登录页无侧边栏', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveURL('/login')

    await expect(page.locator('.el-menu')).not.toBeVisible()
  })
})