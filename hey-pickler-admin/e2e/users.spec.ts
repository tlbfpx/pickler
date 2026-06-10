import { test, expect } from './fixtures/admin.fixture'

test.describe('用户管理', () => {
  test('用户列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('用户管理')
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('搜索用户', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const searchInput = adminPage.getByPlaceholder('按手机号或昵称搜索')
    await searchInput.fill('test')
    await adminPage.getByRole('button', { name: '搜索' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('按城市搜索', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const cityInput = adminPage.getByPlaceholder('按城市搜索')
    if (await cityInput.isVisible()) {
      await cityInput.fill('上海')
      await adminPage.getByRole('button', { name: '搜索' }).click()
      await expect(adminPage.locator('.el-table')).toBeVisible()
    }
  })

  test('查看用户详情', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const detailBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '详情' })
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await expect(adminPage.locator('.el-drawer')).toBeVisible()
      await expect(adminPage.locator('.el-drawer__body')).toBeVisible()
    }
  })

  test('积分明细Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const detailBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '详情' })
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await expect(adminPage.locator('.el-drawer')).toBeVisible()

      // 明星积分明细 tab should be visible
      await expect(adminPage.locator('.el-drawer').getByRole('tab', { name: '明星积分明细' })).toBeVisible()

      // 切换到派对积分明细 tab
      await adminPage.locator('.el-drawer').getByRole('tab', { name: '派对积分明细' }).click()
      await expect(adminPage.locator('.el-drawer').locator('.el-table')).toBeVisible()
    }
  })

  test('赛事记录Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const detailBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '详情' })
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await expect(adminPage.locator('.el-drawer')).toBeVisible()

      await adminPage.locator('.el-drawer').getByRole('tab', { name: '赛事记录' }).click()
      await expect(adminPage.locator('.el-drawer').locator('.el-table')).toBeVisible()
    }
  })

  test('封禁用户', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    // 查找有禁赛按钮的行
    const banBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').locator('button', { hasText: '禁赛' }).first()
    if (await banBtn.isVisible()) {
      await banBtn.click()

      // 填写封禁原因
      const reasonInput = adminPage.locator('.el-dialog .el-textarea__inner').or(adminPage.locator('.el-dialog input[type="text"]'))
      if (await reasonInput.isVisible()) {
        await reasonInput.fill('E2E自动化测试封禁')
      }

      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '确认' }).click()
      await expect(adminPage.locator('.el-message').getByText('成功')).toBeVisible({ timeout: 10000 })
    }
  })

  test('解禁用户', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    // 查找有解禁按钮的行（用户状态为 BANNED）
    const unbanBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').locator('button', { hasText: '解禁' }).first()
    if (await unbanBtn.isVisible()) {
      await unbanBtn.click()
      await expect(adminPage.locator('.el-message').getByText('成功')).toBeVisible({ timeout: 10000 })
    }
  })

  test('分页切换', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    // 验证分页组件存在
    await expect(adminPage.locator('.el-pagination')).toBeVisible()
    await expect(adminPage.locator('.el-pagination').getByText('共')).toBeVisible()

    // 切换每页条数
    const pageSizeSelect = adminPage.locator('.el-pagination .el-select')
    if (await pageSizeSelect.isVisible()) {
      await pageSizeSelect.click()
      const option = adminPage.getByRole('option', { name: '20' }).or(adminPage.getByRole('option', { name: '50' }))
      if (await option.first().isVisible()) {
        await option.first().click()
        await expect(adminPage.locator('.el-table')).toBeVisible()
      }
    }
  })

  test('手机号脱敏', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    // 验证表格中手机号包含 **** 脱敏标记
    const phoneCells = adminPage.locator('.el-table__body-wrapper .el-table__row').first().locator('td')
    const phoneTexts = await phoneCells.allTextContents()
    const hasMaskedPhone = phoneTexts.some((text) => text.includes('****'))
    expect(hasMaskedPhone).toBeTruthy()
  })
})
