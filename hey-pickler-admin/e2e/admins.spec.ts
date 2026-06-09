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

test.describe('管理员管理', () => {
  test('新建管理员', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('button', { name: '新建管理员' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建管理员')

    const adminName = 'e2e_test_' + Date.now()
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '用户名' }).getByRole('textbox').fill(adminName)
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '密码' }).getByRole('textbox').fill('Test123456!')

    // 选择角色
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '角色' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '管理员', exact: true }).click()

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await expect(adminPage.getByText('管理员创建成功')).toBeVisible({ timeout: 10000 })

    await expect(adminPage.getByText(adminName)).toBeVisible()
  })

  test('管理员列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    await expect(adminPage.locator('h1')).toContainText('管理员管理')
  })
})
