import { test, expect } from './fixtures/admin.fixture'

test.describe('导航', () => {
  test('侧边栏展示所有菜单项', async ({ adminPage }) => {
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '控制台' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '活动管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '操作日志' })).toBeVisible()
  })

  test('导航到控制台', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '控制台' }).click()
    await expect(adminPage.locator('h1')).toContainText('控制台')
  })

  test('导航到用户管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('用户管理')
  })

  test('导航到赛事管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('赛事管理')
  })

  test('导航到活动管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '活动管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('活动管理')
  })

  test('导航到排名管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '排名管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('排名管理')
  })

  test('导航到Banner管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('Banner管理')
  })

  test('导航到管理员管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('管理员管理')
  })

  test('导航到操作日志', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '操作日志' }).click()
    await expect(adminPage.locator('h1')).toContainText('操作日志')
  })
})
