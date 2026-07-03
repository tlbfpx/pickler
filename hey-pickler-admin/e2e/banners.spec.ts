import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 适配 PR #20：Banner 管理在折叠的「内容运营」子菜单下（注意路由 title 是「Banner 管理」含空格）
async function gotoBanners(adminPage: Page) {
  const group = adminPage.locator('.el-sub-menu__title').filter({ hasText: '内容运营' }).first()
  if (await group.isVisible()) {
    await group.click()
  }
  await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner 管理' }).click()
  await adminPage.waitForURL(/\/banners$/)
  await expect(adminPage.locator('h1')).toContainText('Banner管理')
}

test.describe('Banner 管理', () => {
  test('Banner 列表展示', async ({ adminPage }) => {
    await gotoBanners(adminPage)
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('新建Banner', async ({ adminPage }) => {
    await gotoBanners(adminPage)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建Banner')

    // 后端 HeadBasedImageUrlValidator 要求：HEAD 返回 2xx + image/* content-type
    // 前端正则：/^https:\/\/[^/]+\/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$/i
    // 用 placehold.co 静态图（URL 不能带额外 query，否则上游返回 403/404）
    const imageUrl = `https://placehold.co/${Date.now() % 900 + 100}x300.jpg`
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

    // Button is "新建" not "提交"
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    // 成功信号：dialog 关闭（ElMessage.success 闪退太快，改用 dialog 状态）
    await expect(adminPage.locator('.el-dialog')).not.toBeVisible({ timeout: 10000 })
    // 表格不展示图片 URL 文本，仅做行级断言 — 行数 ≥ 1 且 linkUrl 匹配
    await expect(adminPage.locator('.el-table__body-wrapper')).toContainText('https://example.com/link')
  })

  test('编辑Banner', async ({ adminPage }) => {
    await gotoBanners(adminPage)

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
    await imageUrlInput.fill(`https://placehold.co/${Date.now() % 900 + 100}x300-edit.jpg`)

    // Button is "更新" not "提交"
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()
    await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
  })

  test('删除Banner', async ({ adminPage }) => {
    await gotoBanners(adminPage)

    const rows = adminPage.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()
    if (rowCount === 0) return

    const deleteBtn = rows.last().getByRole('button', { name: '删除' })
    await expect(deleteBtn).toBeVisible()
    await deleteBtn.click()

    // Confirm in ElMessageBox
    await adminPage.locator('.el-message-box__btns').getByRole('button', { name: '确定' }).click()
    await expect(adminPage.getByText('Banner删除成功')).toBeVisible({ timeout: 10000 })
  })

  test('必填字段验证', async ({ adminPage }) => {
    await gotoBanners(adminPage)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // Submit without filling — button is "新建"
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    await expect(adminPage.getByText('请输入图片地址')).toBeVisible()
  })

  test('排序字段', async ({ adminPage }) => {
    await gotoBanners(adminPage)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill(`https://example.com/sort-banner-${Date.now()}.jpg`)

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
    await gotoBanners(adminPage)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill(`https://example.com/status-banner-${Date.now()}.jpg`)

    const statusSelect = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '状态' })
      .locator('.el-select')
    if (await statusSelect.isVisible()) {
      await statusSelect.click()
      await adminPage.waitForTimeout(500)
      await adminPage.getByRole('option', { name: '停用' }).click()
    }
  })

  test('关闭对话框', async ({ adminPage }) => {
    await gotoBanners(adminPage)

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
      .fill('https://example.com/will-be-cleared.jpg')

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '取消' }).click()
    await expect(adminPage.locator('.el-dialog')).not.toBeVisible()

    await adminPage.getByRole('button', { name: '新建Banner' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    const imageUrlInput = adminPage
      .locator('.el-dialog .el-form-item')
      .filter({ hasText: '图片地址' })
      .getByRole('textbox')
    await expect(imageUrlInput).toHaveValue('')
  })
})
