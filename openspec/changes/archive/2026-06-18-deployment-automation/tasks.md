# Tasks: deployment-automation

## 实施计划

按依赖顺序组织 7 个原子 commit。每个 commit 必须独立可验证（shellcheck / nginx -t / systemd-analyze verify 等静态检查通过）。

### Day 1 — 部署工件（4 commits）

#### Commit 1: deploy/systemd/hey-pickler.service

**文件**: `deploy/systemd/hey-pickler.service`

**内容**:
- `[Unit]` 段：`After=network.target mysql.service redis.service`，`Wants=mysql.service redis.service`
- `[Service]` 段：
  - `Type=simple`, `User=heypickler`, `Group=heypickler`
  - `WorkingDirectory=/opt/heypickler`
  - `EnvironmentFile=/etc/heypickler/heypickler.env`
  - `ExecStart=/usr/bin/java -Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/heypickler/ -Dfile.encoding=UTF-8 -jar /opt/heypickler/hey-pickler-server-1.0.0.jar`
  - `Restart=on-failure`, `RestartSec=10`, `StartLimitInterval=60`, `StartLimitBurst=3`
  - `StandardOutput=append:/var/log/heypickler/app.log`, `StandardError=append:/var/log/heypickler/app.err.log`
  - `LimitNOFILE=65536`
- `[Install]` 段：`WantedBy=multi-user.target`

**验证**: `systemd-analyze verify deploy/systemd/hey-pickler.service`

**Commit message**: `feat(deploy): 新增 hey-pickler systemd unit 文件`

---

#### Commit 2: deploy/nginx/heypickler.conf

**文件**: `deploy/nginx/heypickler.conf`

**内容**: 双 server block（admin vhost + api vhost），含 SSL/TLS 配置、安全 header、gzip、log_format、access_log 分文件。

**验证**: 
- `nginx -t -c $(pwd)/deploy/nginx/heypickler.conf` (在已装 nginx 的机器上)
- 若无法本地验证，至少 `grep -c 'server_name\|listen\|ssl_protocols' heypickler.conf` 确认关键配置存在

**Commit message**: `feat(deploy): 新增 Nginx 双 vhost 参考配置（admin + api）`

---

#### Commit 3: deploy/scripts/install.sh + logrotate snippet

**文件**: 
- `deploy/scripts/install.sh` (主安装脚本)
- `deploy/logrotate/heypickler` (logrotate 配置)

**install.sh 行为**:
1. `check_prereqs`: 检查 java / mysql / redis-cli / nginx 命令存在 + JDK 17+
2. `setup_user_and_dirs`: 创建 heypickler 用户 + `/opt/heypickler` `/var/log/heypickler` `/etc/heypickler` 目录（`0750` `/etc/heypickler`）
3. `install_unit`: 拷贝 systemd unit → `/etc/systemd/system/`，`daemon-reload`，`enable`
4. `write_env_template`: 仅当 env 文件不存在时写入模板（含所有 15 个 env var）+ `chmod 0600`
5. `install_logrotate`: 拷贝 logrotate 配置到 `/etc/logrotate.d/heypickler`
6. 打印下一步指引

**幂等性**: 重跑不覆盖已有 env、不重建用户、不重复 enable unit

**验证**:
- `shellcheck deploy/scripts/install.sh`
- `bash -n deploy/scripts/install.sh` (语法检查)

**Commit message**: `feat(deploy): 新增 install.sh 首次部署脚本（幂等）`

---

#### Commit 4: deploy/scripts/backup-mysql.sh + restore-mysql.sh

**文件**:
- `deploy/scripts/backup-mysql.sh`
- `deploy/scripts/restore-mysql.sh`

**backup-mysql.sh 行为**:
1. source `/etc/heypickler/heypickler.env` 获取 DB 凭据
2. `mysqldump --single-transaction --master-data=2 --routines --triggers` → gzip → `/var/backups/heypickler/mysql/hey_pickler-YYYYMMDD-HHMMSS.sql.gz`
3. 若 `ossutil` 存在：`ossutil cp <file> <OSS_BUCKET>/mysql/<filename> --acl private`
4. 若 `ossutil` 不存在：warn + 继续（不 fail）
5. `find <BACKUP_DIR> -mtime +7 -delete` 清理本地老备份

**restore-mysql.sh 行为**:
1. 参数: `<backup-file>` 或 `<oss-url>`
2. 若 OSS URL：`ossutil cp <url> - | gunzip > /tmp/restore.sql`
3. 否则本地: `gunzip -c <file> > /tmp/restore.sql`
4. 打印 dump 头部的 binlog 坐标（`CHANGE MASTER TO MASTER_LOG_FILE=...`）
5. `mysql -u$DB_USERNAME -p$DB_PASSWORD hey_pickler < /tmp/restore.sql`
6. 提示「如需 PITR，使用上面打印的 binlog 坐标」

**验证**:
- `shellcheck deploy/scripts/backup-mysql.sh deploy/scripts/restore-mysql.sh`
- `bash -n` 语法检查

**Commit message**: `feat(deploy): 新增 MySQL 备份与恢复脚本（OSS 集成 + 本地兜底）`

---

### Day 2 — 文档（3 commits）

#### Commit 5: docs/RUNBOOK.md

**文件**: `docs/RUNBOOK.md`（新增）

**5 章节**（每章 ~150-200 行，含具体命令 + 预期输出片段）:

1. **首次部署**（~250 行）
   - 1.1 前置条件（ECS 规格、JDK/MySQL/Redis 版本、安全组端口、域名解析）
   - 1.2 一键安装（`install.sh` 用法 + 输出解读）
   - 1.3 环境变量配置（每个 env var 含义 + 生成命令）
   - 1.4 启动与验证（systemctl start + curl health + 看日志）
   - 1.5 Nginx 接入（拷贝 conf + 申请证书 + reload）
   - 1.6 故障排查（JDK 版本不对 / MySQL 拒绝 / 端口占用 3 个常见坑）

2. **日常运营**（~150 行）
   - 2.1 服务管理（start/stop/restart/status）
   - 2.2 日志查看（app.log / app.err.log / nginx-access）
   - 2.3 健康检查（actuator/health —— 注：依赖变更 #2 可观测性交付，先用 fallback `curl localhost:8080/api/admin/auth/login -I`）
   - 2.4 配置变更（编辑 env → systemctl restart hey-pickler）
   - 2.5 升级流程（scp 新 jar → systemctl restart → 看日志）

3. **备份与恢复**（~200 行）
   - 3.1 OSS bucket 创建 + RAM 子账号 + AccessKey 配置 + ossutil 安装
   - 3.2 自动备份（cron 配置 + 首次手动跑确认）
   - 3.3 手动备份（直接跑 backup-mysql.sh）
   - 3.4 恢复流程（restore-mysql.sh 完整 step-by-step + binlog PITR）
   - 3.5 季度恢复演练（在测试 ECS 上演练，附 checklist）

4. **限流调优**（~80 行）
   - 4.1 限流维度说明（按 IP / 按用户 / 按路径）
   - 4.2 调整方法（修改 application-prod.yml 的 `hey-pickler.rate-limit.*` → 重新打包 jar → restart；或用 `--hey-pickler.rate-limit.login=120` 命令行覆盖）
   - 4.3 触发限流的客户端表现（小程序 / admin 各会看到什么错误）

5. **常见故障响应**（~200 行）
   - 5.1 服务 5xx 飙升：检查 app.err.log / DB 慢查询 / Redis 延迟 / Nginx upstream timeout
   - 5.2 数据库连接耗尽：检查 HikariCP `maximum-pool-size` / `SHOW PROCESSLIST` / 长事务
   - 5.3 Redis 故障：检查 redis-cli ping / 内存 / 持久化状态
   - 5.4 磁盘满：检查日志大小 / 备份目录 / MySQL data dir
   - 5.5 OOM kill：检查 heap dump / JVM 参数 / ECS 内存
   - 5.6 SSL 证书过期：检查 `openssl s_client` / 续期流程
   - 5.7 升级回滚：从备份恢复 + 回退 jar

**验证**: 人工对照执行，目标 30 分钟内完成首次部署（无开发协助）

**Commit message**: `docs: 新增 RUNBOOK.md 运维手册（5 章节，覆盖部署/运营/备份/调优/故障）`

---

#### Commit 6: docs/DELIVERABLES.md + 更新 DEPLOYMENT-REQUIREMENTS.md

**文件**:
- `docs/DELIVERABLES.md`（新增）
- `docs/DEPLOYMENT-REQUIREMENTS.md`（修改：移除内嵌 java -jar 命令，引用 deploy/ 与 RUNBOOK）

**DELIVERABLES.md 结构**:
- 6 类工件清单表（源代码 / 二进制 / 部署工件 / 文档 / 测试报告 / 数据库）
- 每类附「验收动作」段落（step-by-step）
- 验收签字栏（客户 / 我方双签）

**DEPLOYMENT-REQUIREMENTS.md 修改点**:
- 章节 4.2（后端部署）：移除内嵌 `java -jar` 命令，改为「Run `deploy/scripts/install.sh`，详见 RUNBOOK.md §1」
- 章节 4.3（管理后台部署）：保留 npm build + scp，但 systemd 启动改为引用 unit 文件
- 章节 4.4（Nginx 配置）：移除完整 nginx.conf 内嵌，改为「拷贝 `deploy/nginx/heypickler.conf` 到 `/etc/nginx/sites-available/`」
- 新增章节 4.6（备份与恢复）：引用 RUNBOOK.md §3

**验证**: 通读无矛盾，与 RUNBOOK.md / install.sh / heypickler.conf 引用一致

**Commit message**: `docs: 新增 DELIVERABLES.md 交付物清单，精简 DEPLOYMENT-REQUIREMENTS 内嵌命令`

---

#### Commit 7: OpenSpec 归档

**动作**:
1. `openspec validate deployment-automation --strict`
2. `openspec archive deployment-automation -y`
3. 更新 `CLAUDE.md` 的 docs/ 章节提到 RUNBOOK 与 DELIVERABLES
4. 提交归档变更

**Commit message**: `chore(openspec): 归档 deployment-automation 变更，合并 spec delta`

---

## 验证矩阵

| Commit | 验证手段 | 通过标准 |
|--------|---------|---------|
| 1 (systemd unit) | `systemd-analyze verify` | 退出码 0，无 warning |
| 2 (nginx conf) | `nginx -t -c` 或 grep 关键字段 | 退出码 0 或字段全在 |
| 3 (install.sh) | `shellcheck` + `bash -n` | 无 error 级警告 |
| 4 (backup/restore) | `shellcheck` + `bash -n` | 无 error 级警告 |
| 5 (RUNBOOK.md) | 人工对照执行 | 30 分钟内首次部署成功 |
| 6 (DELIVERABLES.md) | 人工对照执行 | 6 类工件全部可验收 |
| 7 (archive) | `openspec validate --strict` | 通过 |

## 依赖与执行顺序

```
Commit 1 ─┐
          ├─→ Commit 3 (install.sh 引用 systemd unit)
Commit 2 ─┘
          
Commit 4 (backup/restore 独立，可并行)

Commit 5 (RUNBOOK 引用 commit 1-4 的工件) → 依赖 1-4

Commit 6 (DELIVERABLES 引用 commit 1-5) → 依赖 5

Commit 7 (archive) → 依赖 1-6
```

可并行：Commit 1+2 可同时；Commit 4 可与 1-3 并行。

## Out of Scope 提醒

实施过程中**不要**：
- 添加 Dockerfile / docker-compose（Phase 2）
- 写 CI/CD workflow（变更 #5）
- 添加 actuator 配置（变更 #2 可观测性）
- 改 application-prod.yml 加 HikariCP / logging（变更 #2）
- 添加 Prometheus / Sentry（变更 #2）
- 写 k6 / wrk 压测脚本（变更 #3）
- 改 26 个 controller 加 `/api/v1/`（变更 #5）

如果实施中发现需要这些，记在 RUNBOOK 的「Phase 2 待办」章节，不本变更内做。
