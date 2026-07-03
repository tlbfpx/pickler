import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 适配 PR #20：排名管理在折叠的「积分与赛季」子菜单下
// PR #20 + Spec 2/3 后，文案改为战力/活力：
//   - tab：战力排名 / 活力排名（曾用明星/派对）
//   - 录入积分弹窗里的 radio：关联竞技赛事 / 关联社交活动 / 手动录入
//   - 刷新按钮：刷新战力排名 / 刷新活力排名
async function gotoRankings(adminPage: Page) {
  const group = adminPage.locator('.el-sub-menu__title').filter({ hasText: '积分与赛季' }).first()
  if (await group.isVisible()) {
    await group.click()
  }
  await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
  await adminPage.waitForURL(/\/rankings$/)
  await expect(adminPage.locator('h1')).toContainText('排名管理')
}

test.describe('排名管理', () => {
  test('排名列表展示', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()
    await expect(adminPage.getByRole('tab', { name: '活力排名' })).toBeVisible()
  })

  test('战力排名Tab', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    const starTab = adminPage.getByRole('tab', { name: '战力排名' })
    await expect(starTab).toHaveAttribute('aria-selected', 'true')
  })

  test('活力排名Tab', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '活力排名' })).toBeVisible()

    await adminPage.getByRole('tab', { name: '活力排名' }).click()
    await adminPage.waitForTimeout(1000)
    await expect(adminPage.getByRole('tab', { name: '活力排名' })).toHaveAttribute('aria-selected', 'true')
  })

  test('刷新排名', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    const refreshBtn = adminPage.getByRole('button', { name: '刷新战力排名' })
    if (await refreshBtn.isVisible()) {
      await refreshBtn.click()
      await adminPage.waitForTimeout(3000)
      await expect(adminPage.locator('.el-table').first()).toBeVisible()
    }
  })

  test('录入积分弹窗 — 关联赛事模式', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('录入积分')

    // 关联竞技赛事（默认就是 STAR / 关联赛事）
    await adminPage.locator('.el-dialog').getByText('关联竞技赛事', { exact: true }).click()

    // 从下拉选择赛事
    const eventSelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '选择' }).locator('.el-select')
    if (await eventSelect.isVisible()) {
      await eventSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }
  })

  test('录入积分弹窗 — 关联活动模式', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 关联社交活动
    await adminPage.locator('.el-dialog').getByText('关联社交活动', { exact: true }).click()

    const eventSelect = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '选择' }).locator('.el-select')
    if (await eventSelect.isVisible()) {
      await eventSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
      }
    }
  })

  test('录入积分弹窗 — 手动录入', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 手动录入模式
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()
    await adminPage.waitForTimeout(500)

    // 手动模式下，积分类型下拉出现
    const typeSelect = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '积分类型' })
      .locator('.el-select')
    if (await typeSelect.count() > 0 && await typeSelect.first().isVisible()) {
      // 已是默认 STAR，不必交互
    }
  })

  test('录入积分必填验证', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 不选赛事、不填记录直接提交 — 弹窗 footer 的提交按钮是「提交录入」
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' }).click()

    // 校验错误：toast 提示（ElMessage）
    await expect(adminPage.locator('.el-message').first()).toBeVisible({ timeout: 5000 })
  })

  test('添加删除积分行', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 切手动录入后初始会有一条空记录，点击「选择用户」可编辑
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()
    await adminPage.waitForTimeout(500)

    // 「添加一条」按钮存在
    await expect(adminPage.locator('.el-dialog').getByRole('button', { name: '添加一条' })).toBeVisible()

    // 添加记录
    await adminPage.locator('.el-dialog').getByRole('button', { name: '添加一条' }).click()

    // 此时应有 2 个「选择用户」按钮
    const userButtons = adminPage.locator('.el-dialog').getByRole('button', { name: '选择用户' })
    await expect(userButtons).toHaveCount(2)

    // 删除最后一行
    const deleteBtns = adminPage.locator('.el-dialog .record-item button.el-button--danger')
    if (await deleteBtns.last().isVisible()) {
      await deleteBtns.last().click()
    }
  })

  test('积分提交', async ({ adminPage }) => {
    await gotoRankings(adminPage)
    await expect(adminPage.getByRole('tab', { name: '战力排名' })).toBeVisible()

    await adminPage.getByRole('button', { name: '录入积分' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 手动录入模式
    await adminPage.locator('.el-dialog').getByText('手动录入', { exact: true }).click()
    await adminPage.waitForTimeout(500)

    // 点击「提交录入」会触发校验 toast（必填验证已覆盖）— 此用例再走一遍校验路径，
    // 真正的提交依赖远端用户列表 — 太脆于 E2E，留作后端 integration 测试
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交录入' }).click()
    await expect(adminPage.locator('.el-message').first()).toBeVisible({ timeout: 5000 })
  })
})
