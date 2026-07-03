import { test, expect } from './fixtures/admin.fixture'
import type { Page } from '@playwright/test'

// 菜单改名为「社交活动」；页面 h1/dialog 仍是「活动管理」（PR #20 没改视图文案）
async function gotoActivities(adminPage: Page) {
  await adminPage.locator('.el-menu-item').filter({ hasText: '社交活动' }).click()
  await adminPage.waitForURL(/\/activities$/)
  await expect(adminPage.locator('h1')).toContainText('活动管理')
  // 用 .card 包裹的 .el-table 锁定本页（避开 dashboard 残留的 2 个表）
  await expect(adminPage.locator('.card .el-table').first()).toBeVisible()
}

test.describe('活动管理', () => {
  test('活动列表展示', async ({ adminPage }) => {
    await gotoActivities(adminPage)
  })

  test('新建活动', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    await adminPage.getByRole('button', { name: '新建活动' }).click()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建活动')

    // 填写标题
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill(`e2e活动${Date.now()}`)

    // 填写描述
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('测试活动描述')

    // 填写地点
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('测试场馆')

    // 设置活动时间 — click picker, type date directly into input
    const eventTimeInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '活动时间' }).locator('input').first()
    await eventTimeInput.click()
    await adminPage.waitForTimeout(300)
    await eventTimeInput.fill('2026-08-15 10:00')
    await adminPage.keyboard.press('Tab')
    await adminPage.waitForTimeout(300)

    // 设置报名截止时间
    const deadlineInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '报名截止' }).locator('input').first()
    await deadlineInput.click()
    await adminPage.waitForTimeout(300)
    await deadlineInput.fill('2026-08-10 18:00')
    await adminPage.keyboard.press('Tab')
    await adminPage.waitForTimeout(300)

    // 填写最大人数
    const maxParticipantsInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '最大人数' }).locator('input')
    if (await maxParticipantsInput.isVisible()) {
      await maxParticipantsInput.click()
      await maxParticipantsInput.fill('50')
    }

    // 点击提交
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()

    await expect(adminPage.getByText('活动创建成功')).toBeVisible({ timeout: 10000 })
  })

  test('编辑活动', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 点击第一行的编辑按钮
    const editBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '编辑' })
    if (await editBtn.isVisible()) {
      await editBtn.click()
      await adminPage.waitForTimeout(1000)
      await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑活动')

      // 修改标题
      const titleInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox')
      await titleInput.clear()
      await titleInput.fill(`编辑活动${Date.now()}`)

      // 点击更新
      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()

      await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
    }
  })

  test('删除活动', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 点击最后一行的删除按钮
    const lastRow = adminPage.locator('.el-table__body-wrapper .el-table__row').last()
    const deleteBtn = lastRow.getByRole('button', { name: '删除' })
    if (await deleteBtn.isVisible()) {
      await deleteBtn.click()
      await adminPage.waitForTimeout(500)

      // 确认删除 - ElMessageBox 使用确定按钮
      await adminPage.locator('.el-message-box__btns').getByRole('button', { name: '确定' }).click()

      await expect(adminPage.getByText('活动删除成功')).toBeVisible({ timeout: 10000 })
    }
  })

  test('按状态筛选', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 找到状态筛选下拉框（activity 列表无显式 status 过滤器时跳过）
    const statusSelect = adminPage.locator('.filter-bar .el-select').first()
    if (await statusSelect.isVisible()) {
      await statusSelect.click()
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
        await adminPage.waitForTimeout(1000)
      }
    }
  })

  test('分页切换', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 如果存在分页器，点击下一页
    const pagination = adminPage.locator('.pagination-container')
    if (await pagination.isVisible()) {
      const nextBtn = pagination.locator('.btn-next')
      if (await nextBtn.isVisible() && await nextBtn.isEnabled()) {
        await nextBtn.click()
        await adminPage.waitForTimeout(2000)
      }
    }
  })

  test('必填字段验证', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 打开新建对话框
    await adminPage.getByRole('button', { name: '新建活动' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 不填写任何内容直接点击新建按钮
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
    await adminPage.waitForTimeout(500)

    // 验证必填字段错误消息可见
    await expect(adminPage.locator('.el-form-item__error').filter({ hasText: '请输入标题' })).toBeVisible()
  })

  test('关闭对话框清空表单', async ({ adminPage }) => {
    await gotoActivities(adminPage)

    // 打开新建对话框
    await adminPage.getByRole('button', { name: '新建活动' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 填写标题
    const titleInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox')
    await titleInput.fill('临时标题测试')

    // 关闭对话框 - 点击取消按钮
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '取消' }).click()
    await adminPage.waitForTimeout(500)

    // 重新打开新建对话框
    await adminPage.getByRole('button', { name: '新建活动' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 验证标题字段已清空
    await expect(titleInput).toHaveValue('')
  })
})
