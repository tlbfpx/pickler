import { test, expect } from './fixtures/admin.fixture'

// Dashboard 之前没有 e2e 覆盖，导致 echarts 图表回归（不渲染）没被发现。
// 这个测试锁定：KPI 卡片 + echarts canvas 渲染。
test.describe('工作台 Dashboard', () => {
  test('KPI 卡片与所有 echarts 图表渲染', async ({ adminPage }) => {
    await adminPage.goto('/')
    // KPI 卡片可见
    await expect(adminPage.locator('.kpi-card').first()).toBeVisible({ timeout: 10000 })
    // polling 验证 9 canvas 且所有尺寸 >0（覆盖 nextTick 后布局未完成的时机问题，
    // 之前的 waitForTimeout(3000) 等够了反而掩盖了这个回归）
    await expect(async () => {
      const sizes = await adminPage.locator('canvas').evaluateAll(cs => cs.map(c => c.width * c.height))
      expect(sizes.length).toBeGreaterThanOrEqual(9)
      expect(sizes.every(s => s > 0)).toBeTruthy()
    }).toPass({ timeout: 10000 })
  })
})
