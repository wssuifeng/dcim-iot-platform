# AGENTS.md — AI Agent 上下文文件

> 任何新 AI 会话第一步：读本文件 + `README.md` 建立现有工程全貌。
>
> 当前仓库是 `dcim-iot-platform` 产品化仓库，基于 `wssuifeng/iot-platform` v1.0 演进。第四阶段产品化开发以外部公共控制包为事实来源：角色模型、数据范围、迁移策略、分支、合并顺序和 Agent 分工若与本文或 `README.md` 冲突，以第四阶段控制包为准。
>
> 第四阶段公共控制包位于本机文档工作区：`第四阶段-分阶段实现与测试/05-公共协作区/01-实施控制包/`。
>
> 如果当前 AI 会话只打开了本目标仓库，看不到主控文档工作区，则读取仓库根目录的本地分发包：`开发控制文档/00-本地分发包说明.md`。`开发控制文档/` 只用于任务分发和报告暂存，已本地排除提交，禁止加入 Git。

---

## 1. 项目身份

- **项目**：dcim-iot-platform — 数据中心动环监控平台产品化仓库，基于 IoT-Platform v1.0 演进
- **技术栈**：Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + **MySQL 9.6.0** + Redis 7 + JDK 17
- **当前版本**：第四阶段产品化基线（历史代码约等于 v4.2/v4.4 文档状态）
- **分层架构**：Controller → Service（接口） → ServiceImpl → Mapper → Entity

---

## 2. 版本约束（改代码时必须遵守，踩过坑）

| 组件 | 版本 | 注意事项 |
|------|------|---------|
| Spring Boot | **4.0.6** | 是 4.x 不是 3.x。Flyway 模块化、Security API 与 3.x 有差异 |
| MySQL Connector | **9.6.0**（mysql-connector-j） | 驱动类与 8.x 行为不同，连接参数有差异 |
| MyBatis-Plus | 3.5.16 | 分页插件、LambdaQueryWrapper、自动填充 |
| Druid | 1.2.28 | Spring Boot 4 专用 Starter |
| JWT | jjwt | 无状态认证 |
| Redis | 7.x + Lettuce 6.x | 异步非阻塞客户端 |

---

## 3. 代码约定

- **统一响应**：`Result<T>` + `ResultCode`（**不要硬编码 HTTP 状态码**，扩展 `ResultCode` 枚举）
- **异常**：抛 `BusinessException` 子类，由 `GlobalExceptionHandler` 统一兜底
- **分页**：`size ≤ 100, current ≥ 1`（用 `PaginationUtils.validate()` 校验）
- **审计**：关键写操作加 `@AuditLog` 注解
- **注入方式**：Lombok `@RequiredArgsConstructor` 构造器注入，**不用 `@Autowired` 字段注入**
- **缓存**：统一走 `CacheService` 接口，**不直接使用 `RedisTemplate`**
- **XSS 防御**：请求参数由 `XssFilter` 统一 HTML 转义
- **API 文档**：接口说明用 Swagger 注解（`@Tag`/`@Operation`/`@Schema`），启动后访问 `/swagger-ui.html`，**不要再在 src 下创建 README**

### 3.1 注释规范

本项目存在两套并存注释风格，**新增代码必须遵循所属分层的规范，不得混用**：

#### 3.1.1 分层注释规范速查

| 分层 | 类级注释 | 方法级注释 | 字段注释 |
|------|---------|-----------|---------|
| **Controller** | `/** Javadoc */` + `@Tag` | `@Operation`（无需 Javadoc） | N/A |
| **Service 接口** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | N/A |
| **ServiceImpl** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | `//` 行内注释（常量说明） |
| **Entity** | `/** Javadoc */` + `@Schema` | N/A | `@Schema` 注解（不使用 `//`） |
| **DTO/VO** | `/** Javadoc */` + `@Schema` | N/A（静态工厂方法不加注释） | `@Schema` 注解（不使用 `//`） |
| **Mapper** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | N/A |
| **Config** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | `//` 行内注释 |
| **Exception** | `/* 类功能:... */` | 构造器不加注释 | N/A |

#### 3.1.2 详细规则

**Controller 层：**
- 类级写 `/** 中文功能描述 */` + Swagger `@Tag(name = "...")`
- 方法级 **只写 `@Operation`**，参数用 `@Parameter`，不写 Javadoc `@param`/`@return`
- 需要声明错误响应时加 `@ApiResponse`

**Service 接口 / ServiceImpl / Mapper 层（老式 `/* */` 块注释）：**
- 类级写 `/* 类功能:中文描述 */`
- 方法级写 `/* 方法功能:... 参数:... 返回数据:... */`，多参数用 `-` 分隔：`参数:name-描述, age-描述`
- 无参或无返回值写 `参数:无` 或 `返回数据:无`
- 附加说明写 `说明:...`
- ServiceImpl 中 `private` 辅助方法不加注释
- 常量字段用 `//` 行内注释

**Entity / DTO / VO 层（Swagger 注解为主）：**
- 类级写 `/** 中文功能描述 */` + `@Schema(description = "...")`
- 字段级 **只写 `@Schema`**，用 `description`、`example`、`requiredMode` 等属性，**不在字段上写 `//` 注释**
- 静态工厂方法（`fromEntity` 等）不加注释

**公共规则：**
- 不允许在类或方法上完全无注释（即使只有一行类功能描述）
- 不使用 `@author`、`@since`、`@version`、`@deprecated` 等 Javadoc 标准标签
- 不使用 `@param`、`@return`、`@throws` 等标准 Javadoc 标签（Swagger 或 `参数:/返回数据:` 替代）

---

## 4. 禁忌（不要做）

- ❌ 不要在 src 各包下创建 `*README.md`（已清理，会重新冗余、漂移）
- ❌ 不要直接操作 `RedisTemplate`，走 `CacheService`
- ❌ 不要硬编码错误码，扩展 `ResultCode` 枚举
- ❌ 不要在实体类用 `@Autowired`
- ❌ 不要在 Service 接口描述与 ServiceImpl 实现之间制造矛盾（曾发生 DictMetric 缓存描述冲突）
- ❌ 不要把 DictMetric 描述成 "ConcurrentHashMap 内存缓存"——**已迁移到 Redis**（v4.0）
- ❌ 不要让任何 Java 类完全无注释——至少要有类级功能描述

---

## 5. 测试与构建命令

| 命令 | 用途 |
|------|------|
| `mvn test` | 全量单元测试 |
| `mvn test -Dtest=DeviceServiceImplTest` | 单个测试类 |
| `mvn spring-boot:run` | 启动应用（需先起 MySQL + Redis） |
| `mvn clean compile` | 仅编译（快速验证语法） |

**启动前置条件**：MySQL 运行中（库 `iot_platform`）、Redis 运行中、初始数据中 `sys_user` 表存在 ID=1 的管理员账号（测试依赖）。

---

## 6. 文档导航（新会话按需读取）

| 任务类型 | 读什么 |
|---------|--------|
| 任意任务（必读） | `README.md` + 本文件 |
| 第四阶段产品化任务 | 外部公共控制包中的 `01-全员必读.md`、任务分发清单和当前 Agent 任务卡 |
| 新增/改需求 | 外部公共控制包和第三阶段最终设计；已删除的 `开发流程学习文档/` 不再作为事实来源 |
| 改业务代码 | `README.md` 结构树 → **直接读对应 `.java`**（代码最权威，勿读已删除的旧 README） |
| 改 API / 查接口 | 启动后访问 `/swagger-ui.html`（实时从注解生成） |
| 学习某模块原理 | 直接读对应源码、测试、Swagger 注解和公共控制包中的共享契约 |
| 涉及架构决策 | `git log` + 相关代码注释 |

---

## 7. 当前状态

- **当前产品化基线**：`0b6b0cc chore: remove obsolete productization docs`
- **协作主仓库**：`git@github.com:wssuifeng/dcim-iot-platform.git`
- **上游基础仓库**：`git@github.com:wssuifeng/iot-platform.git`，仅作 v1.0 参考
- **集成目标**：`stage4/integration`
- **当前工作流**：多 Agent 独立分支开发，PR 或补丁先合入 `stage4/integration`，通过门控后再进入 `main`
- **产品化控制包**：第四阶段公共控制包优先于本文中的历史路线图、旧角色和旧迁移说明

---

## 8. 关键业务模型速查

> 本节是 v1.0 基础模型速查。第四阶段产品化将引入 `PLATFORM_ADMIN`、`ORG_ADMIN`、组织、机房、数据范围、设备点位、告警状态机和站内通知等新模型，具体以公共控制包为准。

| 实体 | 表 | 要点 |
|------|----|------|
| Device | devices | 设备 CRUD，`status`：0-离线/1-在线/2-故障 |
| DeviceType | device_types | 设备类型，承载类型级告警规则（降级方案） |
| DeviceData | device_data | 上报数据，单条/批量，触发告警检查 |
| Alert | alerts | 告警记录，`alertLevel`：1-紧急 … 5-提示（数字越小越紧急） |
| DictMetric | dict_metric | 指标元数据，Redis 缓存，填充告警消息中文/单位 |
| SysUser | sys_user | RBAC 三级角色：ADMIN/OPERATOR/VIEWER |
| SysAuditLog | sys_audit_log | 操作审计，过期自动清理 |
| NotificationLog | notification_log | 通知发送日志，记录 EMAIL/WEBSOCKET 推送结果 |

**告警规则降级策略**：设备级配置优先，缺失时降级到类型级配置（`getEffectiveRuleConfig`）。

**告警升级判定**：`rule.level < existingAlert.alertLevel` 时升级（数字小 = 更紧急）。
