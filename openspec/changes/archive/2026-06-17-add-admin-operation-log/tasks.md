# Tasks: 后台操作审计日志

## 1. 数据层
- [x] 1.1 新建 `db/migration/V8__add_operation_log.sql`，建表 + 4 个索引
- [x] 1.2 新建 `entity/OperationLog.java`（@TableName + @TableId(AUTO) + @TableField(fill=INSERT) createdAt）
- [x] 1.3 新建 `mapper/OperationLogMapper.java`（继承 BaseMapper）

## 2. 工具类（TDD）
- [x] 2.1 写 `SensitiveDataUtilTest`（10 个 case：password/token/phone/idcard/bankcard/嵌套/列表/解析失败/大 payload/大小写）
- [x] 2.2 实现 `SensitiveDataUtil.maskJson` 让测试全绿
- [x] 2.3 写 `OperationLogClassifierTest`（10 个 case：8 个 module + 6 种 action + 登录 + 未知路径 + query string 剥离）
- [x] 2.4 实现 `OperationLogClassifier.classify` 让测试全绿

## 3. 服务层
- [x] 3.1 新建 `common/dto/OperationLogQuery.java`
- [x] 3.2 新建 `service/OperationLogService.java`（record + page 接口）
- [x] 3.3 实现 `service/impl/OperationLogServiceImpl.java`：`record` 标 `@Async("auditLogExecutor")`，try/catch 吞写库异常；`page` 用 LambdaQueryWrapper
- [x] 3.4 新增 `config/AsyncConfig.java` 的 `auditLogExecutor` bean（core=2/max=4/queue=500/DiscardOldestPolicy）

## 4. AOP 切面
- [x] 4.1 写 `OperationLogAspectTest`（8 个 case：成功/BizException/RuntimeException/GET 跳过/ANONYMOUS/写库失败不传播/XFF/params 截断）
- [x] 4.2 实现 `OperationLogAspect.@Around` 让测试全绿
- [x] 4.3 实现 `buildBaseLog` 从 request 取 operator/IP/UA/params

## 5. 查询接口
- [x] 5.1 新建 `vo/OperationLogVO.java`（含 operatorName）
- [x] 5.2 新建 `controller/admin/AdminOperationLogController.java`（GET + @RequireRole + 批量回填 operatorName）
- [x] 5.3 写 `AdminOperationLogControllerTest`（分页 + 4 种过滤 + operator_name 回填 + null operator 容错）

## 6. 前端
- [x] 6.1 修改 `AppSidebar.vue`：原菜单改名「用户日志」+ 新增「操作日志」菜单（/admin-logs）
- [x] 6.2 修改 `router/index.ts`：注册 admin-logs 子路由
- [x] 6.3 新建 `api/admin-logs.ts`：getOperationLogs + OperationLogItem/Query 类型
- [x] 6.4 新建 `views/admin-logs/AdminLogListView.vue`：过滤器 + 表格 + 详情 drawer
- [x] 6.5 修改 `views/ban-records/BanRecordListView.vue`：H1 与提示文案同步改名
- [x] 6.6 `npm run lint` 通过（新文件无 `Unexpected any`）

## 7. 验证
- [ ] 7.1 `mvn test -Dtest='!*IntegrationTest'` — 单测全绿
- [ ] 7.2 `mvn test -Dtest='*OperationLogAspectTest,*OperationLogClassifierTest,*SensitiveDataUtilTest,*AdminOperationLogControllerTest'` — 新增测试全绿
- [ ] 7.3 `mvn spring-boot:run`，看 Flyway 日志 `Migrating schema ... to version "8"` 应用成功
- [ ] 7.4 admin/admin123 登录 admin 后台
- [ ] 7.5 触发写操作（禁赛用户、改 banner 状态）→ /admin-logs 看到记录
- [ ] 7.6 触发失败（非法 banner URL）→ 看到 status=FAIL、error_code=1001 的记录
- [ ] 7.7 触发登录失败 → 看到 module=AUTH、operator_role=ANONYMOUS 的记录
- [ ] 7.8 验证原「用户日志」菜单仍指向 ban-records 页面
- [ ] 7.9 `SELECT COUNT(*) FROM operation_log` > 0；抽样验证 params 字段 phone/password 已脱敏

## 8. 归档
- [ ] 8.1 移动 `openspec/changes/add-admin-operation-log/` 到 `archive/2026-06-17-add-admin-operation-log/`
- [ ] 8.2 合并 spec delta：新建 `openspec/specs/audit/spec.md` + 增量 `openspec/specs/infrastructure/spec.md`（V8 条目）
