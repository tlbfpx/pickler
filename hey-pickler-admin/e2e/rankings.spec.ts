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

    // 验证明星排名 tab 默认激活
    const starTab = adminPage.getByRole('tab', { name: '明星排名' })
    await expect(starTab).toHaveAttribute('aria-selected', 'true')
  })

  test('派对排名Tab', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '派对排名' })).toBeVisible()

    await adminPage.getByRole('tab', { name: '派对排名' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('刷新排名', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    const refreshBtn = adminPage.getByRole('button', { name: '刷新明星排名' })
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click()
      // 等待刷新完成
      await adminPage.waitForTimeout(3000)
      await expect(adminPage.locator('.el-table')).toBeVisible()
    }
  })

  test('录入积分弹窗 — 关联赛事模式', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('录入积分')

    // 选择关联赛事模式
    await adminPage.locator('.el-dialog').getByText('关联赛事').click()

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
    await adminPage.locator('.el-dialog').getByText('关联活动').click()

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
    await adminPage.locator('.el-dialog').getByText('手动录入').click()

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
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' })).click()

    // 验证出现验证错误
    await expect(adminPage.locator('.el-form-item__error, .el-message').first()).toBeVisible()
  })

  test('添加删除积分行', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 默认应该有一条记录
    await expect(adminPage.locator('.el-dialog').getByText('记录 #1')).toBeVisible()

    // 添加一条
    await adminPage.locator('.el-dialog').getByRole('button', { name: '添加一条' }).click()
    await expect(adminPage.locator('.el-dialog').getByText('记录 #2')).toBeVisible()

    // 删除第二条
    const deleteRowBtn = adminPage.locator('.el-dialog').getByRole('button', { name: '删除' }).last()
    await deleteRowBtn.click()
    // 验证第二条记录已删除
    await expect(adminPage.locator('.el-dialog').getByText('记录 #2')).not.toBeVisible()
  })

  test('积分提交', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.getByRole('tab', { name: '明星排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 选择手动录入模式
    await adminPage.locator('.el-dialog').getByText('手动录入').click()

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

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' })).click()
    await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
  })
})
