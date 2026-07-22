import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 侧边栏用 el-sub-menu 折叠组：运营管理 / 积分与排名 / 内容运营 / 数据 / 系统
// 进入子菜单前必须先点击 .el-sub-menu__title 展开
async function expandGroup(page: Page, groupName: string) {
  const title = page.locator('.el-sub-menu__title').filter({ hasText: groupName }).first()
  if (await title.isVisible()) {
    // 检查是否已展开，避免盲目 click 把已展开的组折叠（el-menu toggle 语义）
    const isOpen = await title.locator('xpath=..').evaluate((el: Element) => el.classList.contains('is-opened'))
    if (!isOpen) {
      await title.click()
    }
  }
}

test.describe('导航', () => {
  test('侧边栏展示所有菜单项', async ({ adminPage }) => {
    // 运营管理 — 默认随登录展开
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '工作台' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '竞技赛事' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '社交活动' })).toBeVisible()

    // 积分与排名（PR#53 排名工作台重设计：排名+赛季合并为单菜单「积分与排名」）
    await expandGroup(adminPage, '积分与排名')
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '积分与排名' })).toBeVisible()

    // 内容运营
    await expandGroup(adminPage, '内容运营')
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: 'Banner 管理' })).toBeVisible()

    // 系统
    await expandGroup(adminPage, '系统')
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '封禁记录' })).toBeVisible()
    await expect(adminPage.locator('.el-menu-item').filter({ hasText: '操作日志' })).toBeVisible()
  })

  test('导航到工作台', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '工作台' }).click()
    await expect(adminPage).toHaveURL(/\/$/)
  })

  test('导航到用户管理', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '用户管理' }).click()
    await expect(adminPage).toHaveURL(/\/users/)
  })

  test('导航到竞技赛事', async ({ adminPage }) => {
    await expandGroup(adminPage, '运营管理')
    await adminPage.locator('.el-menu-item').filter({ hasText: '竞技赛事' }).click()
    await expect(adminPage).toHaveURL(/\/events$/)
    // h1 仍为旧文案（视图内未改）
    await expect(adminPage.locator('h1')).toContainText('赛事管理')
  })

  test('导航到社交活动', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '社交活动' }).click()
    await expect(adminPage).toHaveURL(/\/activities/)
    await expect(adminPage.locator('h1')).toContainText('活动管理')
  })

  test('导航到积分与排名', async ({ adminPage }) => {
    await expandGroup(adminPage, '积分与排名')
    await adminPage.locator('.el-menu-item').filter({ hasText: '积分与排名' }).click()
    await expect(adminPage).toHaveURL(/\/rankings/)
  })

  test('导航到Banner管理', async ({ adminPage }) => {
    await expandGroup(adminPage, '内容运营')
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner 管理' }).click()
    await expect(adminPage).toHaveURL(/\/banners/)
  })

  test('导航到管理员管理', async ({ adminPage }) => {
    await expandGroup(adminPage, '系统')
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await expect(adminPage).toHaveURL(/\/admins/)
  })

  test('导航到操作日志', async ({ adminPage }) => {
    await expandGroup(adminPage, '系统')
    await adminPage.locator('.el-menu-item').filter({ hasText: '操作日志' }).click()
    await expect(adminPage).toHaveURL(/\/admin-logs/)
  })
})
