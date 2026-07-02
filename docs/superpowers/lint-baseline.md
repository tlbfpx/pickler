# Lint Baseline (2026-07-02)

`hey-pickler-admin` 当前 lint 状态快照，用于**防止新增** warning 而非清理历史欠债。

## 状态

```
$ npm run lint:check
✖ 78 problems (0 errors, 78 warnings)
```

- **0 errors** ✅
- **78 warnings**（全部预存在，本 PR 未引入任何新 warning）
- `npm run lint --fix` 已清掉所有可自动修的格式问题
- CI 强制"不增量"纪律：`.github/workflows/ci.yml` 加了 `lint:baseline:check` 步骤

## 分布（按规则）

| 规则 | 数量 | 说明 |
|---|---|---|
| `@typescript-eslint/no-explicit-any` | **76** | `as any` 类型断言（API 模块 + 组件 props + Playwright 1 处） |
| `@typescript-eslint/ban-types` | **2** | `{}` 空接口（`env.d.ts` 内的 vue shim） |

## 规则

**禁止新增 lint warning**。在改动已有文件时，**该文件原有的 warning 数量不应增加**，新写的文件应**零 warning**。

## 工具

`hey-pickler-admin/scripts/check-lint-baseline.mjs` 是纪律的执行者：

```bash
# CI 跑这个（任何新 warning 会让 exit 1）
npm run lint:baseline:check

# 合法清理了预存债后，重新生成 baseline 并提交
npm run lint:baseline:update
```

baseline 的"指纹"是 `(file:line:col:ruleId)` 四元组——只要这个组合没变，message 文字可以调整、文件可以重命名，baseline 都不需要更新。

## CI 集成

`.github/workflows/ci.yml` 的 `frontend` job：

1. `npm run lint:check`（保留 `continue-on-error: true` — 历史 warning 不阻塞合并，但跑出来可见）
2. `npm run lint:baseline:check`（**无 `continue-on-error`** — 任何新 warning 直接 fail 这次 PR）
3. `npm run build`

## 历史

| 时间 | 改动 | warning 数 |
|---|---|---|
| PR #20 之前 | 历史欠债（项目初版到 2026-06） | ~301（其中 1 error + 300 warnings） |
| PR #20 收尾 (`137c4563`) | `npm run lint --fix` 清掉可自动修的格式问题 + 移除 1 个 error | 75 warnings |
| `4d09d270`（chore） | 标记 baseline + 写说明 | 77 warnings（含 `env.d.ts` 2 个 ban-types） |
| 本提交 | 升级为 JSON + CI 加严格 check | 78 warnings（baseline 由结构化脚本生成） |

> PR #20 期间所有 18 个 commit 严守"不增量"纪律。

## 完整指纹

`lint-baseline.json`（同目录，78 个 `{file,line,col,rule,message}` 条目）由 `npm run lint:baseline:update` 生成，排序为 `file → line → col → rule`。
