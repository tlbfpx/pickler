import { test, expect } from '@playwright/test'

test.describe('登录', () => {
  test('使用正确凭据登录', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('h1')).toContainText('Hey Pickler 管理后台')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()

    await expect(page).toHaveURL('/')
    await expect(page.locator('h1')).toContainText('Dashboard')
  })

  test('使用错误密码登录失败', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('wrongpassword')
    await page.getByRole('button', { name: '登录' }).click()

    // 等待错误提示（可能是 ElMessage）
    await page.waitForTimeout(2000)
    // 仍然停留在登录页
    await expect(page).toHaveURL('/login')
  })

  test('空表单提交显示验证错误', async ({ page }) => {
    await page.goto('/login')

    await page.getByRole('button', { name: '登录' }).click()

    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()
  })
})
