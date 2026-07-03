import { test, expect } from '@playwright/test'
import { E2E_ADMIN_USERNAME, E2E_ADMIN_PASSWORD } from './fixtures/credentials'

test.describe('登录认证', () => {
  test('使用正确凭据登录', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()

    await expect(page).toHaveURL('/', { timeout: 10000 })
  })

  test('使用错误密码登录失败', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill('wrongpassword')
    await page.getByRole('button', { name: '登录' }).click()

    await page.waitForTimeout(2000)
    await expect(page).toHaveURL('/login')
  })

  test('空表单提交显示验证错误', async ({ page }) => {
    await page.goto('/login')

    await page.getByRole('button', { name: '登录' }).click()

    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()
  })

  test('已登录访问登录页重定向首页', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/', { timeout: 10000 })

    await page.goto('/login')
    await expect(page).toHaveURL('/')
  })

  test('退出登录', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/', { timeout: 10000 })

    // Click user dropdown to open it
    await page.locator('.user-info').click()
    // Wait for dropdown to appear and click 退出登录
    await page.locator('.el-dropdown-menu__item').filter({ hasText: '退出登录' }).click()

    // Confirm — ElMessageBox uses Chinese locale "确定" button
    const confirmBtn = page.locator('.el-message-box__btns').getByRole('button', { name: '确定' })
    if (await confirmBtn.isVisible()) {
      await confirmBtn.click()
    } else {
      await page.getByRole('button', { name: 'OK' }).click()
    }

    await expect(page).toHaveURL('/login', { timeout: 10000 })
  })

  test('登录后token存储', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL('/', { timeout: 10000 })

    const token = await page.evaluate(() => localStorage.getItem('admin_token'))
    expect(token).not.toBeNull()
    expect(token!.length).toBeGreaterThan(0)
  })

  test('特殊字符密码处理', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill('<script>alert(1)</script>')
    await page.getByRole('button', { name: '登录' }).click()

    await page.waitForTimeout(2000)
    await expect(page).toHaveURL('/login')
    await expect(page.locator('.login-container')).toBeVisible()
  })

  test('登录表单回车提交', async ({ page }) => {
    await page.goto('/login')

    await page.getByPlaceholder('请输入用户名').fill(E2E_ADMIN_USERNAME)
    await page.getByPlaceholder('请输入密码').fill(E2E_ADMIN_PASSWORD)
    await page.getByPlaceholder('请输入密码').press('Enter')

    await expect(page).toHaveURL('/', { timeout: 10000 })
  })
})
