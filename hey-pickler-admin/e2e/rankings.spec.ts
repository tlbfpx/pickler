import { test, expect } from './fixtures/admin.fixture'

test.describe('排名管理', () => {
  test('排名列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('排名管理')
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()
    await expect(adminPage.getByRole('tab', { name: '派对排名' })).toBeVisible()
  })

  test('明星排名Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    const starTab = adminPage.getByRole('tab', { name: '明星排名' })
    await expect(starTab).toHaveAttribute('aria-selected', 'true')
  })

  test('派对排名Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '派对排名' })).toBeVisible()

    await adminPage.getByRole('tab', { name: '派对排名' }).click()
    await adminPage.waitForTimeout(1000)
    // After tab switch, check that the tab is now active
    await expect(adminPage.getByRole('tab', { name: '派对排名' })).toHaveAttribute('aria-selected', 'true')
  })

  test('刷新排名', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    const refreshBtn = adminPage.getByRole('button', { name: '刷新明星排名' })
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click()
      await adminPage.waitForTimeout(3000)
      await expect(adminPage.locator('.el-table').first()).toBeVisible()
    }
  })

  test('录入积分弹窗 — 关联赛事模式', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('录入积分')

    // 选择关联赛事模式 — use exact match to avoid matching "关联赛事/活动"
    await adminPage.locator('.el-dialog').getByText('关联赛事', { exact: true }).click()

    // 从下拉选择赛事
    const eventSelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事' }).locator('.el-select')
    if (await eventSelect.isVisible()) {
      await eventSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }
  })

  test('录入积分弹窗 — 关联活动模式', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 选择关联活动模式
    await adminPage.locator('.el-dialog').getByText('关联活动', { exact: true }).click()

    // 从下拉选择活动
    const activitySelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '活动' }).locator('.el-select')
    if (await activitySelect.isVisible()) {
      await activitySelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }
  })

  test('录入积分弹窗 — 手动录入', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 选择手动录入模式
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()

    // 选择积分类型
    const typeSelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '积分类型' }).locator('.el-select')
    if (await typeSelect.isVisible()) {
      await typeSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }
  })

  test('录入积分必填验证', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 不填写记录直接提交
    const submitBtn = adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' })
    if (!await submitBtn.isVisible()) {
      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()
    } else {
      await submitBtn.click()
    }

    await expect(adminPage.locator('.el-form-item__error, .el-message').first()).toBeVisible()
  })

  test('添加删除积分行', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // In MANUAL mode, there's initially one empty record with "选择用户" button
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()
    await adminPage.waitForTimeout(500)

    // Check "添加一条" button exists
    await expect(adminPage.locator('.el-dialog').getByRole('button', { name: '添加一条' })).toBeVisible()

    // Add a record
    await adminPage.locator('.el-dialog').getByRole('button', { name: '添加一条' }).click()

    // Now there should be 2 "选择用户" buttons (one per record)
    const userButtons = adminPage.locator('.el-dialog').getByRole('button', { name: '选择用户' })
    await expect(userButtons).toHaveCount(2)

    // Delete the second record — find delete button in last record-item
    const deleteBtns = adminPage.locator('.el-dialog .record-item .el-button--danger')
    if (await deleteBtns.last().isVisible()) {
      await deleteBtns.last().click()
    }
  })

  test('积分提交', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 选择手动录入模式
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()

    // 选择积分类型
    const typeSelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '积分类型' }).locator('.el-select')
    if (await typeSelect.isVisible()) {
      await typeSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }

    // 填写积分
    const pointsInput = adminPage.locator('.el-dialog').getByPlaceholder('积分').first()
    if (await pointsInput.isVisible()) {
      await pointsInput.fill('10')
    }

    // 填写原因
    const reasonInput = adminPage.locator('.el-dialog').getByPlaceholder('原因').first()
    if (await reasonInput.isVisible()) {
      await reasonInput.fill('E2E自动化测试积分')
    }

    const submitBtn = adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' })
    if (!await submitBtn.isVisible()) {
      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()
    } else {
      await submitBtn.click()
    }
    await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
  })
})
