
# IoT-Platform 模块补完计划

> **版本**: V1.0.0 | **状态**: 🟡 草稿
>
> **创建**: 2026-06-21 | **修改**: 2026-06-21
>
> **负责人**: 项目负责人

---

### 📋 变更记录

| 版本 | 日期 | 变更人 | 类型 | 摘要 |
|:----:|:----:|:------:|:----:|------|
| V1.0.0 | 2026-06-21 | 项目负责人 | ✨ 新增 | 初始版本，基于代码审查发现的问题制定全量补完计划 |

---

## 1. 文档概述

### 1.1 编制目的

本文档基于 [IoT-Platform需求规格说明书 V1.1.0](./IoT-Platform需求规格说明书.md) 与实际代码 [README.md](../../README.md) 的两轮交叉复核结果，系统性地定义当前代码实现与需求规格之间的差异项的修正计划。计划按 **三阶段** 推进，每阶段定义明确的问题清单、修正措施、验收标准与责任分工。

### 1.2 前置阅读

| 文档 | 路径 |
|------|------|
| IoT-Platform需求规格说明书 V1.1.0 | [./IoT-Platform需求规格说明书.md](./IoT-Platform需求规格说明书.md) |
| 需求文档标准化方案 V1.0.0 | [./需求文档标准化方案.md](./需求文档标准化方案.md) |
| 项目 README | [../../README.md](../../README.md) |
| AGENTS.md | [../../AGENTS.md](../../AGENTS.md) |

### 1.3 术语

| 术语 | 含义 |
|------|------|
| CR | Code Review，代码审查 |
| QA | 质量保证/测试 |
| Flyway | 数据库版本化管理工具 |

### 1.4 当前问题统计

| 严重级别 | 数量 | 问题 ID |
|:--------:|:----:|---------|
| 🔴 严重 | 3 | P1-1 ~ P1-3 |
| 🟡 中等 | 4 | P2-1 ~ P2-4 |
| 🔵 轻微 | 7 | P3-1 ~ P3-7 |
| **合计** | **14** | |

---

## 2. 阶段规划总览

```
时间线（天）     1         2         3         4         5
                │         │         │         │         │
阶段1：核心修复  ██████████
阶段2：功能补全            ████████████████████
阶段3：质量验证                                      ██████████
                │         │         │         │         │
里程碑          M1       M2                 M3
```

| 阶段 | 名称 | 时间 | 包含问题 | 交付物 |
|:----:|------|:----:|----------|--------|
| **阶段 1** | 核心代码缺陷修复 | Day 1 | P1-1, P1-2, P1-3 | 代码变更 + Flyway V5 + 单元测试 |
| **阶段 2** | 功能补全与文档修正 | Day 2-3 | P2-1, P2-2, P2-3, P2-4, P3-1~P3-5 | 登出接口 + notification_log + 需求与README修正 |
| **阶段 3** | 质量验证 | Day 4-5 | P3-6, P3-7 | 浏览器兼容报告 + JMeter压测报告 |

---

## 3. 阶段 1：核心代码缺陷修复（Day 1）

### 概述

修复 3 项严重问题，均为需求明确要求但代码未实现或数据注释存在系统性风险。

---

### 3.1 问题 P1-1：DictMetric 实体缺少 Jakarta Validation 校验注解

#### 3.1.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [DictMetric.java](../../src/main/java/com/example/platform/entity/DictMetric.java) |
| **关联需求** | REQ-QUA-FR-003 — "补全 DictMetricController 等缺失 @Validated 注解的接口参数校验" |
| **严重性** | 🔴 严重 |
| **类型** | 功能性遗漏 |

#### 3.1.2 影响分析

空 `metric_key` 直接写入数据库会导致告警引擎无法正确匹配指标，告警消息中出现空字段名，直接影响实时告警的可读性与准确性。其他 5 个核心实体均具备完整校验，此为显性遗漏。

#### 3.1.3 根本原因

DictMetric 类在需求文档中明确要求校验注解，但编码阶段遗漏；DictMetricController 的 `add()` / `update()` 方法也缺少 `@Validated` 注解触发校验。

#### 3.1.4 修正措施

| 步骤 | 操作 | 涉及代码 |
|:----:|------|----------|
| 1.1 | `metricKey` 添加 `@NotBlank` + `@Pattern(regexp = "^[a-z_]+$")` + `@Size(max=50)` | DictMetric.java |
| 1.2 | `metricName` 添加 `@NotBlank` + `@Size(max=50)` | DictMetric.java |
| 1.3 | `metricUnit` 添加 `@Size(max=20)` | DictMetric.java |
| 1.4 | `description` 添加 `@Size(max=200)` | DictMetric.java |
| 1.5 | `add(DictMetric)` → `add(@Validated @RequestBody DictMetric)` | DictMetricController.java |
| 1.6 | `update(DictMetric)` → `update(@Validated @RequestBody DictMetric)` | DictMetricController.java |
| 1.7 | 编写 DictMetric 校验单元测试，覆盖 4 种非法输入 | DictMetricServiceTest.java |

#### 3.1.5 验收标准

- `POST /api/dict-metrics` 发送 `{"metricKey":""}` 返回 HTTP 400 + 错误信息
- `POST /api/dict-metrics` 发送 `{"metricKey":"ABC"}`（含大写字母）返回 HTTP 400
- `POST /api/dict-metrics` 发送合法数据返回 HTTP 200
- 单元测试覆盖：空 key、非法字符、超长字符串、正常数据 4 种场景

---

### 3.2 问题 P1-2：DictMetric 实体缺 createTime/updateTime 字段映射 + 数据库列名不一致

#### 3.2.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [DictMetric.java](../../src/main/java/com/example/platform/entity/DictMetric.java)、[V1__init_schema.sql](../../src/main/resources/db/migration/V1__init_schema.sql) |
| **关联需求** | REQ-INF-FR-001 — "DictMetric实体需补充 create_time 和 update_time 字段" |
| **严重性** | 🔴 严重 |
| **类型** | 功能性遗漏 + 规范偏离 |

#### 3.2.2 影响分析

两个子问题：**(a)** 无时间字段映射，无法追踪指标字典创建/修改时间；**(b)** 列名 `created_at` / `updated_at` 与其余 6 张表的 `create_time` / `update_time` 风格不一致，MyBatis-Plus 自动填充机制无法覆盖该表。

#### 3.2.3 根本原因

V1 建表脚本编写时采用了 `created_at` / `updated_at` 命名，与其他表不统一，后续补全实体时也未同步添加字段映射。

#### 3.2.4 修正措施

| 步骤 | 操作 | 涉及代码 |
|:----:|------|----------|
| 1.1 | 新建 Flyway V5 迁移脚本，重命名列并修正 alerts 表注释（合并 P1-3） | V5__fix_dict_metric_and_alert_schema.sql |
| 1.2 | `ALTER TABLE dict_metric CHANGE COLUMN created_at create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'` | 同上 |
| 1.3 | `ALTER TABLE dict_metric CHANGE COLUMN updated_at update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'` | 同上 |
| 1.4 | DictMetric 实体添加 `createTime` / `updateTime` 字段，含 `@TableField(fill = FieldFill.INSERT)` 和 `@TableField(fill = FieldFill.INSERT_UPDATE)` | DictMetric.java |
| 1.5 | 验证 `MyMetaObjectHandler` 自动填充对 dict_metric 表生效 | — |
| 1.6 | 更新 README 手工建表 SQL 中的 dict_metric 建表语句 | README.md |

#### 3.2.5 验收标准

- Flyway V5 迁移执行成功，`flyway_schema_history` 记录正确
- `SHOW FULL COLUMNS FROM dict_metric` 显示列名为 `create_time` / `update_time`
- 新增指标后 `create_time` 自动填充当前时间
- 更新指标后 `update_time` 自动刷新
- 迁移在已有数据表上无报错执行

---

### 3.3 问题 P1-3：数据库建表注释中告警级别映射与代码定义相反

#### 3.3.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [V1__init_schema.sql](../../src/main/resources/db/migration/V1__init_schema.sql) |
| **关联需求** | REQ-ALT-FR-001 — 告警规则引擎，5 级告警体系 |
| **严重性** | 🔴 严重 |
| **类型** | 数据文档错误 |

#### 3.3.2 影响分析

SQL 注释 `alert_level` 写为 `1一般 2严重 3紧急`，但代码定义为 `1-紧急，2-严重，3-一般，4-警告，5-提示`。离线告警代码写入 `alertLevel=1` 本意为"紧急"，若 DBA 按 SQL 注释理解会误判为"一般"。此风险不对程序运行造成影响，但对直接查库的人员产生严重误导。

#### 3.3.3 根本原因

V1 建表脚本编写时采用了早期的错误级别定义，后续代码中级别语义发生了反转但 DDL 注释未同步。

#### 3.3.4 修正措施

| 步骤 | 操作 | 涉及代码 |
|:----:|------|----------|
| 1.1 | 在 V5 迁移脚本中（与 P1-2 合并）执行 MODIFY COLUMN 更新注释 | V5__fix_dict_metric_and_alert_schema.sql |
| 1.2 | 检查 DDL 中其他表字段注释是否有类似错误 | — |
| 1.3 | 更新 README 手工建表 SQL 中的注释 | README.md |

V5 SQL 示例：

```sql
-- 修正 dict_metric 列名（P1-2）
ALTER TABLE dict_metric CHANGE COLUMN created_at create_time
    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE dict_metric CHANGE COLUMN updated_at update_time
    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 修正 alerts 表注释（P1-3）
ALTER TABLE alerts MODIFY COLUMN alert_level TINYINT DEFAULT 1
    COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示';
```

#### 3.3.5 验收标准

- `SHOW FULL COLUMNS FROM alerts` 的 `alert_level` 列 Comment 显示正确级别映射
- 已有告警数据的不丢、不变化
- README 手工建表 SQL 同步更新

### 3.4 阶段 1 交付物清单

| # | 交付物 | 类型 |
|:-:|--------|:----:|
| D1 | DictMetric.java — 补充校验注解 | 代码变更 |
| D2 | DictMetricController.java — 加 @Validated | 代码变更 |
| D3 | V5__fix_dict_metric_and_alert_schema.sql — Flyway 迁移 | 脚本 |
| D4 | DictMetricServiceTest.java — 校验单元测试 | 单元测试 |
| D5 | README.md — 同步手工建表 SQL | 文档变更 |

### 3.5 阶段 1 验收检查单

- [ ] `mvn clean compile` 零错误
- [ ] `mvn test -Dtest=DictMetricServiceTest` 全量通过
- [ ] Flyway V5 迁移无报错
- [ ] DictMetric 非法输入返回 400
- [ ] DictMetric 合法数据返回 200
- [ ] `SHOW FULL COLUMNS FROM alerts` 注释正确

---

## 4. 阶段 2：功能补全与文档修正（Day 2-3）

### 概述

补全登出接口、notification_log 持久化表等缺失功能，并全面修正 README 与需求规格说明书中的矛盾。

---

### 4.1 问题 P2-1：缺少用户登出接口

#### 4.1.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | TokenBlacklistService 已完整实现（双层黑名单），但无 Controller 暴露登出端点 |
| **关联需求** | REQ-SEC-FR-002 — Token 校验与接口鉴权 |
| **严重性** | 🟡 中等 |

#### 4.1.2 影响分析

用户点击"退出登录"时前端只能清除 localStorage，Token 存活期可长达 2 小时。安全风险虽然有限（Token 有天然 TTL），但作为安全机制设计的最后一环不应缺失。

#### 4.1.3 根本原因

Token 黑名单在后期迭代中加入，但对应的 REST 端点未同步补全。

#### 4.1.4 修正措施

| 步骤 | 操作 | 涉及代码 |
|:----:|------|----------|
| 2.1 | AuthController 新增 `POST /api/auth/logout`，从 Header 提取 Token 加入黑名单 | AuthController.java |
| 2.2 | 添加 Swagger `@Operation(summary = "用户登出")` | 同上 |
| 2.3 | SecurityConfig 确认 `/api/auth/logout` 已配置为需认证（非 permitAll） | SecurityConfig.java（确认） |
| 2.4 | 前端 auth store 的 logout action 调用该接口 | `frontend/src/stores/auth.js` |

#### 4.1.5 验收标准

- 登录后调用 `POST /api/auth/logout` 返回 200
- 登出后同一 Token 请求受保护接口返回 401
- 未认证请求 `POST /api/auth/logout` 返回 401
- 前端退出登录后确实调用了该接口

---

### 4.2 问题 P2-2：README 中登录失败次数描述错误（3次 vs 5次）

#### 4.2.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [README.md](../../README.md) 核心能力表 + SysUser 实体说明 |
| **实际值** | `MAX_LOGIN_FAIL_COUNT = 5`（[AuthServiceImpl.java](../../src/main/java/com/example/platform/service/impl/AuthServiceImpl.java)） |
| **严重性** | 🟡 中等 |
| **类型** | 文档错误 |

#### 4.2.2 修正措施

| 步骤 | 操作 |
|:----:|------|
| 2.5 | 核心能力表"用户管理"行：`3次` → `5次` |
| 2.6 | SysUser 实体说明行：`3次` → `5次` |

#### 4.2.3 验收标准

README 中全文搜索"3次"不再出现（确认其他位置也修正）。

---

### 4.3 问题 P2-3：需求文档内部矛盾 — 账号正常状态值不一致

#### 4.3.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | 需求文档 REQ-SEC-FR-001 前置条件写 `status=1` 为正常，但 REQ-INF-FR-004 和代码均为 `0-正常，1-停用` |
| **严重性** | 🟡 中等 |
| **类型** | 需求文档内部矛盾 |

#### 4.3.2 修正措施

| 步骤 | 操作 | 位置 |
|:----:|------|------|
| 2.7 | REQ-SEC-FR-001 前置条件 1: `status=1` → `status=0` | IoT-Platform需求规格说明书.md |
| 2.8 | 检查文档全文其他 `status=1` 引用，全部统一 | IoT-Platform需求规格说明书.md |

#### 4.3.3 验收标准

需求规格说明书中所有 `status` 相关描述统一为 `0-正常，1-停用`，无冲突表述。

---

### 4.4 问题 P2-4：notification_log 表未实现

#### 4.4.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | REQ-ALT-FR-003 后置条件要求"通知发送记录写入 notification_log 表"，REQ-INF-FR-004 定义了该表结构 |
| **当前状态** | 通知去重走 Redis，邮件走 EmailNotificationServiceImpl，均无持久化日志 |
| **严重性** | 🟡 中等 |
| **类型** | 功能缺失 |

#### 4.4.2 影响分析

无法追溯通知发送历史。运维排查"为什么没收到通知"类问题时缺乏数据支撑。但核心告警生成和通知功能正常，不构成阻塞。

#### 4.4.3 根本原因

notification_log 是需求文档 V1.1.0 新增项，通知机制本身也是后期加入，日志持久化被遗漏。

#### 4.4.4 修正措施

| 步骤 | 操作 | 涉及代码 |
|:----:|------|----------|
| 2.9 | 创建 Flyway V6 迁移脚本，建 notification_log 表 | V6__add_notification_log.sql |
| 2.10 | 创建 `NotificationLog` Entity（含 `id, alert_id, notification_type, recipient, content, status, send_time, error_message`） | entity/NotificationLog.java |
| 2.11 | 创建 `NotificationLogMapper` | mapper/NotificationLogMapper.java |
| 2.12 | 修改 `EmailNotificationServiceImpl`，发送后记录发送结果 | 修改 impl 文件 |
| 2.13 | 修改 `AlertWebSocketService`，推送后记录发送状态 | 修改 impl 文件 |

V6 SQL 结构：

```sql
CREATE TABLE notification_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_id INT NOT NULL COMMENT '关联告警ID',
    notification_type VARCHAR(20) NOT NULL COMMENT '通知类型：EMAIL / WEBSOCKET',
    recipient VARCHAR(100) COMMENT '接收方（邮箱地址或用户ID）',
    content VARCHAR(500) COMMENT '通知内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待发送 1-成功 2-失败',
    error_message VARCHAR(500) COMMENT '失败原因',
    send_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT '通知发送日志表';
```

#### 4.4.5 验收标准

- Flyway V6 迁移成功
- 生成紧急告警后 notification_log 表有对应的 EMAIL + WEBSOCKET 两条记录
- 邮件发送失败时记录 status=2 + error_message

---

### 4.5 问题 P3-1~P3-5：轻微文档问题

#### 4.5.1 问题汇总

| ID | 问题 | 操作 | 工作量 |
|:--:|------|------|:------:|
| P3-1 | 需求文档引用不存在的注册接口 | 删除 REQ-SEC-FR-002 中"注册接口 POST /api/auth/register"的引用 | 0.1h |
| P3-2 | README 手工建表 SQL 缺 deleted 字段 | 6 张表的 CREATE TABLE 均添加 `deleted TINYINT DEFAULT 0` 列 | 0.3h |
| P3-3 | README 环境要求缺 Redis | 添加 `Redis \| 7.0+ \| 缓存数据库` 行 | 0.1h |
| P3-4 | SysUser/SysAuditLog 无 @TableLogic | 经分析属于有意设计，**无需修改**，但在需求文档补充说明 | 0h |
| P3-5 | 需求文档 status=1 正常（已计入 P2-3） | 与 P2-3 合并处理 | 0h |

#### 4.5.2 操作详情

**P3-1**：定位到 [IoT-Platform需求规格说明书.md](./IoT-Platform需求规格说明书.md) 中 REQ-SEC-FR-002 业务规则第 1 项，删除或替换为"登录接口（POST /api/auth/login）无需Token"。

**P3-2**：README 第 6 节"建表"部分 6 个 CREATE TABLE 语句末尾添加：

```sql
deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'
```

并在每个表的索引建议中添加 `INDEX idx_deleted (deleted)`。

**P3-3**：README 第 5 节环境要求表格中，在 MySQL 行下方新增一行：

```markdown
| Redis | 7.0+ | 缓存数据库（指标字典、Token 黑名单、限流计数器等） |
```

---

### 4.6 阶段 2 交付物清单

| # | 交付物 | 类型 |
|:-:|--------|:----:|
| D6 | AuthController.java — 登出接口 | 代码变更 |
| D7 | V6__add_notification_log.sql — Flyway 迁移 | 脚本 |
| D8 | NotificationLog.java + Mapper | 新文件 |
| D9 | EmailNotificationServiceImpl / AlertWebSocketService — 写通知日志 | 代码变更 |
| D10 | README.md — 全面修正（3次→5次、建表SQL、Redis依赖） | 文档变更 |
| D11 | IoT-Platform需求规格说明书.md — 修正 status 矛盾 + 删除注册接口引用 | 文档变更 |

### 4.7 阶段 2 验收检查单

- [ ] `POST /api/auth/logout` 接口可用
- [ ] 登出后 Token 立即失效
- [ ] Flyway V6 迁移成功
- [ ] 告警触发后 notification_log 有对应记录
- [ ] README 全文无"3次"错误表述
- [ ] README 环境要求含 Redis
- [ ] README 手工建表 SQL 含 deleted 列
- [ ] 需求文档 status 描述统一

---

## 5. 阶段 3：质量验证（Day 4-5）

### 概述

对整体系统进行浏览器兼容性验证和性能基准测试，确保前两阶段修改不引入回归问题，同时验证非功能需求指标。

---

### 5.1 问题 P3-6：前端浏览器兼容性验证

#### 5.1.1 测试矩阵

| 浏览器 | 版本 | 测试页面 | 测试要点 |
|--------|:----:|----------|----------|
| Google Chrome | 最新稳定版 | 登录 / 仪表盘 / 设备列表 / 告警管理 | 布局无错乱，JS 无报错 |
| Mozilla Firefox | 最新稳定版 | 同上 | 同上 |
| Microsoft Edge | 最新稳定版 | 同上 | 同上 |
| Apple Safari | 最新稳定版 | 同上 | 同上（有条件时） |

#### 5.1.2 测试用例

| # | 测试场景 | 操作步骤 | 预期结果 |
|:-:|----------|----------|----------|
| TC1 | 登录页渲染 | 打开登录页面 | 表单居中显示，无布局错乱 |
| TC2 | 仪表盘加载 | 登录后跳转仪表盘 | 4 个统计卡片正常，折线图渲染 |
| TC3 | 设备列表分页 | 进入设备管理，翻页 | 表格数据显示正确，分页控件可用 |
| TC4 | 告警处理 | 在告警页面处理一条告警 | 弹窗正常，处理后状态更新 |
| TC5 | 登出功能 | 点击退出登录 | 前端清除 token 并重定向到登录页 |

#### 5.1.3 交付物

浏览器兼容性测试报告（截图 + 测试结果记录）。

---

### 5.2 问题 P3-7：性能基准测试

#### 5.2.1 测试环境

| 维度 | 配置 |
|------|------|
| 服务器 | 本地开发机（或 Docker 容器） |
| 压测工具 | Apache JMeter 5.6+ |
| 线程数 | 50 并发 |
| 循环次数 | 1000 次请求 |

#### 5.2.2 测试场景

| # | 场景 | 接口 | 目标 |
|:-:|------|------|:----:|
| PS1 | 设备列表查询 | `GET /api/devices?page=1&size=10` | P95 < 200ms |
| PS2 | 告警列表查询 | `GET /api/alerts?page=1&size=10` | P95 < 200ms |
| PS3 | 设备数据上报 | `POST /api/device-data` | P95 < 500ms |
| PS4 | 仪表盘统计 | `GET /api/dashboard/stats` | P95 < 200ms |
| PS5 | 混合场景(7:2:1) | 查询 70% + 上报 20% + 统计 10% | 错误率 < 0.1% |

#### 5.2.3 交付物

JMeter 压测报告（含 .jmx 测试计划文件 + HTML 报告）。

---

### 5.3 回归验证（贯穿阶段 1-3）

每阶段完成后执行 `mvn clean test` 确保全量单元测试通过。

```bash
# 全量单元测试
mvn clean test

# 指定阶段关键测试
mvn test -Dtest="AuthServiceImplTest,DictMetricServiceTest,AlertServiceImplTest"

# 编译验证
mvn clean compile
```

---

## 6. 风险评估与应对

### 6.1 风险矩阵

| 风险描述 | 概率 | 影响 | 等级 | 应对策略 |
|----------|:----:|:----:|:----:|----------|
| Flyway V5 在已有数据表上执行失败（列重命名冲突） | 低 | 高 | 🟡 | 使用幂等 DDL（`IF EXISTS` 预检查），先在测试环境执行 |
| dict_metric 列重命名导致现有实体映射异常 | 低 | 中 | 🟢 | V5 迁移后重新编译，启动时 MyBatis 自动校验映射 |
| 通知日志写入增加告警链路延迟 | 中 | 低 | 🟢 | 通知日志使用独立异步线程写入，不阻塞主链路 |
| JMeter 压测发现 P95 不达标 | 中 | 中 | 🟡 | 预备方案：优化 SQL 索引、增大连接池、二级缓存 |
| 浏览器兼容性问题 | 低 | 低 | 🟢 | Vue3 + Element Plus 理论兼容，发现问题按优先级修复 |

### 6.2 回滚方案

| 场景 | 回滚操作 |
|------|----------|
| Flyway 迁移失败 | 删除 `flyway_schema_history` 中 V5/V6 记录，删除对应迁移 SQL，重新启动 |
| 登出接口异常 | 回退 AuthController.java 变更，移除新增的 logout 方法 |
| notification_log 影响主链路 | 在 service 层加 try-catch 包裹日志写入，异常仅记录日志不抛出 |

---

## 7. 预防机制（长效改进）

### 7.1 需求-代码-文档三方同步

每次需求变更（新增/修订功能需求）后，PR 提交前须完成：

```
需求文档 → 代码实现 → README 同步 → AGENTS.md 同步
```

由 PR 提交者在 CR 描述中附带三方同步检查结果。

### 7.2 实体校验注解检查清单

Code Review 模板中增加检查项：

- [ ] 新增/修改的 `@RequestBody` 实体是否具有完整的 Jakarta Validation 注解
- [ ] Controller 是否加 `@Validated`（含分组）
- [ ] DTO 字段是否标注 `@Schema`（Swagger 文档生成）

### 7.3 DDL 注释一致性

- 新增 DDL 迁移脚本的 `COMMENT` 内容必须与对应 Entity 的 `@Schema(description=...)` 含义一致
- 数据库字段的注释中枚举值（如状态、级别）必须与 Java 代码定义的常量/枚举一致

### 7.4 文档自动化验证（CI）

在 CI pipeline 中增加：

```bash
# 验证 README 中版本号与 pom.xml 一致
grep -c "版本: v$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)" README.md
# 验证 README 环境要求中不遗漏 Redis
grep -q "Redis" README.md
# 验证 AGENTS.md 中的版本信息
grep -q "当前版本" AGENTS.md
```

### 7.5 性能基准常态化

- 每个大版本发布前执行一次 JMeter 基准测试
- 测试结果存入 `开发流程学习文档/性能测试/` 目录
- 建立性能基线，当 P95 退化超过 20% 时标记回归

---

## 8. 附录

### 8.1 问题完整索引

| ID | 严重级 | 模块 | 文件名（代码/文档） | 阶段 |
|:--:|:------:|:----:|---------------------|:----:|
| P1-1 | 🔴 | 配置模块 | DictMetric.java, DictMetricController.java | 1 |
| P1-2 | 🔴 | 基础设施 | DictMetric.java, V1__init_schema.sql | 1 |
| P1-3 | 🔴 | 告警模块 | V1__init_schema.sql | 1 |
| P2-1 | 🟡 | 安全模块 | AuthController.java（缺失） | 2 |
| P2-2 | 🟡 | 文档 | README.md | 2 |
| P2-3 | 🟡 | 文档 | IoT-Platform需求规格说明书.md | 2 |
| P2-4 | 🟡 | 告警模块 | notification_log表/Entity/Service（缺失） | 2 |
| P3-1 | 🔵 | 文档 | IoT-Platform需求规格说明书.md | 2 |
| P3-2 | 🔵 | 文档 | README.md | 2 |
| P3-3 | 🔵 | 文档 | README.md | 2 |
| P3-4 | 🔵 | 基础设施 | 无需修改 | — |
| P3-5 | 🔵 | 文档 | IoT-Platform需求规格说明书.md（已合并P2-3） | 2 |
| P3-6 | 🔵 | QA | 浏览器兼容性验证 | 3 |
| P3-7 | 🔵 | QA | JMeter 性能压测 | 3 |

### 8.2 验收总检查单

以下为主检查单，供项目经理/技术负责人最终确认：

#### 阶段 1 检查点

- [ ] `mvn clean compile` 零错误
- [ ] `mvn test` 全量单元测试通过
- [ ] Flyway V5 迁移执行成功
- [ ] DictMetric 校验单元测试覆盖 4 种非法场景
- [ ] DictMetric 非法输入返回 400，合法输入返回 200
- [ ] `SHOW FULL COLUMNS FROM alerts` 注释正确
- [ ] `SHOW FULL COLUMNS FROM dict_metric` 列名已更正

#### 阶段 2 检查点

- [ ] `POST /api/auth/logout` 可用，登出后 Token 失效
- [ ] Flyway V6 迁移成功
- [ ] 告警通知生成后 notification_log 有对应记录
- [ ] README 全文无"3次"错误描述
- [ ] README 环境要求含 Redis
- [ ] README 手工建表 SQL 含 deleted 列
- [ ] 需求文档中 status 描述统一

#### 阶段 3 检查点

- [ ] Chrome/Firefox/Edge 核心页面无异常
- [ ] JMeter 50 并发混合场景错误率 < 0.1%
- [ ] 查询类接口 P95 < 200ms
- [ ] 上报类接口 P95 < 500ms

#### 全局检查点

- [ ] AGENTS.md 版本信息更新
- [ ] README.md 文档版本号更新
- [ ] 本文档变更为 🟢 已批准 状态

---

> **本文档遵循**：[需求文档标准化方案 V1.0.0](./需求文档标准化方案.md)
