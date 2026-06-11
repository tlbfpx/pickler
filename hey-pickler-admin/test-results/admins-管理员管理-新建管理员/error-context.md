# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admins.spec.ts >> 管理员管理 >> 新建管理员
- Location: e2e/admins.spec.ts:15:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('管理员创建成功')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByText('管理员创建成功')

```

```yaml
- text: Hey Pickler 管理后台
- menubar:
  - menuitem "控制台":
    - img
    - text: 控制台
  - menuitem "用户管理":
    - img
    - text: 用户管理
  - menuitem "赛事管理":
    - img
    - text: 赛事管理
  - menuitem "排名管理":
    - img
    - text: 排名管理
  - menuitem "Banner管理":
    - img
    - text: Banner管理
  - menuitem "管理员管理":
    - img
    - text: 管理员管理
- heading "Hey Pickler 管理后台" [level=2]
- button:
  - img
  - img
- heading "管理员管理" [level=1]
- button "新建管理员":
  - img
  - text: 新建管理员
- table:
  - rowgroup:
    - row "ID 用户名 角色 创建时间 操作":
      - columnheader "ID"
      - columnheader "用户名"
      - columnheader "角色"
      - columnheader "创建时间"
      - columnheader "操作"
- table:
  - rowgroup
- text: No Data
- dialog "新建管理员":
  - banner:
    - heading "新建管理员" [level=2]
    - button "Close this dialog":
      - img
  - text: "*用户名"
  - textbox "*用户名":
    - /placeholder: 请输入用户名
    - text: e2e_test_1780965957309
  - text: 密码
  - textbox "密码":
    - /placeholder: 请输入密码
    - text: Test123456!
  - img
  - text: "*角色"
  - combobox "*角色"
  - text: 管理员
  - img
  - contentinfo:
    - button "取消"
    - button "新建"
```

# Test source

```ts
  1  | import { test as base, expect } from '@playwright/test'
  2  | 
  3  | const test = base.extend<{ adminPage: import('@playwright/test').Page }>({
  4  |   adminPage: async ({ page }, use) => {
  5  |     await page.goto('/login')
  6  |     await page.getByPlaceholder('请输入用户名').fill('admin')
  7  |     await page.getByPlaceholder('请输入密码').fill('admin123')
  8  |     await page.getByRole('button', { name: '登录' }).click()
  9  |     await expect(page).toHaveURL('/')
  10 |     await use(page)
  11 |   },
  12 | })
  13 | 
  14 | test.describe('管理员管理', () => {
  15 |   test('新建管理员', async ({ adminPage }) => {
  16 |     await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
  17 |     await adminPage.waitForTimeout(1000)
  18 | 
  19 |     await adminPage.getByRole('button', { name: '新建管理员' }).click()
  20 |     await expect(adminPage.locator('.el-dialog__title')).toContainText('新建管理员')
  21 | 
  22 |     const adminName = 'e2e_test_' + Date.now()
  23 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '用户名' }).getByRole('textbox').fill(adminName)
  24 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '密码' }).getByRole('textbox').fill('Test123456!')
  25 | 
  26 |     // 选择角色
  27 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '角色' }).locator('.el-select').click()
  28 |     await adminPage.getByRole('option', { name: '管理员', exact: true }).click()
  29 | 
  30 |     await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
> 31 |     await expect(adminPage.getByText('管理员创建成功')).toBeVisible({ timeout: 10000 })
     |                                                  ^ Error: expect(locator).toBeVisible() failed
  32 | 
  33 |     await expect(adminPage.getByText(adminName)).toBeVisible()
  34 |   })
  35 | 
  36 |   test('管理员列表展示', async ({ adminPage }) => {
  37 |     await adminPage.locator('.el-menu-item').filter({ hasText: '管理员管理' }).click()
  38 |     await adminPage.waitForTimeout(2000)
  39 | 
  40 |     await expect(adminPage.locator('h1')).toContainText('管理员管理')
  41 |   })
  42 | })
  43 | 
```