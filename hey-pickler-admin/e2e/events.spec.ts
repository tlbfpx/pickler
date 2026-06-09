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

test.describe('赛事管理', () => {
  test('新建赛事', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建赛事')

    // 填写赛事表单
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()

    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试锦标赛')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('Playwright自动化测试创建的赛事')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('E2E测试球馆')

    // 设置比赛时间 - 直接输入日期值
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '比赛时间' }).getByPlaceholder('请选择比赛时间').click()
    await adminPage.waitForTimeout(500)
    // 选择下月
    await adminPage.locator('.el-date-picker .el-icon-arrow-right').first().click()
    await adminPage.waitForTimeout(300)
    await adminPage.locator('.el-date-table td.available').first().click()
    await adminPage.waitForTimeout(300)
    // 点击确定按钮确认日期
    const confirmBtns = adminPage.locator('.el-picker-panel__footer .el-button--default')
    if (await confirmBtns.last().isVisible()) {
      await confirmBtns.last().click()
    }

    // 设置报名截止时间
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '报名截止' }).getByPlaceholder('请选择报名截止时间').click()
    await adminPage.waitForTimeout(500)
    await adminPage.locator('.el-date-picker .el-icon-arrow-right').first().click()
    await adminPage.waitForTimeout(300)
    await adminPage.locator('.el-date-table td.available').first().click()
    await adminPage.waitForTimeout(300)
    const confirmBtns2 = adminPage.locator('.el-picker-panel__footer .el-button--default')
    if (await confirmBtns2.last().isVisible()) {
      await confirmBtns2.last().click()
    }

    // 点击对话框内的"新建"按钮
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    await expect(adminPage.getByText('赛事创建成功')).toBeVisible({ timeout: 10000 })
  })

  test('赛事列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await adminPage.waitForTimeout(2000)

    await expect(adminPage.locator('h1')).toContainText('赛事管理')
  })

  test('筛选赛事', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await adminPage.waitForTimeout(2000)

    // 使用筛选下拉
    const typeSelect = adminPage.locator('.filter-bar .el-select').first()
    if (await typeSelect.isVisible()) {
      await typeSelect.click()
      await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()
      await adminPage.waitForTimeout(2000)
    }
  })
})
