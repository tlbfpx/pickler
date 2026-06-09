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

test.describe('Banner管理', () => {
  test('新建Banner', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(1000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建Banner')

    // 填写表单 - 使用更宽松的选择器
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试Banner')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '图片' }).locator('input').fill('https://test.com/banner-e2e.jpg')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '跳转链接' }).getByRole('textbox').fill('https://test.com/link-e2e')

    // 点击对话框内的"新建"按钮
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await expect(adminPage.getByText('Banner创建成功')).toBeVisible({ timeout: 10000 })

    // 验证列表中出现
    await expect(adminPage.getByText('E2E测试Banner')).toBeVisible()
  })

  test('编辑Banner', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    // 点击第一行的编辑按钮
    const editBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '编辑' })
    if (await editBtn.isVisible()) {
      await editBtn.click()
      await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑Banner')

      await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试Banner-已编辑')
      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()

      await expect(adminPage.getByText('Banner更新成功')).toBeVisible({ timeout: 10000 })
    }
  })

  test('Banner列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    // 验证页面标题
    await expect(adminPage.locator('h1')).toContainText('Banner管理')
  })
})
