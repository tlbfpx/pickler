import { test, expect } from './fixtures/admin.fixture'

test.describe('赛事管理', () => {
  test('赛事列表展示', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('h1')).toContainText('赛事管理')
    await expect(adminPage.locator('.el-table')).toBeVisible()
  })

  test('新建赛事', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()
    await expect(adminPage.locator('.el-dialog__title')).toContainText('新建赛事')

    // 选择赛事类型: 明星赛
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()

    // 填写表单
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试锦标赛')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('Playwright自动化测试创建的赛事')
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('E2E测试球馆')

    // 设置比赛时间
    const eventTimeInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '比赛时间' }).locator('input').first()
    await eventTimeInput.click()
    await eventTimeInput.fill('2026-09-01 10:00')
    // 点击表单其他区域关闭日期选择器
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

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' })).click()
    await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
  })

  test('编辑赛事', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const editBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '编辑' })
    if (await editBtn.isVisible()) {
      await editBtn.click()
      await expect(adminPage.locator('.el-dialog')).toBeVisible()

      // 修改标题
      const titleInput = adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox')
      await titleInput.clear()
      await titleInput.fill('E2E编辑后的赛事')

      await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '保存' })).click()
      await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
    }
  })

  test('删除赛事', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const lastRow = adminPage.locator('.el-table__body-wrapper .el-table__row').last()
    const deleteBtn = lastRow.getByRole('button', { name: '删除' })
    if (await deleteBtn.isVisible()) {
      await deleteBtn.click()

      // 确认删除弹窗
      await adminPage.locator('.el-message-box__btns').getByRole('button', { name: '确认' }).or(adminPage.locator('.el-message-box__btns').getByRole('button', { name: '删除' })).click()
      await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
    }
  })

  test('按类型筛选', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const typeSelect = adminPage.locator('.filter-bar .el-select, .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').first()
    if (await typeSelect.isVisible()) {
      await typeSelect.click()
      await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()
      await expect(adminPage.locator('.el-table')).toBeVisible()
    }
  })

  test('按状态筛选', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const statusSelect = adminPage.locator('.filter-bar .el-select, .el-form-item').filter({ hasText: '状态' }).locator('.el-select').first()
    if (await statusSelect.isVisible()) {
      await statusSelect.click()
      // 选择一个状态选项
      const firstOption = adminPage.getByRole('option').first()
      if (await firstOption.isVisible()) {
        await firstOption.click()
        await expect(adminPage.locator('.el-table')).toBeVisible()
      }
    }
  })

  test('分页切换', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    const pagination = adminPage.locator('.el-pagination')
    if (await pagination.isVisible()) {
      const nextBtn = pagination.locator('.btn-next')
      if (await nextBtn.isEnabled()) {
        await nextBtn.click()
        await expect(adminPage.locator('.el-table')).toBeVisible()
      }
    }
  })

  test('必填字段验证', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 不填写任何内容直接提交
    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' })).click()

    // 验证出现表单验证错误提示
    await expect(adminPage.locator('.el-form-item__error').first()).toBeVisible()
  })

  test('查看参赛者列表', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    // 查找有详情/参赛者按钮的行
    const detailBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '详情' }).or(adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '参赛者' }))
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await expect(adminPage.locator('.el-drawer, .el-dialog')).toBeVisible()
    }
  })

  test('活动类型选择 — 派对赛', async ({ adminPage }) => {
    await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
    await expect(adminPage.locator('.el-table')).toBeVisible()

    await adminPage.getByRole('button', { name: '新建赛事' }).click()
    await expect(adminPage.locator('.el-dialog')).toBeVisible()

    // 选择赛事类型: 派对赛
    await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
    await adminPage.getByRole('option', { name: '派对赛', exact: true }).click()

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

    await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '提交' }).or(adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' })).click()
    await expect(adminPage.getByText(/成功/)).toBeVisible({ timeout: 10000 })
  })
})
