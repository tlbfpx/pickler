import { test, expect } from './fixtures/admin.fixture'

// Dashboard 之前没有 e2e 覆盖，导致 echarts 图表回归（不渲染）没被发现。
// 这个测试锁定：KPI 卡片 + echarts canvas 渲染。
test.describe('工作台 Dashboard', () => {
  test('KPI 卡片与 echarts 图表正常渲染', async ({ adminPage }) => {
    await adminPage.goto('/')
    // KPI 卡片可见
    await expect(adminPage.locator('.kpi-card').first()).toBeVisible({ timeout: 10000 })
    // echarts 渲染会在 .chart-lg 容器内创建 canvas
    await expect(adminPage.locator('.chart-lg canvas').first()).toBeAttached({ timeout: 10000 })
    // 等图表绘制完成
    await adminPage.waitForTimeout(2000)
    const canvasCount = await adminPage.locator('.chart-lg canvas').count()
    expect(canvasCount).toBeGreaterThanOrEqual(2)
  })
})
