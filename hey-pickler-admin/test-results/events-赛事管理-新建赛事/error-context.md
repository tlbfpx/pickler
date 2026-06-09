# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: events.spec.ts >> 赛事管理 >> 新建赛事
- Location: e2e/events.spec.ts:15:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('.el-date-picker .el-icon-arrow-right').first()

```

# Page snapshot

```yaml
- generic [ref=e1]:
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
          - heading "赛事管理" [level=1] [ref=e56]
          - button "新建赛事" [ref=e57] [cursor=pointer]:
            - generic [ref=e58]:
              - img [ref=e60]
              - text: 新建赛事
        - generic [ref=e62]:
          - generic [ref=e63]:
            - generic [ref=e65] [cursor=pointer]:
              - generic:
                - combobox [ref=e67]
                - generic [ref=e68]: 筛选类型
              - img [ref=e71]
            - generic [ref=e74] [cursor=pointer]:
              - generic:
                - combobox [ref=e76]
                - generic [ref=e77]: 筛选状态
              - img [ref=e80]
          - generic [ref=e83]:
            - table [ref=e85]:
              - rowgroup [ref=e97]:
                - row "ID 类型 标题 地点 比赛时间 最大人数 当前人数 状态 费用 操作" [ref=e98]:
                  - columnheader "ID" [ref=e99]:
                    - generic [ref=e100]: ID
                  - columnheader "类型" [ref=e101]:
                    - generic [ref=e102]: 类型
                  - columnheader "标题" [ref=e103]:
                    - generic [ref=e104]: 标题
                  - columnheader "地点" [ref=e105]:
                    - generic [ref=e106]: 地点
                  - columnheader "比赛时间" [ref=e107]:
                    - generic [ref=e108]: 比赛时间
                  - columnheader "最大人数" [ref=e109]:
                    - generic [ref=e110]: 最大人数
                  - columnheader "当前人数" [ref=e111]:
                    - generic [ref=e112]: 当前人数
                  - columnheader "状态" [ref=e113]:
                    - generic [ref=e114]: 状态
                  - columnheader "费用" [ref=e115]:
                    - generic [ref=e116]: 费用
                  - columnheader "操作" [ref=e117]:
                    - generic [ref=e118]: 操作
            - generic [ref=e122]:
              - table:
                - rowgroup
              - generic [ref=e124]: No Data
          - generic [ref=e126]:
            - generic [ref=e127]: Total 0
            - generic [ref=e130] [cursor=pointer]:
              - generic:
                - combobox [ref=e132]
                - generic [ref=e133]: 10/page
              - img [ref=e136]
            - button "Go to previous page" [disabled] [ref=e138]:
              - generic:
                - img
            - list [ref=e139]:
              - listitem "page 1" [ref=e140]: "1"
            - button "Go to next page" [disabled] [ref=e141]:
              - generic:
                - img
            - generic [ref=e142]:
              - generic [ref=e143]: Go to
              - spinbutton "Page" [ref=e146]: "1"
        - dialog "新建赛事" [ref=e148]:
          - generic [ref=e149]:
            - banner [ref=e150]:
              - heading "新建赛事" [level=2] [ref=e151]
              - button "Close this dialog" [ref=e152] [cursor=pointer]:
                - img [ref=e154]
            - generic [ref=e157]:
              - generic [ref=e158]:
                - generic [ref=e159]: "*赛事类型"
                - generic [ref=e162] [cursor=pointer]:
                  - generic:
                    - combobox "*赛事类型" [ref=e164]
                    - generic [ref=e165]: 明星赛
                  - img [ref=e168]
              - generic [ref=e170]:
                - generic [ref=e171]: "*标题"
                - textbox "*标题" [ref=e175]:
                  - /placeholder: 请输入赛事标题
                  - text: E2E测试锦标赛
              - generic [ref=e176]:
                - generic [ref=e177]: "*描述"
                - textbox "*描述" [ref=e180]:
                  - /placeholder: 请输入赛事描述
                  - text: Playwright自动化测试创建的赛事
              - generic [ref=e181]:
                - generic [ref=e182]: "*地点"
                - textbox "*地点" [ref=e186]:
                  - /placeholder: 请输入赛事地点
                  - text: E2E测试球馆
              - generic [ref=e187]:
                - generic [ref=e188]: "*比赛时间"
                - generic [ref=e191]:
                  - img [ref=e194]
                  - combobox "*比赛时间" [expanded] [active] [ref=e198]
              - generic [ref=e199]:
                - generic [ref=e200]: "*报名截止"
                - generic [ref=e203]:
                  - img [ref=e206]
                  - combobox "*报名截止" [ref=e210]
              - generic [ref=e211]:
                - generic [ref=e212]: "*最大人数"
                - generic [ref=e214]:
                  - button "decrease number" [ref=e215] [cursor=pointer]:
                    - img [ref=e217]
                  - button "increase number" [ref=e219] [cursor=pointer]:
                    - img [ref=e221]
                  - spinbutton "*最大人数" [ref=e225]: "50"
              - generic [ref=e226]:
                - generic [ref=e227]: "*费用（元）"
                - generic [ref=e229]:
                  - button "decrease number" [ref=e230]:
                    - img [ref=e232]
                  - button "increase number" [ref=e234] [cursor=pointer]:
                    - img [ref=e236]
                  - spinbutton "*费用（元）" [ref=e240]: "0"
            - contentinfo [ref=e241]:
              - button "取消" [ref=e242] [cursor=pointer]:
                - generic [ref=e243]: 取消
              - button "新建" [ref=e244] [cursor=pointer]:
                - generic [ref=e245]: 新建
  - dialog [ref=e246]:
    - generic [ref=e247]:
      - generic [ref=e249]:
        - generic [ref=e250]:
          - textbox "Select date" [ref=e254]
          - textbox "Select time" [ref=e258]
        - generic [ref=e259]:
          - generic [ref=e260]:
            - button "Previous Year" [ref=e261] [cursor=pointer]:
              - img [ref=e263]
            - button "Previous Month" [ref=e265] [cursor=pointer]:
              - img [ref=e267]
          - button "2026" [ref=e269] [cursor=pointer]
          - button "June" [ref=e270] [cursor=pointer]
          - generic [ref=e271]:
            - button "Next Month" [ref=e272] [cursor=pointer]:
              - img [ref=e274]
            - button "Next Year" [ref=e276] [cursor=pointer]:
              - img [ref=e278]
        - grid "Use the arrow keys and enter to select the day of the month" [ref=e281]:
          - rowgroup [ref=e282]:
            - row "Sunday Monday Tuesday Wednesday Thursday Friday Saturday" [ref=e283]:
              - columnheader "Sunday" [ref=e284]: Sun
              - columnheader "Monday" [ref=e285]: Mon
              - columnheader "Tuesday" [ref=e286]: Tue
              - columnheader "Wednesday" [ref=e287]: Wed
              - columnheader "Thursday" [ref=e288]: Thu
              - columnheader "Friday" [ref=e289]: Fri
              - columnheader "Saturday" [ref=e290]: Sat
            - row "31 1 2 3 4 5 6" [ref=e291]:
              - gridcell "31" [ref=e292] [cursor=pointer]:
                - generic [ref=e294]: "31"
              - gridcell "1" [ref=e295] [cursor=pointer]:
                - generic [ref=e297]: "1"
              - gridcell "2" [ref=e298] [cursor=pointer]:
                - generic [ref=e300]: "2"
              - gridcell "3" [ref=e301] [cursor=pointer]:
                - generic [ref=e303]: "3"
              - gridcell "4" [ref=e304] [cursor=pointer]:
                - generic [ref=e306]: "4"
              - gridcell "5" [ref=e307] [cursor=pointer]:
                - generic [ref=e309]: "5"
              - gridcell "6" [ref=e310] [cursor=pointer]:
                - generic [ref=e312]: "6"
            - row "7 8 9 10 11 12 13" [ref=e313]:
              - gridcell "7" [ref=e314] [cursor=pointer]:
                - generic [ref=e316]: "7"
              - gridcell "8" [ref=e317] [cursor=pointer]:
                - generic [ref=e319]: "8"
              - gridcell "9" [ref=e320] [cursor=pointer]:
                - generic [ref=e322]: "9"
              - gridcell "10" [ref=e323] [cursor=pointer]:
                - generic [ref=e325]: "10"
              - gridcell "11" [ref=e326] [cursor=pointer]:
                - generic [ref=e328]: "11"
              - gridcell "12" [ref=e329] [cursor=pointer]:
                - generic [ref=e331]: "12"
              - gridcell "13" [ref=e332] [cursor=pointer]:
                - generic [ref=e334]: "13"
            - row "14 15 16 17 18 19 20" [ref=e335]:
              - gridcell "14" [ref=e336] [cursor=pointer]:
                - generic [ref=e338]: "14"
              - gridcell "15" [ref=e339] [cursor=pointer]:
                - generic [ref=e341]: "15"
              - gridcell "16" [ref=e342] [cursor=pointer]:
                - generic [ref=e344]: "16"
              - gridcell "17" [ref=e345] [cursor=pointer]:
                - generic [ref=e347]: "17"
              - gridcell "18" [ref=e348] [cursor=pointer]:
                - generic [ref=e350]: "18"
              - gridcell "19" [ref=e351] [cursor=pointer]:
                - generic [ref=e353]: "19"
              - gridcell "20" [ref=e354] [cursor=pointer]:
                - generic [ref=e356]: "20"
            - row "21 22 23 24 25 26 27" [ref=e357]:
              - gridcell "21" [ref=e358] [cursor=pointer]:
                - generic [ref=e360]: "21"
              - gridcell "22" [ref=e361] [cursor=pointer]:
                - generic [ref=e363]: "22"
              - gridcell "23" [ref=e364] [cursor=pointer]:
                - generic [ref=e366]: "23"
              - gridcell "24" [ref=e367] [cursor=pointer]:
                - generic [ref=e369]: "24"
              - gridcell "25" [ref=e370] [cursor=pointer]:
                - generic [ref=e372]: "25"
              - gridcell "26" [ref=e373] [cursor=pointer]:
                - generic [ref=e375]: "26"
              - gridcell "27" [ref=e376] [cursor=pointer]:
                - generic [ref=e378]: "27"
            - row "28 29 30 1 2 3 4" [ref=e379]:
              - gridcell "28" [ref=e380] [cursor=pointer]:
                - generic [ref=e382]: "28"
              - gridcell "29" [ref=e383] [cursor=pointer]:
                - generic [ref=e385]: "29"
              - gridcell "30" [ref=e386] [cursor=pointer]:
                - generic [ref=e388]: "30"
              - gridcell "1" [ref=e389] [cursor=pointer]:
                - generic [ref=e391]: "1"
              - gridcell "2" [ref=e392] [cursor=pointer]:
                - generic [ref=e394]: "2"
              - gridcell "3" [ref=e395] [cursor=pointer]:
                - generic [ref=e397]: "3"
              - gridcell "4" [ref=e398] [cursor=pointer]:
                - generic [ref=e400]: "4"
            - row "5 6 7 8 9 10 11" [ref=e401]:
              - gridcell "5" [ref=e402] [cursor=pointer]:
                - generic [ref=e404]: "5"
              - gridcell "6" [ref=e405] [cursor=pointer]:
                - generic [ref=e407]: "6"
              - gridcell "7" [ref=e408] [cursor=pointer]:
                - generic [ref=e410]: "7"
              - gridcell "8" [ref=e411] [cursor=pointer]:
                - generic [ref=e413]: "8"
              - gridcell "9" [ref=e414] [cursor=pointer]:
                - generic [ref=e416]: "9"
              - gridcell "10" [ref=e417] [cursor=pointer]:
                - generic [ref=e419]: "10"
              - gridcell "11" [ref=e420] [cursor=pointer]:
                - generic [ref=e422]: "11"
      - generic [ref=e423]:
        - button "Now" [ref=e424] [cursor=pointer]:
          - generic [ref=e425]: Now
        - button "OK" [ref=e426] [cursor=pointer]:
          - generic [ref=e427]: OK
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
  14 | test.describe('赛事管理', () => {
  15 |   test('新建赛事', async ({ adminPage }) => {
  16 |     await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
  17 |     await adminPage.waitForTimeout(1000)
  18 | 
  19 |     await adminPage.getByRole('button', { name: '新建赛事' }).click()
  20 |     await expect(adminPage.locator('.el-dialog__title')).toContainText('新建赛事')
  21 | 
  22 |     // 填写赛事表单
  23 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '赛事类型' }).locator('.el-select').click()
  24 |     await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()
  25 | 
  26 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '标题' }).getByRole('textbox').fill('E2E测试锦标赛')
  27 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '描述' }).getByRole('textbox').fill('Playwright自动化测试创建的赛事')
  28 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '地点' }).getByRole('textbox').fill('E2E测试球馆')
  29 | 
  30 |     // 设置比赛时间 - 直接输入日期值
  31 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '比赛时间' }).getByPlaceholder('请选择比赛时间').click()
  32 |     await adminPage.waitForTimeout(500)
  33 |     // 选择下月
> 34 |     await adminPage.locator('.el-date-picker .el-icon-arrow-right').first().click()
     |                                                                             ^ Error: locator.click: Test timeout of 30000ms exceeded.
  35 |     await adminPage.waitForTimeout(300)
  36 |     await adminPage.locator('.el-date-table td.available').first().click()
  37 |     await adminPage.waitForTimeout(300)
  38 |     // 点击确定按钮确认日期
  39 |     const confirmBtns = adminPage.locator('.el-picker-panel__footer .el-button--default')
  40 |     if (await confirmBtns.last().isVisible()) {
  41 |       await confirmBtns.last().click()
  42 |     }
  43 | 
  44 |     // 设置报名截止时间
  45 |     await adminPage.locator('.el-dialog .el-form-item').filter({ hasText: '报名截止' }).getByPlaceholder('请选择报名截止时间').click()
  46 |     await adminPage.waitForTimeout(500)
  47 |     await adminPage.locator('.el-date-picker .el-icon-arrow-right').first().click()
  48 |     await adminPage.waitForTimeout(300)
  49 |     await adminPage.locator('.el-date-table td.available').first().click()
  50 |     await adminPage.waitForTimeout(300)
  51 |     const confirmBtns2 = adminPage.locator('.el-picker-panel__footer .el-button--default')
  52 |     if (await confirmBtns2.last().isVisible()) {
  53 |       await confirmBtns2.last().click()
  54 |     }
  55 | 
  56 |     // 点击对话框内的"新建"按钮
  57 |     await adminPage.locator('.el-dialog__footer').getByRole('button', { name: '新建' }).click()
  58 | 
  59 |     await expect(adminPage.getByText('赛事创建成功')).toBeVisible({ timeout: 10000 })
  60 |   })
  61 | 
  62 |   test('赛事列表展示', async ({ adminPage }) => {
  63 |     await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
  64 |     await adminPage.waitForTimeout(2000)
  65 | 
  66 |     await expect(adminPage.locator('h1')).toContainText('赛事管理')
  67 |   })
  68 | 
  69 |   test('筛选赛事', async ({ adminPage }) => {
  70 |     await adminPage.locator('.el-menu-item').filter({ hasText: '赛事管理' }).click()
  71 |     await adminPage.waitForTimeout(2000)
  72 | 
  73 |     // 使用筛选下拉
  74 |     const typeSelect = adminPage.locator('.filter-bar .el-select').first()
  75 |     if (await typeSelect.isVisible()) {
  76 |       await typeSelect.click()
  77 |       await adminPage.getByRole('option', { name: '明星赛', exact: true }).click()
  78 |       await adminPage.waitForTimeout(2000)
  79 |     }
  80 |   })
  81 | })
  82 | 
```