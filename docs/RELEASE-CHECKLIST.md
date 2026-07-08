# Hey Pickler 上线 Checklist

> 发布到生产前逐项勾选。每项关联具体文档/脚本，勿脱离文档单独执行。
>
> 配合 `docs/RUNBOOK.md`（运维排障）、`docs/CREDENTIALS.md`（密钥管理）、`docs/DEPLOYMENT-REQUIREMENTS.md`（部署清单/硬件）使用。

---

## 一、密钥与后端配置（必做）

- [ ] 申请 NVD_API_KEY（免费：https://nvd.nist.gov/developers/request-an-api-key）并配为 GitHub repo secret —— 否则 CVE 扫描跳过，依赖漏洞状态未知（见 `CREDENTIALS.md` §1.5）
- [ ] 生成生产密钥：`JWT_SECRET`（≥32 字符）、`AES_KEY`（恰好 16/24/32 字节）、`DB_PASSWORD`、`REDIS_PASSWORD`（见 `CREDENTIALS.md` §1.1 的 openssl 命令）
- [ ] 配 `INITIAL_ADMIN_USERNAME` / `INITIAL_ADMIN_PASSWORD`（首次部署；首登后立即改密）
- [ ] 配 `WX_APPID` / `WX_SECRET`，且 `WX_DEV_MODE=false`（生产必须关闭微信鉴权旁路）
- [ ] 配 `CORS_ADMIN_ORIGINS`（生产 admin 面板域名，逗号分隔）
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `PROD_GUARD=true`（防线：dev profile 或 dev secret 进生产时拒绝启动）
- [ ] 启动日志确认：`Profile guard passed (prod profile, secrets verified unique)`

## 二、前端（必做）

- [ ] `hey-pickler-wxapp/app.js`：把 `PROD_BASE_URL` 占位符替换为真实生产域名（必须 HTTPS + 已备案）
- [ ] `hey-pickler-admin`：`npm ci && npm run build` 产出 `dist/`，部署到 Nginx 静态目录
- [ ] Nginx：`/api` 反代到后端 8080，`/` 托管 admin `dist/`（见 `DEPLOYMENT-REQUIREMENTS.md` §6）
- [ ] SSL 证书 + 域名 ICP 备案（微信小程序硬性要求，否则无法发布）
- [ ] wxapp 在微信开发者工具上传 → 提交审核

## 三、基础设施（必做）

- [ ] MySQL ≥ 8.0.16（V12 的 CHECK 约束依赖此版本），`max_connections≥500`，InnoDB + utf8mb4
- [ ] Redis ≥ 6，`maxmemory≥512MB`（生产推荐 1GB），eviction `allkeys-lru`，开启持久化
- [ ] `scripts/backup-db.sh` 接 cron（建议每日 02:30）+ OSS upload hook；保留 ≥30 天
- [ ] mysqldump 全量 + binlog 增量；备份跨 region 存储并验证可恢复
- [ ] `scripts/health-check.sh` 接 cron（每分钟）+ `ALERT_WEBHOOK`（钉钉/Slack）

## 四、部署

- [ ] **staging 先行**：`deploy.yml` workflow_dispatch → environment=staging，跑通后再 production
- [ ] 或手动部署：`mvn clean package -DskipTests` → `java -jar hey-pickler-server-1.0.0.jar --spring.profiles.active=prod`
- [ ] 生产部署前触发 `DB_BACKUP_HOOK`（`deploy.yml` 已含 pre-deploy backup 步骤）

## 五、部署后验证（Smoke Test）

- [ ] `bash scripts/smoke-test.sh` 全绿（health / app 公开端点 / admin 无 token 返回 401）
- [ ] 带 `ADMIN_TOKEN` 再跑一次 smoke-test.sh，确认 admin 端点 200
- [ ] 人工 smoke：admin 登录 → 赛事列表分页 → 详情 summary 卡片 → 报名→批量签到 → wxapp 首页拉取
- [ ] `/actuator/health` 返回 `{"status":"UP"}`

## 六、监控告警（建议上线即配）

| 指标 | 阈值 | 说明 |
|---|---|---|
| `/actuator/health` | 1min | `health-check.sh` 已覆盖 |
| 5xx 错误率 | >1% / 5min | 钉钉告警群 |
| API p99 延迟 | >2s / 5min | 告警群 |
| JVM heap | >85% | 见 RUNBOOK §3 |
| MySQL slow query | >10/min | 见 RUNBOOK §4 |
| Redis memory | >80% | 见 RUNBOOK §5 |
| rate-limit 429 | >100/min | 疑似 brute force |

## 七、回滚预案（出事时照此执行）

- **服务回滚**（保留数据）：见 `RUNBOOK.md` §6.1 —— 回退到上一个稳定 tag
- **数据库回滚**：见 `RUNBOOK.md` §6.2 —— **优先前向修复**（社区版无 `flyway:undo`），破坏性变更用备份恢复
- **紧急停机**：`docker stop hey-pickler-server`（优雅）或 `docker kill`（强杀，不推荐）

---

## 参考

- `docs/RUNBOOK.md` — 运维排障（5 分钟定位清单、指标、回滚）
- `docs/CREDENTIALS.md` — 密钥生成/轮换/应急响应
- `docs/DEPLOYMENT-REQUIREMENTS.md` — 硬件/软件清单、Nginx 配置、端口
- `.env.example` — 环境变量模板
- `scripts/` — `smoke-test.sh` / `backup-db.sh` / `health-check.sh` / `cve-gate.sh` / `perf-baseline.sh`
