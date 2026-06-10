import { test, expect } from '@playwright/test'

test.describe('登录认证', () => {
  test('使用正确凭据登录', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()

    await expect(page).toHaveURL('/')
  })

  test('使用错误密码登录失败', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('wrongpassword')
    await page.getByRole('button', { name: '登录' }).click()

    // Element Plus ElMessage error toast
    await expect(page.locator('.el-message--error')).toBeVisible()
    await expect(page).toHaveURL('/login')
  })

  test('空表单提交显示验证错误', async ({ page }) => {
    await page.goto('/login')

    await page.getByRole('button', { name: '登录' }).click()

    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()
  })

  test('已登录访问登录页重定向首页', async ({ page }) => {
    // Login first
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')

    // Navigate to /login while authenticated
    await page.goto('/login')
    await expect(page).toHaveURL('/')
  })

  test('退出登录', async ({ page }) => {
    // Login first
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')

    // Click user dropdown to open it, then click Logout
    await page.locator('.user-info').click()
    await page.getByRole('listitem').filter({ hasText: 'Logout' }).click()

    // Confirm the ElMessageBox dialog
    await page.getByRole('button', { name: 'OK' }).click()

    await expect(page).toHaveURL('/login')
  })

  test('登录后token存储', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/')

    const token = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(token).not.toBeNull()
    expect(token!.length).toBeGreaterThan(0)
  })

  test('特殊字符密码处理', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('<script>alert(1)</script>')
    await page.getByRole('button', { name: '登录' }).click()

    // Should stay on login page (wrong password) without crashing
    await expect(page).toHaveURL('/login')
    // No JS alert should fire — the page should remain stable
    await expect(page.locator('.login-container')).toBeVisible()
  })

  test('登录表单回车提交', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByPlaceholder('请输入密码').press('Enter')

    await expect(page).toHaveURL('/')
  })
})
