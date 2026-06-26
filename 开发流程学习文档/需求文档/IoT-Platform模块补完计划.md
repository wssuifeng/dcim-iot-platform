
# IoT-Platform 模块补完计划

> **版本**: V1.1.0 | **状态**: 🟡 草稿
>
> **创建**: 2026-06-21 | **修改**: 2026-06-21
>
> **负责人**: 项目负责人

---

### 📋 变更记录

| 版本 | 日期 | 变更人 | 类型 | 摘要 |
|:----:|:----:|:------:|:----:|------|
| V1.1.0 | 2026-06-21 | 项目负责人 | 🔄 修订 | 基于第二轮代码审查新增阶段4（4项新发现问题），更新全部14项原始问题的状态 |

---

## 1. 文档概述

### 1.1 编制目的

本文档基于两轮代码审查与需求规格说明书交叉比对的结果，系统性地定义当前代码实现与需求规格之间的差异项的修正计划。

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
| SpEL | Spring Expression Language，Spring 表达式语言 |
| Flyway | 数据库版本化管理工具 |
| CR | Code Review，代码审查 |

---

## 2. 项目当前状态

### 2.1 版本基线

项目当前处于 **v4.2**（2026-06-21），以下修复已在 v4.2 中完成：

| 批次 | 问题 | 修复摘要 |
|:----:|:----:|----------|
| ✅ P1-1 | DictMetric 校验注解 + 字段映射 | `@NotBlank/@Pattern/@Size` 注解 + `createTime/updateTime` 自动填充 + `@TableLogic` |
| ✅ P1-2 | dict_metric 列名不规范 | Flyway V5 `created_at→create_time`, `updated_at→update_time` |
| ✅ P1-3 | alerts 告警级别注释错误 | Flyway V5 修正为 `1-紧急 2-严重 3-一般 4-警告 5-提示` |
| ✅ P2-1 | 缺少登出接口 | AuthController 新增 `POST /api/auth/logout` |
| ✅ P2-2 | README 3次→5次 | 核心能力表 + SysUser 说明已更新 |
| ✅ P2-3 | 需求文档 status 矛盾 | REQ-SEC-FR-001 前置条件修正为 `status=0` |
| ✅ P2-4 | notification_log 未实现 | Flyway V6 建表 + Entity + Mapper + Email/WebSocket 日志写入 |
| ✅ P3-1 | 需求文档注册接口引用 | 已删除 |
| ✅ P3-3 | README 缺 Redis | 环境要求 + 技术栈表格已补充 |

### 2.2 仍存在的问题

| 阶段 | ID | 严重级 | 摘要 | 类型 |
|:----:|:--:|:------:|------|:----:|
| 阶段3 | P3-2 | 🔵 轻微 | README 手工建表 SQL 仍缺 deleted 列（4张表） | 文档遗漏 |
| 阶段3 | P3-6 | ⏳ 待执行 | 浏览器兼容性验证 | 测试任务 |
| 阶段3 | P3-7 | ⏳ 待执行 | JMeter 性能基准测试 | 测试任务 |
| **阶段4** | **N1** | **🔴 严重** | **AuditLogAspect SpEL 字段链解析全部失效** | **功能缺陷** |
| **阶段4** | **N2** | **🔴 严重** | **DictMetricController @AuditLog 引用不存在字段 `metricCode`** | **功能缺陷** |
| **阶段4** | **N3** | **🟡 中等** | **WebSocketConfig 全源跨域许可** | **安全风险** |
| 阶段3 | P3-4 | 🔵 轻微 | SysUser/SysAuditLog 无 @TableLogic（有意设计，无需修改） | — |

---

## 3. 阶段 3：收尾任务（Day 1-2）

### 3.1 问题 P3-2：README 手工建表 SQL 缺 deleted 列

**影响分析**：新人直接复制 README 建表会导致 4 张表缺少 `deleted` 列，逻辑删除在这些表上不生效。

**修正措施**：

| 表名 | 位置（README.md） | 操作 |
|------|:-----------------:|------|
| `device_types` | 第 268-276 行 | 末尾添加 `deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'` |
| `device_data` | 第 293-299 行 | 同上 |
| `alerts` | 第 301-312 行 | 同上 |
| `sys_user` | 第 324-333 行 | 同上 |

> 工作量：约 0.2 小时

### 3.2 问题 P3-6 / P3-7：浏览器兼容性 + JMeter 性能测试

参见原计划中的验证方案（阶段3），建议在阶段4代码修复后统一执行。

---

## 4. 阶段 4：第二轮审查发现的问题（Day 1-2）

### 4.1 问题 N1（🔴 严重）：AuditLogAspect SpEL 字段链解析全部失效

#### 4.1.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [AuditLogAspect.java#L106-L112](file:///d:\code\iot-platform\src\main\java\com\example\platform\common\aspect\AuditLogAspect.java#L106-L112) — `buildDetail()` 方法 |
| **影响范围** | 全部 6 个 Controller 中的 **8 处 `@AuditLog` 注解** |
| **严重性** | 🔴 严重 |
| **类型** | 功能缺陷（审计日志输出错误） |

#### 4.1.2 影响分析

`buildDetail()` 的当前实现只做简单字符串替换——将 `#paramName` 替换为参数的 `toString()` 结果。但全部 8 处 `@AuditLog` 注解都使用了 `#paramName.fieldName` 语法（如 `#device.deviceName`、`#user.username`），导致：

- `#device` 被替换为 Device 对象的 `toString()` 全文
- `.deviceName` 作为字面文本保留

**实际输出示例**：
```
"设备名称:Device(id=1, deviceName=温度传感器, deviceCode=TEMP-001, ...).deviceName"
```
**期望输出**：
```
"设备名称:温度传感器"
```

受影响的注解列表：

| 文件 | 行号 | 当前 detail 表达式 |
|------|:----:|--------------------|
| [DictMetricController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DictMetricController.java) | 39 | `#metric.metricCode, #metric.metricName` |
| [DictMetricController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DictMetricController.java) | 47 | `#metric.id` |
| [DeviceController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DeviceController.java) | 69 | `#device.deviceName, #device.deviceCode` |
| [DeviceController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DeviceController.java) | 77 | `#id, #device.deviceName` |
| [AlertController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\AlertController.java) | 75 | `#dto.ids, #dto.handleUser` |
| [SysUserController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\SysUserController.java) | 65 | `#user.username, #user.role` |
| [SysUserController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\SysUserController.java) | 73 | `#id, #user.username` |
| [DeviceTypeController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DeviceTypeController.java) | 46 | `#deviceType.typeName, #deviceType.typeCode` |
| [DeviceTypeController.java](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DeviceTypeController.java) | 53 | `#id, #deviceType.typeName` |

#### 4.1.3 根本原因

`buildDetail()` 方法设计时仅考虑了 `#paramName` 的简单替换场景，未实现 SpEL 表达式引擎来解析 `#paramName.fieldName` 链式访问。

#### 4.1.4 修正措施

两种方案可选：

| 方案 | 描述 | 复杂度 | 优点 | 缺点 |
|:----:|------|:------:|------|------|
| **A（推荐）** | 引入 Spring SpEL 引擎解析 `#param.field` 语法 | 低（加 1 个依赖调用来解析） | 功能完整，支持任意深度的属性链 | 需理解 SpEL API |
| **B** | 保持简单替换，用反射按 `.` 拆分链访问字段值 | 中 | 不引入新依赖 | 需自行处理 null/NPE/嵌套 |

**推荐方案 A 实施步骤**：

| 步骤 | 操作 | 涉及文件 |
|:----:|------|----------|
| 4.1 | 修改 `buildDetail()`：用 `SpelExpressionParser` 解析 `#param.field` 语法 | AuditLogAspect.java |
| 4.2 | 创建 `StandardEvaluationContext`，将方法参数名→参数值注册为变量 | AuditLogAspect.java |
| 4.3 | 若 SpEL 解析失败（如字段不存在），降级到原简单替换逻辑 | AuditLogAspect.java |
| 4.4 | 添加 null 安全处理：SpEL 配置 `setAutoGrowNullReferences(false)` | AuditLogAspect.java |
| 4.5 | 编写单元测试验证：`buildDetail("名称:#device.deviceName", args)` → `"名称:设备A"` | AuditLogAspectTest.java 新增 |

```java
// 核心修改（buildDetail 方法替换为 SpEL 实现）
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

private static final SpelExpressionParser PARSER = new SpelExpressionParser();

private String buildDetail(String template, JoinPoint joinPoint) {
    if (template == null || template.isBlank()) {
        return "";
    }
    // 检测是否包含 SpEL 表达式（#param.field 模式）
    if (!template.contains("#")) {
        return template;
    }
    
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] paramNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();
    
    StandardEvaluationContext context = new StandardEvaluationContext();
    for (int i = 0; i < paramNames.length && i < args.length; i++) {
        context.setVariable(paramNames[i], args[i]);
    }
    
    try {
        return PARSER.parseExpression(template, new TemplateParserContext()).getValue(context, String.class);
    } catch (Exception e) {
        log.warn("SpEL 解析失败，降级到简单替换: template={}, error={}", template, e.getMessage());
        // 降级：保持原逻辑
        String result = template;
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            String value = args[i] == null ? "null" : args[i].toString();
            result = result.replace("#" + paramNames[i], value);
        }
        return result;
    }
}
```

#### 4.1.5 验收标准

- `@AuditLog(detail = "设备名称:#device.deviceName")` 审计日志输出为 `"设备名称:温度传感器"`
- 无字段链的简单 `#paramName` 兼容
- SpEL 解析异常时降级到 `toString()` 不抛异常
- 单元测试覆盖：无 `#` 的模板、`#param.field`、多层字段链、null 值

#### 4.1.6 工作量预估

| 任务 | 工时 |
|------|:----:|
| AuditLogAspect 修改 | 1.0h |
| 单元测试 | 0.5h |
| 验证所有 @AuditLog 输出正确 | 0.5h |
| **小计** | **2.0h** |

---

### 4.2 问题 N2（🔴 严重）：DictMetricController 的 @AuditLog 引用不存在字段

#### 4.2.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [DictMetricController.java#L39](file:///d:\code\iot-platform\src\main\java\com\example\platform\controller\DictMetricController.java#L39) |
| **错误** | `detail = "指标编码:#metric.metricCode"`，但 DictMetric 实体字段名为 **`metricKey`**（非 `metricCode`） |
| **严重性** | 🔴 严重 |
| **类型** | 字段引用错误 |

#### 4.2.2 影响分析

- 修复 N1 后，SpEL 会尝试访问 `metric.metricCode` 属性
- DictMetric 实体中无 `getMetricCode()` 方法
- SpEL 解析将抛出异常，降级到 `toString()` 输出
- 审计日志记录不会中断业务流程（catch 住了），但日志内容错误

#### 4.2.3 修正措施

| 步骤 | 操作 | 位置 |
|:----:|------|------|
| 4.6 | `"指标编码:#metric.metricCode"` → `"指标编码:#metric.metricKey"` | DictMetricController.java:39 |

**工作量**：1 分钟

---

### 4.3 问题 N3（🟡 中等）：WebSocketConfig 全源跨域许可

#### 4.3.1 问题详情

| 维度 | 内容 |
|------|------|
| **发现位置** | [WebSocketConfig.java#L30](file:///d:\code\iot-platform\src\main\java\com\example\platform\config\WebSocketConfig.java#L30) |
| **代码** | `.setAllowedOriginPatterns("*")` |
| **严重性** | 🟡 中等 |
| **类型** | 安全风险 |

#### 4.3.2 影响分析

`*` 允许任意来源发起 WebSocket 连接。生产环境下可能被恶意站点利用建立 WebSocket 连接，消耗服务端资源（尽管 STOMP 需要 JWT 认证才能订阅实际消息）。

#### 4.3.3 修正措施

| 步骤 | 操作 |
|:----:|------|
| 4.7 | 将 `"*"` 改为从配置文件读取的允许源列表，开发环境继续使用通配符 |
| 4.8 | 在 `application-dev.yaml` 中添加 `websocket.allowed-origins=http://localhost:*` |
| 4.9 | 在 `application-prod.yaml` 中添加具体的生产环境域名 |

```yaml
# application.yaml 新增
websocket:
  allowed-origins: ${WEBSOCKET_ALLOWED_ORIGINS:http://localhost:*}
```

```java
// WebSocketConfig 修改
@Value("${websocket.allowed-origins:http://localhost:*}")
private String[] allowedOrigins;

// registerStompEndpoints 中
registry.addEndpoint("/ws")
        .setAllowedOriginPatterns(allowedOrigins)
        .withSockJS();
```

**工作量**：0.3 小时

---

### 4.4 阶段 4 交付物清单

| # | 交付物 | 类型 |
|:-:|--------|:----:|
| D1 | AuditLogAspect.java — SpEL 解析 + 异常降级 | 代码变更 |
| D2 | AuditLogAspectTest.java — SpEL 解析单元测试 | 测试 |
| D3 | DictMetricController.java — metricCode→metricKey | 代码变更 |
| D4 | WebSocketConfig.java + application.yaml — 跨域配置外部化 | 代码变更 |

---

## 5. 整体时间线

```
时间线（天）     1         2         3
                │         │         │
阶段3：P3-2     ██
阶段4：N1-N3              ████████
阶段3：P3-6/7                        ████████
                │         │         │
里程碑          M1        M2        M3
```

| 里程碑 | 时间 | 验收内容 |
|--------|:----:|----------|
| M1 | Day 1 | README 建表 SQL 全部含 deleted 列 |
| M2 | Day 2 | AuditLog SpEL 解析上线 + 单元测试通过 + WebSocket CORS 配置化 |
| M3 | Day 3 | 浏览器兼容验证 + JMeter 压测报告 |

---

## 6. 问题完整索引

| ID | 严重级 | 模块 | 文件 | 阶段 | 当前状态 |
|:--:|:------:|:----:|------|:----:|:--------:|
| P1-1 | 🔴 | 配置 | DictMetric.java | 1 | ✅ 已完成 |
| P1-2 | 🔴 | 基础设施 | V1__init_schema.sql | 1 | ✅ 已完成 |
| P1-3 | 🔴 | 告警 | V1__init_schema.sql | 1 | ✅ 已完成 |
| P2-1 | 🟡 | 安全 | AuthController.java | 2 | ✅ 已完成 |
| P2-2 | 🟡 | 文档 | README.md | 2 | ✅ 已完成 |
| P2-3 | 🟡 | 文档 | IoT-Platform需求规格说明书.md | 2 | ✅ 已完成 |
| P2-4 | 🟡 | 告警 | notification_log | 2 | ✅ 已完成 |
| P3-1 | 🔵 | 文档 | IoT-Platform需求规格说明书.md | 2 | ✅ 已完成 |
| P3-2 | 🔵 | 文档 | README.md | 3 | ⏳ 待修复 |
| P3-3 | 🔵 | 文档 | README.md | 2 | ✅ 已完成 |
| P3-4 | 🔵 | 基础设施 | SysUser/SysAuditLog | — | ✅ 无需修改 |
| P3-6 | 🔵 | QA | 浏览器兼容 | 3 | ⏳ 待执行 |
| P3-7 | 🔵 | QA | JMeter 压测 | 3 | ⏳ 待执行 |
| **N1** | **🔴** | **通用** | **AuditLogAspect.java** | **4** | **⏳ 待修复** |
| **N2** | **🔴** | **配置** | **DictMetricController.java** | **4** | **⏳ 待修复** |
| **N3** | **🟡** | **基础设施** | **WebSocketConfig.java** | **4** | **⏳ 待修复** |

---

## 7. 风险评估

| 风险 | 概率 | 影响 | 应对 |
|------|:----:|:----:|------|
| SpEL 解析异常导致审计日志切面抛出未捕获异常 | 低 | 高 | 全程 `try-catch`，异常降级到原 `toString()` 逻辑 |
| WebSocket 跨域配置修改导致前端连接失败 | 中 | 高 | 先验证环境变量默认值 `http://localhost:*` 与开发环境兼容 |
| README 建表 SQL 仍遗漏某张表 | 低 | 低 | 检查 Flyway V3 中实际列变更的表清单，逐表核对 |

---

> **本文档遵循**：[需求文档标准化方案 V1.0.0](./需求文档标准化方案.md)
