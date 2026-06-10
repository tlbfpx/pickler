import { test, expect } from './fixtures/admin.fixture'

test.describe('管理员管理', () => {
  test('管理员列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    await expect(adminPage.locator('h1')).toContainText('管理员管理')
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    await expect(rows).toHaveCount({ min: 1 } as any)
  })

  test('新建管理员', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

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
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    // Find a row that is NOT the current admin user (admin)
    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const rowText = await row.textContent()
      // Skip own row
      if (rowText?.includes('admin')) continue

      const editBtn = row.getByRole('button', { name: '编辑' })
      if (await editBtn.isVisible()) {
        await editBtn.click()
        await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑管理员')

        // Change role
        await adminPage
          .locator('.el-dialog .el-form-item')
          .filter({ hasText: '角色' })
          .locator('.el-select')
          .click()
        await adminPage.getByRole('option', { name: '操作员' }).click()

        await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()
        await expect(adminPage.getByText('管理员更新成功')).toBeVisible({ timeout: 10000 })
        return
      }
    }
  })

  test('重置密码', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const resetBtn = row.getByRole('button', { name: '重置密码' })
      if (await resetBtn.isVisible()) {
        await resetBtn.click()
        await expect(adminPage.locator('.el-dialog')).toBeVisible()

        // Fill new password
        const passwordInput = adminPage
          .locator('.el-dialog .el-form-item')
          .filter({ hasText: '新密码' })
          .getByRole('textbox')
        if (await passwordInput.isVisible()) {
          await passwordInput.fill('NewPass1234!')
        }

        await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '确认' }).click()
        await expect(adminPage.getByText('密码重置成功')).toBeVisible({ timeout: 10000 })
        return
      }
    }
  })

  test('密码强度不足提示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

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

    // Assert validation error about password length
    await expect(adminPage.locator('.el-form-item__error').filter({ hasText: /密码/ })).toBeVisible()
  })

  test('角色选择', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

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

    // Test all three roles
    const roles = ['超级管理员', '管理员', '操作员']
    for (const role of roles) {
      await roleSelect.click()
      await adminPage.getByRole('option', { name: role, exact: true }).click()
      // Verify the selected role is displayed
      await expect(roleSelect.locator('.el-select__selected-item, .el-select__placeholder, .el-input__inner')).toContainText(role)
    }
  })

  test('不能修改自己的角色', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const rowText = await row.textContent()
      // Find own row (admin user)
      if (!rowText?.includes('admin')) continue

      const editBtn = row.getByRole('button', { name: '编辑' })
      if (await editBtn.isVisible()) {
        await editBtn.click()

        // Should show error message about not being able to modify own role
        await expect(adminPage.getByText('不能修改自己的角色')).toBeVisible({ timeout: 5000 })
        return
      }
    }
  })

  test('管理员列表分页', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
    await adminPage.waitForTimeout(2000)

    // Check pagination shows total count text "共"
    const pagination = adminPage.locator('.el-pagination')
    if (await pagination.isVisible()) {
      await expect(pagination).toContainText('共')
    }
  })
})
