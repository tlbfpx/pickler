# Hey Pickler 上线排期（v1.0 Launch）

> ⚠️ **场景说明（2026-07-21 更新）**：项目当前**只做本机部署**，公网上线暂不推进。
> milestone `v1.0-launch` 的 20 个 issue（#78–#97）已全部 **wontfix 关闭**（`closed=20 / open=0`）。
> 本文档保留作**未来公网上线时的参考**（排期 / 泳道 / 依赖关系仍有效）。
> 本机部署请改看 [README §七 本地开发快速启动](../README.md) + `scripts/dev-up.sh`。

> 把 [`RELEASE-CHECKLIST.md`](./RELEASE-CHECKLIST.md) 的检查项按 **关键路径 + 并行泳道** 重新组织，配上工期 / 前置依赖 / 负责角色 / 验收勾选。
>
> 配合 [`RUNBOOK.md`](./RUNBOOK.md)（运维排障）、[`CREDENTIALS.md`](./CREDENTIALS.md)（密钥管理）、[`DEPLOYMENT-REQUIREMENTS.md`](./DEPLOYMENT-REQUIREMENTS.md)（硬件 / Nginx 清单）使用。
>
> **执行跟踪**：GitHub milestone `v1.0-launch`。下表每个 `#slug` 对应一个 issue（title 前缀 `[slug]`），泳道 = label（`track-A-合规` / `track-B-CICD` / `track-C-基建` / `track-D-验收`）。

---

## 状态总览

代码层已 sign-off（标准化 PR#55–67、生产就绪 v19、Dashboard Phase 1+2、review #3/#4 安全修复全部入 master），OpenSpec 无进行中变更。**剩余工作全部是「上线运维 + 外部依赖」，无一行业务代码需要再写。** 瓶颈在 A1（ICP 备案，1–3 周）。

---

## 关键路径总览

```
Day 0 ──► A1 ICP 备案(1–3周) ──► A2 SSL+域名(1–3天) ──► A3 wxapp 审核(1–7天) ──► A4 发布
   │                                                                   ▲
   ├──► B1 NVD_API_KEY(并行)                                            │
   └──► 备案等待期 1–3 周内并行铺完 B + C 泳道 ──► D 验收就绪 ──────────┘
```

整体 4–5 周见发布，瓶颈 = A1。

---

## Day-0 立即启动（今天就做，互不阻塞）

- [ ] **#A1 域名 ICP 备案** — 关键路径起点，最长前置项
- [ ] **#B1 申请 NVD_API_KEY** — 补 CI 唯一缺口（CVE 门禁否则长期空转）

---

## 泳道 A · 合规与发布（关键路径，串行）

| # | 事项 | 负责 | 工期 | 前置 | 验收 |
|---|---|---|---|---|---|
| A1 | 域名 ICP 备案 | 法务/运维 | 1–3 周 | 无 | 工信部备案通过、域名可解析 |
| A2 | SSL 证书 + HTTPS 域名 | 运维 | 1–3 天 | A1 | `https://<prod-domain>` 证书有效 |
| A3 | wxapp `app.js` 换 `PROD_BASE_URL` + 上传 + 提交审核 | 小程序 | 1–7 天 | A2 | 微信平台显示「审核通过」 |
| A4 | wxapp 正式发布 | 小程序 | 即时 | A3 + D5 | 小程序线上可访问、API 通 |

---

## 泳道 B · CI/CD 接通（备案等待期并行）

> `deploy.yml`（v19 已重写）已是完整的 staging→production 双环境 workflow。本泳道是把它「接通」——配 secrets + 写服务器侧接收端 + 配 Environments 审批，**不改 workflow 代码**。

| # | 事项 | 负责 | 工期 | 前置 | 验收 |
|---|---|---|---|---|---|
| B1 | 申请 NVD_API_KEY → 配 GitHub secret | 后端 | 几天–1 周 | 无 | CI `dep-check-ci` profile 跑通、无 `NoDataException` |
| B2 | 开通容器仓库（GHCR / 阿里云 ACR）+ 配 `REGISTRY` / `REGISTRY_USER` / `REGISTRY_PASS` | 运维 | 0.5 天 | 无 | `docker push` 到仓库成功 |
| B3 | 服务器侧 deploy-hook 接收端（拉镜像 + 重启 + health 等待）+ 配 `STAGING_DEPLOY_HOOK` / `PRODUCTION_DEPLOY_HOOK` / `STAGING_URL` / `PRODUCTION_URL` + GitHub Environments 给 `production` 配 required reviewers | 运维 | 1–2 天 | B2 | hook 触发后服务重启、`/actuator/health` UP |
| B4 | 配 `DB_BACKUP_HOOK`（接生产库 `backup-db.sh`） | 运维 | 0.5 天 | C4 | production deploy 前自动备份触发 |
| B5 | **staging 首次部署验证**（`deploy.yml` → environment=staging） | 运维+后端 | 0.5 天 | B1–B4 + C1 | pipeline 全绿、smoke-test.sh 通过 |

---

## 泳道 C · 基础设施 + 运维（备案等待期并行）

| # | 事项 | 负责 | 工期 | 前置 | 验收 |
|---|---|---|---|---|---|
| C1 | 服务器/云主机 + MySQL ≥8.0.16（max_connections≥500）+ Redis ≥6（maxmemory≥512MB、allkeys-lru、持久化）+ Nginx | 运维 | 1–2 天 | 无 | 三服务健康、版本达标 |
| C2 | 生成生产密钥（`JWT_SECRET`≥32 / `AES_KEY` 精确 16/24/32 / `DB_PASSWORD` / `REDIS_PASSWORD`）+ 配 `WX_DEV_MODE=false` / `CORS_ADMIN_ORIGINS` / `SPRING_PROFILES_ACTIVE=prod` / `PROD_GUARD=true` + `INITIAL_ADMIN_*`（首登改密） | 后端 | 0.5 天 | 无 | 启动日志 `Profile guard passed` |
| C3 | `npm ci && npm run build` 出 admin `dist/` + Nginx 托管 + `/api` 反代 8080 | 前端/运维 | 0.5 天 | C1 | admin 面板可访问、登录通 |
| C4 | `backup-db.sh` → cron 02:30 + OSS 上传 + 跨 region 存储 + **恢复演练** | 运维 | 1 天 | C1 | 演练库从备份恢复成功 |
| C5 | `health-check.sh` → cron 每分钟 + `ALERT_WEBHOOK`（钉钉/Slack） | 运维 | 0.5 天 | C1 | 服务 down 触发告警 |
| C6 | 监控告警接入（5xx>1% / p99>2s / JVM heap>85% / slow query>10min / Redis>80% / 429>100min） | 运维 | 1–2 天 | C1 | 各指标阈值触发告警 |

---

## 终点 D · 生产部署 + 验收

| # | 事项 | 负责 | 前置 | 验收 |
|---|---|---|---|---|
| D1 | staging 全链路烟测（含批量签到、summary 卡片、配置驱动） | QA | B5 + C3 | 全链路无阻断 |
| D2 | **production 部署**（`deploy.yml` → environment=production，审批门禁） | 运维 | D1 通过 | pipeline 全绿、smoke 通过 |
| D3 | 验证 V21 migration（`login_log` / `access_log` 两表 + 字段齐全） | 后端 | D2 | `SHOW TABLES` + `DESC` 两表 OK |
| D4 | `smoke-test.sh`（无 token + 带 `ADMIN_TOKEN`）+ 人工 smoke | QA | D2 | 全绿 |
| D5 | wxapp devtools 全链路回归 | 小程序 | D2 | 三端一致 |

---

## deploy.yml 接通对照（属 B 泳道，单列方便对照 workflow）

workflow 已就绪（v19），缺以下 secrets / 配置才会真正 ship（未配时 pipeline 只 build 不 ship、发 `::warning::`，属安全降级）：

| 引用 | secret / 配置 | 归属 issue |
|---|---|---|
| `REGISTRY` / `REGISTRY_USER` / `REGISTRY_PASS` | 容器仓库凭证 | B2 |
| `STAGING_DEPLOY_HOOK` / `PRODUCTION_DEPLOY_HOOK` | 服务器侧拉镜像重启 webhook | B3 |
| `STAGING_URL` / `PRODUCTION_URL` | post-deploy smoke 目标 | B3 |
| `DB_BACKUP_HOOK` | pre-deploy 备份触发 | B4 |
| Environments → `production` required reviewers | 手动审批门禁 | B3 |

---

## 工期与瓶颈分析

- **关键路径**：A1 → A2 → A3 → A4，串行 4–5 周。**备案审核期间项目处于等待态，应在此窗口铺完 B + C 泳道**（B+C 合计约 1–2 周工作量，窗口充裕）。
- **瓶颈**：A1 ICP 备案。代码再好也卡在这里，**Day 0 必须启动**。
- **CI 唯一代码缺口**：B1（NVD_API_KEY），其余 B 项是配置 / 脚本接通。
- **唯一有实质开发量的**：B3 的 deploy-hook 接收端（服务器侧拉镜像 + 重启脚本，1–2 天）。

---

## 参考

- [`RELEASE-CHECKLIST.md`](./RELEASE-CHECKLIST.md) — 上线逐项勾选清单（与本文档一一对应）
- [`RUNBOOK.md`](./RUNBOOK.md) — 运维排障（5 分钟定位、指标、回滚）
- [`CREDENTIALS.md`](./CREDENTIALS.md) — 密钥生成 / 轮换 / 应急响应
- [`DEPLOYMENT-REQUIREMENTS.md`](./DEPLOYMENT-REQUIREMENTS.md) — 硬件 / 软件 / Nginx / 端口清单
- `.github/workflows/deploy.yml` — staging→production 双环境 CD（v19 已就绪）
- `scripts/` — `smoke-test.sh` / `backup-db.sh` / `health-check.sh` / `cve-gate.sh` / `perf-baseline.sh`
- GitHub milestone **`v1.0-launch`** — 执行跟踪
