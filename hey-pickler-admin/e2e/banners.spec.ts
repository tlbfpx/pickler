import { test, expect } from './fixtures/admin.fixture'

test.describe('Banner管理', () => {
  test('Banner列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await expect(adminPage.locator('h1')).toContainText('Banner管理')
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('新建Banner', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建Banner')

    const imageUrl = `https://example.com/e2e-banner-${Date.now()}.jpg`
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill(imageUrl)

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '跳转链接' })
      .getByRole('textbox')
      .fill('https://example.com/link')

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()
    await expect(adminPage.getByText('Banner创建成功')).toBeVisible({ timeout: 10000 })
  })

  test('编辑Banner', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    const editBtn = adminPage
      .locator('.el-table__body-wrapper .el-table__row')
      .first()
      .getByRole('button', { name: '编辑' })
    await expect(editBtn).toBeVisible()
    await editBtn.click()

    await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑Banner')

    const imageUrlInput = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
    await imageUrlInput.clear()
    await imageUrlInput.fill(`https://example.com/edited-banner-${Date.now()}.jpg`)

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()
    await expect(adminPage.getByText('Banner更新成功')).toBeVisible({ timeout: 10000 })
  })

  test('删除Banner', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()
    if (rowCount === 0) return

    const deleteBtn = rows.last().getByRole('button', { name: '删除' })
    await expect(deleteBtn).toBeVisible()
    await deleteBtn.click()

    // Confirm in popover
    await adminPage.getByRole('button', { name: '确定' }).click()
    await expect(adminPage.getByText('Banner删除成功')).toBeVisible({ timeout: 10000 })
  })

  test('必填字段验证', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // Submit without filling any fields
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).click()

    await expect(adminPage.getByText('请输入图片地址')).toBeVisible()
  })

  test('排序字段', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // Fill required fields first
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill(`https://example.com/sort-banner-${Date.now()}.jpg`)

    // Change sort order number input
    const sortInput = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '排序' })
      .getByRole('spinbutton')
    if (await sortInput.isVisible()) {
      await sortInput.clear()
      await sortInput.fill('10')
      await expect(sortInput).toHaveValue('10')
    }
  })

  test('状态选择', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // Fill required fields
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill(`https://example.com/status-banner-${Date.now()}.jpg`)

    // Open status dropdown and select 停用
    const statusSelect = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '状态' })
      .locator('.el-select')
    if (await statusSelect.isVisible()) {
      await statusSelect.click()
      await adminPage.getByRole('option', { name: '停用' }).click()
    }
  })

  test('关闭对话框', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
    await adminPage.waitForTimeout(2000)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // Fill something
    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill('https://example.com/will-be-cleared.jpg')

    // Click cancel
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '取消' }).click()
    await expect(adminPage.locator('.el-dialog')).not.toBeVisible()

    // Reopen and assert form is cleared
    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    const imageUrlInput = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
    await expect(imageUrlInput).toHaveValue('')
  })
})
