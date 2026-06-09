# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: banners.spec.ts >> Banner管理 >> 新建Banner
- Location: e2e/banners.spec.ts:15:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.fill: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('.el-dialog .el-form-item').filter({ hasText: '图片' }).locator('input')
    - locator resolved to <input name="file" type="file" accept="image/*" class="el-upload__input"/>
    - fill("https://test.com/banner-e2e.jpg")
  - attempting fill action
    2 × waiting for element to be visible, enabled and editable
      - element is not visible
    - retrying fill action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and editable
      - element is not visible
    - retrying fill action
      - waiting 100ms
    51 × waiting for element to be visible, enabled and editable
       - element is not visible
     - retrying fill action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - generic [ref=e5]: Hey Pickler 管理后台
    - menubar [ref=e6]:
      - menuitem "控制台" [ref=e7] [cursor=pointer]:
        - img [ref=e9]
        - generic [ref=e13]: 控制台
      - menuitem "用户管理" [ref=e14] [cursor=pointer]:
        - img [ref=e16]
        - generic [ref=e18]: 用户管理
      - menuitem "赛事管理" [ref=e19] [cursor=pointer]:
        - img [ref=e21]
        - generic [ref=e23]: 赛事管理
      - menuitem "排名管理" [ref=e24] [cursor=pointer]:
        - img [ref=e26]
        - generic [ref=e28]: 排名管理
      - menuitem "Banner管理" [ref=e29] [cursor=pointer]:
        - img [ref=e31]
        - generic [ref=e34]: Banner管理
      - menuitem "管理员管理" [ref=e35] [cursor=pointer]:
        - img [ref=e37]
        - generic [ref=e39]: 管理员管理
  - generic [ref=e40]:
    - generic [ref=e41]:
      - heading "Hey Pickler 管理后台" [level=2] [ref=e43]
      - button [ref=e46] [cursor=pointer]:
        - img [ref=e48]
        - img [ref=e51]
    - generic [ref=e54]:
      - generic [ref=e55]:
        - heading "Banner管理" [level=1] [ref=e56]
        - button "新建Banner" [ref=e57] [cursor=pointer]:
          - generic [ref=e58]:
            - img [ref=e60]
            - text: 新建Banner
      - generic [ref=e64]:
        - table [ref=e66]:
          - rowgroup [ref=e76]:
            - row "ID 标题 图片 跳转链接 排序 状态 创建时间 操作" [ref=e77]:
              - columnheader "ID" [ref=e78]:
                - generic [ref=e79]: ID
              - columnheader "标题" [ref=e80]:
                - generic [ref=e81]: 标题
              - columnheader "图片" [ref=e82]:
                - generic [ref=e83]: 图片
              - columnheader "跳转链接" [ref=e84]:
                - generic [ref=e85]: 跳转链接
              - columnheader "排序" [ref=e86]:
                - generic [ref=e87]: 排序
              - columnheader "状态" [ref=e88]:
                - generic [ref=e89]: 状态
              - columnheader "创建时间" [ref=e90]:
                - generic [ref=e91]: 创建时间
              - columnheader "操作" [ref=e92]:
                - generic [ref=e93]: 操作
        - generic [ref=e97]:
          - table:
            - rowgroup
          - generic [ref=e99]: No Data
      - dialog "新建Banner" [ref=e101]:
        - generic [ref=e102]:
          - banner [ref=e103]:
            - heading "新建Banner" [level=2] [ref=e104]
            - button "Close this dialog" [ref=e105] [cursor=pointer]:
              - img [ref=e107]
          - generic [ref=e110]:
            - generic [ref=e111]:
              - generic [ref=e112]: "*标题"
              - textbox "*标题" [active] [ref=e116]:
                - /placeholder: 请输入Banner标题
                - text: E2E测试Banner
            - group "*图片" [ref=e117]:
              - generic [ref=e118]: "*图片"
              - button "上传图片" [ref=e122] [cursor=pointer]:
                - button "上传图片" [ref=e123]:
                  - generic [ref=e124]:
                    - img [ref=e126]
                    - text: 上传图片
            - generic [ref=e128]:
              - generic [ref=e129]: "*跳转链接"
              - textbox "*跳转链接" [ref=e133]:
                - /placeholder: 请输入跳转链接
            - generic [ref=e134]:
              - generic [ref=e135]: "*排序"
              - generic [ref=e137]:
                - button "decrease number" [ref=e138]:
                  - img [ref=e140]
                - button "increase number" [ref=e142] [cursor=pointer]:
                  - img [ref=e144]
                - spinbutton "*排序" [ref=e148]: "0"
            - generic [ref=e149]:
              - generic [ref=e150]: 启用
              - generic [ref=e152]:
                - switch "启用" [checked]
          - contentinfo [ref=e155]:
            - button "取消" [ref=e156] [cursor=pointer]:
              - generic [ref=e157]: 取消
            - button "新建" [ref=e158] [cursor=pointer]:
              - generic [ref=e159]: 新建
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
  14 | test.describe('Banner管理', () => {
  15 |   test('新建Banner', async ({ adminPage }) => {
  16 |     await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
  17 |     await adminPage.waitForTimeout(1000)
  18 | 
  19 |     await adminPage.getByRole('button', { name: '新建Banner' }).click()
  20 |     await expect(adminPage.locator('.el-dialog__title')).toContainText('新建Banner')
  21 | 
  22 |     // 填写表单 - 使用更宽松的选择器
  23 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试Banner')
> 24 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '图片' }).locator('input').fill('https://test.com/banner-e2e.jpg')
     |                                                                                                    ^ Error: locator.fill: Test timeout of 30000ms exceeded.
  25 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '跳转链接' }).getByRole('textbox').fill('https://test.com/link-e2e')
  26 | 
  27 |     // 点击对话框内的"新建"按钮
  28 |     await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
  29 |     await expect(adminPage.getByText('Banner创建成功')).toBeVisible({ timeout: 10000 })
  30 | 
  31 |     // 验证列表中出现
  32 |     await expect(adminPage.getByText('E2E测试Banner')).toBeVisible()
  33 |   })
  34 | 
  35 |   test('编辑Banner', async ({ adminPage }) => {
  36 |     await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
  37 |     await adminPage.waitForTimeout(2000)
  38 | 
  39 |     // 点击第一行的编辑按钮
  40 |     const editBtn = adminPage.locator('.el-table__body-wrapper .el-table__row').first().getByRole('button', { name: '编辑' })
  41 |     if (await editBtn.isVisible()) {
  42 |       await editBtn.click()
  43 |       await expect(adminPage.locator('.el-dialog__title')).toContainText('编辑Banner')
  44 | 
  45 |       await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试Banner-已编辑')
  46 |       await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '更新' }).click()
  47 | 
  48 |       await expect(adminPage.getByText('Banner更新成功')).toBeVisible({ timeout: 10000 })
  49 |     }
  50 |   })
  51 | 
  52 |   test('Banner列表展示', async ({ adminPage }) => {
  53 |     await adminPage.locator('.el-menu-item').filter({ hasText: 'Banner管理' }).click()
  54 |     await adminPage.waitForTimeout(2000)
  55 | 
  56 |     // 验证页面标题
  57 |     await expect(adminPage.locator('h1')).toContainText('Banner管理')
  58 |   })
  59 | })
  60 | 
```