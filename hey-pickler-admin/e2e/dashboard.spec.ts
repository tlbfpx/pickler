import { test, expect } from './fixtures/admin.fixture'

// Dashboard 之前没有 e2e 覆盖，导致 echarts 图表回归（不渲染）没被发现。
// 这个测试锁定：KPI 卡片 + echarts canvas 渲染。
test.describe('工作台 Dashboard', () => {
  test('KPI 卡片与所有 echarts 图表渲染', async ({ adminPage }) => {
    await adminPage.goto('/')
    // KPI 卡片可见
    await expect(adminPage.locator('.kpi-card').first()).toBeVisible({ timeout: 10000 })
    // 等所有图表渲染：chart-lg(2) + chart-sm(3) + chart-xs(4) = 9
    await adminPage.waitForTimeout(3000)
    const totalCanvas = await adminPage.locator('canvas').count()
    expect(totalCanvas).toBeGreaterThanOrEqual(9)
  })
})
