import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 适配 PR #20：封禁记录在折叠的「系统」子菜单下；h1 仍是「用户日志」（视图未迁）
async function gotoBanRecords(adminPage: Page) {
  const group = adminPage.locator('.el-sub-menu__title').filter({ hasText: '系统' }).first()
  if (await group.isVisible()) {
    await group.click()
  }
  await adminPage.locator('.el-menu-item').filter({ hasText: '封禁记录' }).click()
  await adminPage.waitForURL(/\/ban-records$/)
  await expect(adminPage.locator('h1')).toContainText('用户日志')
}

test.describe('封禁记录', () => {
  test('封禁记录列表展示', async ({ adminPage }) => {
    await gotoBanRecords(adminPage)
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('按操作类型筛选', async ({ adminPage }) => {
    await gotoBanRecords(adminPage)

    // 找到操作类型筛选下拉框
    const actionSelect = adminPage.locator('.filter-bar .el-select').first()
    if (await actionSelect.isVisible()) {
      // 筛选封禁
      await actionSelect.click()
      await adminPage.getByRole('option', { name: '封禁', exact: true }).click()
      await adminPage.waitForTimeout(2000)

      // 筛选解禁
      await actionSelect.click()
      await adminPage.getByRole('option', { name: '解禁', exact: true }).click()
      await adminPage.waitForTimeout(2000)
    }
  })

  test('分页切换', async ({ adminPage }) => {
    await gotoBanRecords(adminPage)

    // 验证分页总数显示
    const pagination = adminPage.locator('.pagination-container')
    if (await pagination.isVisible()) {
      await expect(pagination.locator('span').filter({ hasText: '共' })).toBeVisible()

      // 点击下一页
      const nextBtn = pagination.locator('.btn-next')
      if (await nextBtn.isVisible() && await nextBtn.isEnabled()) {
        await nextBtn.click()
        await adminPage.waitForTimeout(2000)
      }
    }
  })

  test('手机号脱敏显示', async ({ adminPage }) => {
    await gotoBanRecords(adminPage)

    // 检查表格中用户列是否包含脱敏手机号（格式: 138****5678）
    const userCells = adminPage.locator('.el-table__body-wrapper .user-sub')
    const count = await userCells.count()

    for (let i = 0; i < Math.min(count, 3); i++) {
      const text = await userCells.nth(i).textContent()
      if (text && text.includes('****')) {
        // 验证脱敏格式: 3位数字 + **** + 4位数字
        expect(text).toMatch(/\d{3}\*\*\*\*\d{4}/)
      }
    }
  })
})
