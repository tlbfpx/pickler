# Proposal: 部署自动化与运维 runbook（deployment-automation）

## Why

`docs/DEPLOYMENT-REQUIREMENTS.md` 描述了硬件、软件清单、`java -jar` 手工启动命令，但客户拿到二进制后**没有任何工程化交付物**能直接落地：

1. **无进程守护**：`java -jar` 手工跑，进程崩了没人重启，服务器重启后不自启
2. **无备份与恢复**：MySQL / Redis 没有备份脚本，没有恢复演练流程，出事故客户无法回滚
3. **Nginx 模板不全**：DEPLOYMENT-REQUIREMENTS 给的配置缺 `ssl_protocols` / `client_max_body_size` / 安全 header / 日志格式
4. **无运维 runbook**：客户运营团队接手后无故障响应手册，所有问题都要打电话问开发
5. **无交付物清单**：客户验收无凭据，不知道最终应该收到哪些文件

这是 5 个 Phase 1 交付变更中的第 4 个（前 3 个：secure-credentials-parameterization 已交付，PR#10 等待 sign-off；可观测性 / 测试覆盖 / CI-CD 待启动）。本变更目标是把「代码能跑」升级为「客户能独立部署运营」。

**目标部署环境**：阿里云 ECS（Ubuntu 22.04+ / Alibaba Cloud Linux 3），备份走阿里云 OSS。其他云厂商不在本变更范围。

## What Changes

### 部署工件（Deployment Artifacts）

- **`deploy/systemd/hey-pickler.service`**：systemd unit 文件，含 `User=heypickler` / `Restart=on-failure` / `RestartSec=10` / `After=mysql.service redis.service` / JVM 参数（`-Xmx` / `-XX:+UseGZipGC`）/ 环境变量文件 `EnvironmentFile=/etc/heypickler/heypickler.env`
- **`deploy/nginx/heypickler.conf`**：可部署的 Nginx 参考配置（admin + api 双 vhost），含 `ssl_protocols TLSv1.2 TLSv1.3` / `client_max_body_size 10m` / `gzip` / HSTS / X-Frame-Options / `proxy_read_timeout 60s` / access log format
- **`deploy/scripts/install.sh`**：首次部署脚本（创建用户、目录、拷贝 systemd unit、启用服务、首次启动校验）

> **范围排除**：本变更不做 Docker 容器化。客户如果是 K8s/容器化场景，留给 Phase 2 单独交付（与可观测性变更合并可能更合理）。

### 运维 Runbook

**`docs/RUNBOOK.md`**，覆盖以下 5 个场景：

1. **首次部署**：从 jar 到服务运行（10 步：建用户→拷贝→配置 env→启动→smoke test）
2. **日常运营**：日志位置、日志轮转、查看实时日志、重启服务、查看健康状态
3. **备份与恢复**：
   - MySQL 每日全量 + binlog 增量（`mysqldump --single-transaction --master-data=2`）
   - 保留 30 天滚动
   - 恢复演练步骤（季度执行一次）
4. **限流调优**：Redis key 命名规则、`hey-pickler.rate-limit.*` 各维度含义、调整后 reload 方式
5. **常见故障响应**：
   - 5xx 飙升（应用日志 + DB 慢查询 + Redis 延迟）
   - DB 主从切换 / Redis 故障 / 磁盘满 / OOM kill

### 交付物清单

**`docs/DELIVERABLES.md`**：客户验收清单，分类列出：

| 类别 | 工件 | 验收标准 |
|------|------|---------|
| 源代码 | git tag / branch / commit SHA | 可重复构建 |
| 二进制 | `hey-pickler-server-1.0.0.jar` / admin `dist/` | 启动成功 |
| 部署工件 | systemd unit / nginx conf / install.sh | 跑通 smoke test |
| 文档 | README / DEPLOYMENT-REQUIREMENTS / CREDENTIALS / RUNBOOK / DELIVERABLES | 客户运营可独立读懂 |
| 测试报告 | unit + integration + e2e 通过率 | 满足验收门禁 |
| 数据库 | V1-V8 migration 脚本 + 升级 SQL（如有） | 全新 DB 可重建 |

## Impact

**新增文件（5）**：
- `deploy/systemd/hey-pickler.service`
- `deploy/nginx/heypickler.conf`
- `deploy/scripts/install.sh`
- `docs/RUNBOOK.md`
- `docs/DELIVERABLES.md`

**修改文件（1）**：
- `docs/DEPLOYMENT-REQUIREMENTS.md`（引用新文件，移除内嵌手工命令，改为 `see deploy/`）

**spec 影响**：
- `infrastructure` spec 新增 4 条 requirement：部署工件 / 反向代理参考配置 / 备份与恢复 / 运维 runbook
- `infrastructure` spec 修改 1 条：将「客户部署文档」从描述性段落升级为「必须提供可执行工件」的强制要求

**影响下游变更**：
- 可观测性变更（#2）会引用本变更的 `deploy/systemd/hey-pickler.service` 作为 JVM 参数注入点
- CI/CD 变更（#5）会在 `.github/workflows/release.yml` 中引用本变更的 `deploy/scripts/install.sh` 作为部署入口

## Decisions

### D1: 部署工件用 systemd 还是 Docker？

**选 systemd**。理由：

1. DEPLOYMENT-REQUIREMENTS.md 现有内容已经按 `java -jar` 思路写，systemd 是自然延伸
2. 客户是企业客户，可能对 Docker daemon 依赖敏感（额外攻击面、运维成本）
3. systemd 是 Linux 原生，Ubuntu 22.04+ / CentOS 8+ 直接可用，无依赖
4. Docker 容器化涉及镜像构建、registry、K8s 编排，scope 会膨胀，留 Phase 2

**Docker 影响范围**：如果客户后期要求容器化，可以补 Dockerfile 在 `deploy/docker/`，systemd unit 继续保留作为非容器化选项。两套并存不冲突。

### D2: 备份目标位置

**阿里云 OSS（主）+ ECS 本地临时盘（中转）**。理由：

1. 部署环境已锁定阿里云 ECS，OSS 是天然搭配（同区域内网传输免流量费）
2. runbook 给出 `ossutil` 完整命令链路：mysqldump → 本地 gzip → `ossutil cp` 到 OSS bucket → 本地保留 7 天 / OSS 保留 30 天滚动
3. 客户需自建 OSS bucket + RAM 子账号 + AccessKey，runbook 第 3 章给出步骤
4. 跨云迁移场景（如客户未来想搬腾讯云）需重写备份脚本，但 Phase 1 不考虑

### D3: Nginx 配置写在 docs/ 还是 deploy/？

**`deploy/nginx/heypickler.conf`**（部署工件目录，可执行），DEPLOYMENT-REQUIREMENTS.md 只引用不内嵌。

理由：docs/ 是说明文档，deploy/ 是可执行工件。客户运维 `cp deploy/nginx/heypickler.conf /etc/nginx/sites-available/` 直接用，不需要从 markdown 复制粘贴。

### D4: install.sh 做多少事情？

**仅做「首次部署」最小集合**：

- 创建 `heypickler` 系统用户
- 创建 `/opt/heypickler/` / `/var/log/heypickler/` / `/etc/heypickler/` 目录
- 拷贝 systemd unit 到 `/etc/systemd/system/`
- 提示运维填入 `/etc/heypickler/heypickler.env`（不自动生成，避免硬编码）
- `systemctl daemon-reload && systemctl enable hey-pickler`
- **不**自动 `systemctl start`（需人工确认 env 文件后启动）

不做：jar 拷贝（客户用 scp/rsync 自己传）、SSL 证书申请（需域名 + DNS）、防火墙配置（UFW/iptables 客户自己定）。

### D5: 交付物清单（DELIVERABLES.md）覆盖范围？

**覆盖代码 + 二进制 + 部署 + 文档 + 测试 + 数据库 6 类**，每类列具体工件 + 验收标准。验收标准用「客户运营在无开发协助下能完成的动作」描述（如「能在 30 分钟内完成首次部署」），不用「文件存在」这种弱标准。

不做：
- 不给 SLA 数字（如 99.9% uptime）——那是合同条款，不是技术交付物
- 不给性能基准数字——那是测试覆盖变更（#3）的范围

## Open Questions

D1-D4 已确认。D5（DELIVERABLES.md 是否加测试通过率硬阈值）默认按推荐走：**仅要求测试报告，不强制百分比**。如果客户合同里有 SLA / 测试覆盖率条款，再追加。

## Risks

- **systemd unit 在客户发行版上不兼容**：仅测试 Ubuntu 22.04 / CentOS 8，客户若用其他发行版（如 Debian、Rocky Linux）需自行调整。runbook 注明。
- **install.sh 在受限环境下失败**：如 SELinux enforcing 模式、无 sudo 权限等。脚本前置检查 + 失败时清晰提示。
- **Nginx 配置在客户既有 Nginx 版本上 syntax 不兼容**：仅测试 Nginx 1.20+，runbook 注明。

## Out of Scope

- Docker 容器化（Phase 2）
- K8s Helm chart（Phase 2）
- SSL 证书自动申请（certbot 集成留给客户运营）
- 监控系统集成（Prometheus / Grafana / Sentry）——属于变更 #2 可观测性
- CI/CD pipeline——属于变更 #5
- 性能压测脚本——属于变更 #3
