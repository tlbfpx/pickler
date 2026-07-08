import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 进入竞技赛事菜单：菜单改名为「竞技赛事」；页面 h1/dialog 仍是「赛事管理」（PR #20 没改视图文案）
async function gotoEvents(adminPage: Page) {
  await adminPage.locator('.el-menu-item').filter({ hasText: '竞技赛事' }).click()
  await adminPage.waitForURL(/\/events$/)
  await expect(adminPage.locator('h1')).toContainText('赛事管理')
  // 用 .card 包裹的 .el-table 锁定本页（避开 dashboard 残留的 2 个表）
  await expect(adminPage.locator('.page-card .el-table').first()).toBeVisible()
}

test.describe('赛事管理', () => {
  test('赛事列表展示', async ({ adminPage }) => {
    await gotoEvents(adminPage)
    await expect(adminPage.locator('.page-card .el-table').first()).toBeVisible()
  })

  test('新建赛事', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建赛事')

    // 选择赛事类型: 明星赛
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '竞技赛事', exact: true }).click()

    // 填写表单
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试锦标赛')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('Playwright自动化测试创建的赛事')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('E2E测试球馆')

    // 设置比赛时间
    const eventTimeInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '比赛时间' }).locator('input').first()
    await eventTimeInput.click()
    await eventTimeInput.fill('2026-09-01 10:00')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).click()

    // 设置报名截止时间
    const deadlineInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '报名截止' }).locator('input').first()
    await deadlineInput.click()
    await deadlineInput.fill('2026-08-25 18:00')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).click()

    // 填写最大参赛人数
    const maxInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '最大参赛人数' }).getByRole('textbox')
    if (await maxInput.isVisible()) {
      await maxInput.fill('50')
    }

    // Button is "新建" not "提交"
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await expect(adminPage.getByText('赛事创建成功')).toBeVisible({ timeout: 10000 })
  })

  test('编辑赛事', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const editBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '编辑' })
    if (await editBtn.isVisible()) {
      await editBtn.click()
      await expect(adminPage.locator('.el-dialog')).toBeVisible()

      const titleInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox')
      await titleInput.clear()
      await titleInput.fill('E2E编辑后的赛事')

      // Edit mode button is "更新"
      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()
      await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
    }
  })

  test('删除赛事', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const lastRow = adminPage.locator('.el-table__body-wrapper .el-table__row').last()
    const deleteBtn = lastRow.getByRole('button', { name: '删除' })
    if (await deleteBtn.isVisible()) {
      await deleteBtn.click()

      // 乐观删除 + 5s 撤销窗口：底部出现撤销条，倒计时到 0 后才真正调后端
      const undoBar = adminPage.locator('.undo-bar')
      await expect(undoBar).toBeVisible({ timeout: 5000 })
      // 等 5s 倒计时结束，后端 DELETE 完成，成功消息浮现（toast 内文本形如 "X" 已删除）
      await expect(adminPage.locator('.el-message').filter({ hasText: '已删除' })).toBeVisible({ timeout: 10000 })
    }
  })

  test('按类型筛选', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const typeSelect = adminPage.locator('.filter-bar .el-select, .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').first()
    if (await typeSelect.isVisible()) {
      await typeSelect.click()
      await adminPage.getByRole('option', { name: '竞技赛事', exact: true }).click()
      await expect(adminPage.locator('.page-card .el-table').first()).toBeVisible()
    }
  })

  test('按状态筛选', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const statusSelect = adminPage.locator('.filter-bar .el-select, .el-form-item').filter({ hasText: '状态' }).locator('.el-select').first()
    if (await statusSelect.isVisible()) {
      await statusSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
        await expect(adminPage.locator('.page-card .el-table').first()).toBeVisible()
      }
    }
  })

  test('分页切换', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const pagination = adminPage.locator('.el-pagination')
    if (await pagination.isVisible()) {
      const nextBtn = pagination.locator('.btn-next')
      if (await nextBtn.isEnabled()) {
        await nextBtn.click()
        await expect(adminPage.locator('.page-card .el-table').first()).toBeVisible()
      }
    }
  })

  test('必填字段验证', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    await expect(adminPage.locator('.el-form-item__error').first()).toBeVisible()
  })

  test('查看参赛者列表', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    const detailBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '详情' })
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await expect(adminPage.locator('.el-drawer, .el-dialog')).toBeVisible()
    }
  })

  test('活动类型选择 — 派对赛', async ({ adminPage }) => {
    await gotoEvents(adminPage)

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '社交活动', exact: true }).click()

    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E派对赛测试')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('派对赛类型测试赛事')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('E2E派对球馆')

    const eventTimeInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '比赛时间' }).locator('input').first()
    await eventTimeInput.click()
    await eventTimeInput.fill('2026-09-01 10:00')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).click()

    const deadlineInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '报名截止' }).locator('input').first()
    await deadlineInput.click()
    await deadlineInput.fill('2026-08-25 18:00')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).click()

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await expect(adminPage.getByText('赛事创建成功')).toBeVisible({ timeout: 10000 })
  })
})
