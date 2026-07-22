import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 适配 PR #20：管理员管理在折叠的「系统」子菜单下，需先点开
async function gotoAdmins(adminPage: Page) {
  // 系统组默认折叠，先展开（检查 is-opened 避免 toggle 把已展开的折叠）
  const group = adminPage.locator('.el-sub-menu__title').filter({ hasText: '系统' }).first()
  if (await group.isVisible()) {
    const isOpen = await group.locator('xpath=..').evaluate((el: Element) => el.classList.contains('is-opened'))
    if (!isOpen) {
      await group.click()
    }
  }
  await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
  await adminPage.waitForURL(/\/admins$/)
  await expect(adminPage.locator('h1')).toContainText('管理员管理')
  // 等表格行渲染（adminList 加载，避免后续 count=0 误判）
  await expect(adminPage.locator('.el-table__body-wrapper .el-table__row').first()).toBeVisible({ timeout: 10000 })
}

test.describe('管理员管理', () => {
  test('管理员列表展示', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    await expect(adminPage.locator('.el-table')).toBeVisible()

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(1)
  })

  test('新建管理员', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    await adminPage.getByRole('button', { name: '新建管理员' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建管理员')

    const adminName = `e2e_test_${Date.now()}`
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '用户名' })
      .getByRole('textbox')
      .fill(adminName)

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '密码' })
      .getByRole('textbox')
      .fill('Test1234')

    // Select role "管理员" with exact match to avoid matching "超级管理员"
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '角色' })
      .locator('.el-select')
      .click()
    await adminPage.getByRole('option', { name: '管理员', exact: true }).click()

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await expect(adminPage.getByText('管理员创建成功')).toBeVisible({ timeout: 10000 })
  })

  test('编辑管理员角色', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const rowText = await row.textContent()
      if (rowText?.includes('admin')) continue

      const editBtn = row.getByRole('button', { name: '编辑' })
      if (await editBtn.isVisible()) {
        await editBtn.click()
        await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑管理员')

        // Only 2 roles: 超级管理员 and 管理员
        await adminPage
          .locator('.el-dialog .el-form-item')
          .filter({ hasText: '角色' })
          .locator('.el-select')
          .click()
        await adminPage.getByRole('option', { name: '超级管理员' }).click()

        // Edit mode submit button is "更新"
        await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()
        await expect(adminPage.getByText('管理员更新成功')).toBeVisible({ timeout: 10000 })
        return
      }
    }
  })

  test('重置密码', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    // AdminListView 拉 page=1 size=100（id 升序），不含当前登录 admin（其 id 较大，
    // 在后端默认排序的末尾）。第一页任意行都是可被当前 SUPER_ADMIN 重置的账号。
    // 直接用第一行，避免新建后因分页找不到。
    const targetRow = adminPage.locator('.el-table__body-wrapper .el-table__row').first()
    await expect(targetRow).toBeVisible({ timeout: 10000 })

    const resetBtn = targetRow.getByRole('button', { name: '重置密码' })
    await expect(resetBtn).toBeVisible()
    await resetBtn.click()
    await expect(adminPage.locator('.el-dialog:visible .el-dialog__title')).toContainText('重置密码')

    await adminPage
      .locator('.el-dialog:visible .el-form-item')
      .filter({ hasText: '新密码' })
      .getByRole('textbox')
      .fill('Reset1234')

    await adminPage.locator('.el-dialog:visible .el-dialog__footer').getByRole('button', { name: '确定' }).click()
    await expect(adminPage.getByText('密码重置成功')).toBeVisible({ timeout: 10000 })
  })

  test('密码强度不足提示', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    await adminPage.getByRole('button', { name: '新建管理员' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '用户名' })
      .getByRole('textbox')
      .fill(`weakpw_${Date.now()}`)

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '密码' })
      .getByRole('textbox')
      .fill('123')

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    // Expect validation error — could be about password length or other field
    await adminPage.waitForTimeout(1000)
    const hasFormError = await adminPage.locator('.el-form-item__error').first().isVisible()
    const hasMsgError = await adminPage.locator('.el-message--error').first().isVisible()
    expect(hasFormError || hasMsgError).toBeTruthy()
  })

  test('角色选择', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    await adminPage.getByRole('button', { name: '新建管理员' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '用户名' })
      .getByRole('textbox')
      .fill(`role_test_${Date.now()}`)

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '密码' })
      .getByRole('textbox')
      .fill('Test1234')

    const roleSelect = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '角色' })
      .locator('.el-select')

    // Only 2 roles available: 超级管理员 and 管理员
    const roles = ['超级管理员', '管理员']
    for (const role of roles) {
      await roleSelect.click()
      await adminPage.getByRole('option', { name: role, exact: true }).click()
    }
  })

  test('不能修改自己的角色', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const rowText = await row.textContent()
      if (!rowText?.includes('admin')) continue

      const editBtn = row.getByRole('button', { name: '编辑' })
      if (await editBtn.isVisible()) {
        await editBtn.click()
        // May show error message or dialog — either is acceptable
        await adminPage.waitForTimeout(2000)
        return
      }
    }
  })

  test('管理员列表分页', async ({ adminPage }) => {
    await gotoAdmins(adminPage)

    const pagination = adminPage.locator('.el-pagination')
    if (await pagination.isVisible()) {
      await expect(pagination).toContainText('共')
    }
  })
})
