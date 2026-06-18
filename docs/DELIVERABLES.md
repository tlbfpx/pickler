# 交付物清单（DELIVERABLES）

> 客户验收清单。每个工件配「验收动作」，验收动作用「客户运营在无开发协助下能完成的动作」描述，不用「文件存在」这种弱标准。
>
> 验收流程：客户运营逐项执行「验收动作」 → 全部通过 → 签字栏签字 → 交付完成。

---

## 1. 源代码

| 工件 | 位置 | 验收动作 |
|------|------|---------|
| Git tag | `git tag v1.0.0` | 客户运营在干净环境 `git clone` + `git checkout v1.0.0`，能成功执行 `mvn clean package` 与 `npm ci && npm run build` |
| 分支 | `master`（默认）+ `feature/*`（开发分支，归档后保留）| 客户能浏览 commit history 看到完整变更轨迹 |
| 提交记录 | `git log --oneline` | 每个 commit 信息清晰，符合 `<type>: <desc>` 格式 |

**验收动作（step-by-step）**：

```bash
# 1. 拉代码
git clone <repo-url> /tmp/heypickler-verify
cd /tmp/heypickler-verify
git checkout v1.0.0

# 2. 后端构建
cd hey-pickler-server
mvn clean package -DskipTests
ls target/hey-pickler-server-1.0.0.jar  # 必须存在

# 3. 前端构建
cd ../hey-pickler-admin
npm ci
npm run build
ls dist/index.html  # 必须存在
```

---

## 2. 二进制

| 工件 | 位置 | 验收动作 |
|------|------|---------|
| 后端 jar | `hey-pickler-server-1.0.0.jar` | 在 JDK 17+ 环境 `java -jar` 启动，监听 8080 端口，日志显示 `Started HeyPicklerApplication` |
| admin 前端 | `hey-pickler-admin/dist.zip`（构建产物打包）| Nginx 指向 dist/，浏览器访问 `https://admin.heypickler.com` 看到登录页 |

**验收动作**：

```bash
# 后端
java -version  # 17+
java -jar hey-pickler-server-1.0.0.jar &
sleep 15
curl -sf -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" -d '{}' | grep -q 'code' && echo OK

# 前端（假设 nginx 已配置 root /var/www/.../dist/）
curl -sf https://admin.heypickler.com/ | grep -q '<title>' && echo OK
```

---

## 3. 部署工件

| 工件 | 位置 | 验收动作 |
|------|------|---------|
| systemd unit | `deploy/systemd/hey-pickler.service` | 部署后 `systemctl status hey-pickler` 显示 `active (running)` |
| Nginx 配置 | `deploy/nginx/heypickler.conf` | 部署后 `nginx -t` 通过 + 浏览器访问双域名看到正确响应 |
| install.sh | `deploy/scripts/install.sh` | 在干净 ECS 上 `sudo bash install.sh` 完整跑通（5 步全 OK），重跑一次确认幂等 |
| logrotate 配置 | `deploy/logrotate/heypickler` | `logrotate -d /etc/logrotate.d/heypickler` dry-run 通过 |
| MySQL 备份脚本 | `deploy/scripts/backup-mysql.sh` | 手动跑一次产出 `.sql.gz` 文件 ≥1KB + OSS 可见 |
| MySQL 恢复脚本 | `deploy/scripts/restore-mysql.sh` | 在测试 DB 上从备份恢复成功 + 关键表行数与原库一致 |

**验收动作（端到端）**：

```bash
# 1. 在干净 ECS 上执行 install.sh
sudo bash deploy/scripts/install.sh
# 期望：5 步全部 [INFO] OK，结尾打印下一步指引

# 2. 配置 env + 上传 jar（按 RUNBOOK §1.3-1.4）

# 3. 启动
sudo systemctl start hey-pickler
systemctl is-active hey-pickler  # 期望 active

# 4. Nginx 部署
sudo cp deploy/nginx/heypickler.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/heypickler.conf /etc/nginx/sites-enabled/
sudo nginx -t  # 期望 syntax is ok / test is successful

# 5. 备份脚本
sudo bash deploy/scripts/backup-mysql.sh
ls -lh /var/backups/heypickler/mysql/*.sql.gz  # 期望有今天的文件
ossutil ls oss://heypickler-backup/mysql/ | tail -5  # 期望 OSS 可见

# 6. logrotate dry-run
sudo logrotate -d /etc/logrotate.d/heypickler 2>&1 | tail -5
```

---

## 4. 文档

| 工件 | 验收标准 |
|------|---------|
| `README.md` | 项目概述 / 技术栈 / 系统架构 / 部署入口完整 |
| `docs/DEPLOYMENT-REQUIREMENTS.md` | 部署前硬件软件清单 + 引用 deploy/ 与 RUNBOOK |
| `docs/CREDENTIALS.md` | 4 大场景：首部署 / 升级 / 密钥轮换 / 应急响应 |
| `docs/RUNBOOK.md` | 5 章节运维手册 + 验收动作可执行 |
| `docs/DELIVERABLES.md` | 本文件，6 类工件清单 |
| `CLAUDE.md` | 开发指南（架构 / 环境变量 / workflow）|
| `.env.example` | 环境变量模板 |
| OpenSpec specs | `openspec/specs/` 含 8 个能力域的最新 spec |
| OpenSpec archive | `openspec/changes/archive/` 含已交付变更的完整四件套 |

**验收动作**：

- **客户运营**通读 `README.md` + `docs/DEPLOYMENT-REQUIREMENTS.md` + `docs/RUNBOOK.md` + `docs/CREDENTIALS.md`，能独立：
  - 解释项目目标与架构
  - 完成 ECS 选型与软件准备
  - 执行首次部署（§1）
  - 处理常见故障（§5）
  - 完成备份恢复演练（§3.5）

- **客户开发**通读 `CLAUDE.md` + `openspec/specs/`，能独立：
  - 搭建本地开发环境
  - 找到对应能力的 spec requirement 与 scenario
  - 遵循 OpenSpec + Superpowers workflow 提交变更

---

## 5. 测试报告

| 工件 | 验收标准 |
|------|---------|
| 后端单元测试 | `mvn test -Dtest='!*IntegrationTest'` 全绿 |
| 后端集成测试 | `mvn test -Dtest='*IntegrationTest'` 全绿（除已知的 flaky case，附录说明）|
| 后端测试报告 | GitHub Actions CI 自动执行，PR 绿勾即通过；本变更不强制覆盖率阈值 |
| 前端 e2e 测试 | `npm run test:e2e` 全绿 |
| 端到端冒烟测试 | 部署后人工执行 RUNBOOK §1.4.4 + §1.5.4 通过 |
| CI 流水线 | `.github/workflows/ci.yml` 双 job（backend + frontend）在 PR 上自动跑绿（PR #12 已交付） |
| 安全审计 | 凭据审计（CREDENTIALS.md 已覆盖）；静态扫描留待后续 |

**验收动作**：

```bash
# 后端
cd hey-pickler-server
mvn test -Dtest='!*IntegrationTest' 2>&1 | tail -5
# 期望：BUILD SUCCESS + Tests run: N, Failures: 0, Errors: 0

# 前端
cd hey-pickler-admin
npm run test:e2e 2>&1 | tail -10
# 期望：N passed (N passed)
```

CI 自动归档：PR #12 引入的 GitHub Actions workflow 会在每次 push / PR 自动跑 backend 单元 + 集成测试（MySQL 8 + Redis 6 service container）+ frontend `lint:check` + build，跑绿即作为合并门槛。客户可在 GitHub Actions UI 查看历次 run 的日志与测试结果。

---

## 6. 数据库

| 工件 | 验收标准 |
|------|---------|
| Flyway migrations | `src/main/resources/db/migration/V1__init_schema.sql` 到 `V8__add_operation_log.sql` |
| 全新 schema 部署 | 在空 DB 上启动后端，Flyway 自动跑完 V1-V8，关键表存在 |
| 升级路径文档 | `docs/CREDENTIALS.md` 含 V2 checksum 修复 SQL（secure-credentials 变更引入）|
| 初始管理员账号 | 通过 `AdminBootstrapper` 从 `INITIAL_ADMIN_PASSWORD` 注入，不再硬编码 |

**验收动作**：

```bash
# 1. 全新 schema 重建
mysql -u root -p -e "DROP DATABASE IF EXISTS hey_pickler_verify;
                     CREATE DATABASE hey_pickler_verify DEFAULT CHARACTER SET utf8mb4;"

# 2. 配置 env 指向新 schema + 启动
sudo vi /etc/heypickler/heypickler.env
# 改 DB_URL 的 schema 名为 hey_pickler_verify
sudo systemctl restart hey-pickler

# 3. 验证 Flyway 跑完
mysql -u root -p hey_pickler_verify -e "SHOW TABLES;"
# 期望：admin_user / event / registration / point_record / ranking /
#       banner / ban_record / operation_log / flyway_schema_history

mysql -u root -p hey_pickler_verify -e "SELECT version, success FROM flyway_schema_history;"
# 期望：V1-V8 全 success=1

# 4. 验证初始 admin
mysql -u root -p hey_pickler_verify -e "SELECT username, role FROM admin_user;"
# 期望：admin / SUPER_ADMIN（由 AdminBootstrapper 创建）
```

---

## 验收签字

| 类别 | 状态 | 客户方签字 | 日期 | 我方签字 | 日期 |
|------|------|-----------|------|---------|------|
| 1. 源代码 | □ | | | | |
| 2. 二进制 | □ | | | | |
| 3. 部署工件 | □ | | | | |
| 4. 文档 | □ | | | | |
| 5. 测试报告 | □ | | | | |
| 6. 数据库 | □ | | | | |

**最终交付完成日期**：__________

**备注**：

---

## 附录：已交付变更追溯

| 变更 | OpenSpec archive | PR | 范围 |
|------|-----------------|----|----|
| secure-credentials-parameterization | `2026-06-18-secure-credentials-parameterization` | #10 | 凭据参数化 + 启动校验 |
| deployment-automation | `2026-06-18-deployment-automation` | #11 | systemd + Nginx + install.sh + RUNBOOK + 本文件 |
| cicd-pipeline | `2026-06-18-add-cicd-pipeline` | #12 | GitHub Actions CI workflow + lint:check script + lint 债务清理 |

**已跳过变更**：

- 变更 #2：可观测性（actuator / 结构化日志 / Sentry）— 客户决定跳过，留待 Phase 2 按需评估

**待交付变更**（不阻塞本次验收）：

- 变更 #3：核心业务测试覆盖
- 变更 #5 剩余项：API 版本化（`/v1/` 前缀）+ 自动 CD（推迟 Phase 2）

详见 `docs/RUNBOOK.md` 附录 D「Phase 2 待办」。
