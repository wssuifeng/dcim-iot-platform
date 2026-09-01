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
- **当前版本**：`refactor/depivot` 分支（README 路线图阶段一~八全部完成，当前为阶段八真实用户打磨基线）；历史 v4.x 文档状态仅作参考
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
- **API 文档**：接口说明用 Swagger 注解（`@Tag`/`@Operation`/`@Schema`），启动后访问 `/api/swagger-ui.html`（context-path 为 `/api`），**不要再在 src 下创建 README**

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
- ❌ 不要在 Service 接口描述与 ServiceImpl 实现之间制造矛盾（曾发生缓存描述冲突）
- ❌ 不要引用已删除的 v1.0 老链（DeviceData/Alert/DictMetric/NotificationLog/`getEffectiveRuleConfig`）——现行链是 ingest 门 + MetricSample + AlarmEvent 状态机 + V15 告警模版
- ❌ 不要用 `updateById` 显式置空字段——MyBatis-Plus 会忽略 null（解绑告警模版等场景必须用 `LambdaUpdateWrapper.set`，踩过坑）
- ❌ 不要让任何 Java 类完全无注释——至少要有类级功能描述
- ❌ **不要在企微智能机器人渠道启用状态下启动开发/测试平台**——该渠道（`alarm_webhook_channel` 中 `channel_type=WECOM_AIBOT`）已接入用户真实企微群，测试告警会直接骚扰真实用户。起平台前先置 `enabled=0`（渠道管理页开关或直接改库），测完按需恢复；凭证（botId/secret）用户可能在企微后台重置，订阅失败先想到凭证已换

---

## 5. 测试与构建命令

| 命令 | 用途 |
|------|------|
| `mvn test` | 平台全量单元测试（370 用例） |
| `mvn test -Dtest=DeviceServiceImplTest` | 单个测试类 |
| `mvn spring-boot:run` | 启动应用（需先起 MySQL + Redis） |
| `mvn clean compile` | 仅编译（快速验证语法） |
| `cd simulator && mvn test` | 仿真服务测试（独立子项目，独立 H2） |
| `cd frontend && npm run test` | 前端 Vitest 单元测试 |

**启动前置条件**：MySQL 运行中（库 `iot_device_db`，与 `application-dev.yaml` / `.env.example` 默认 `DB_URL` 一致）、Redis 运行中、初始数据中 `sys_user` 表存在 ID=1 的管理员账号（测试依赖）。本机端口约定：平台 8081/前端 5175/仿真器 8090；8080、5173、5174 属于用户其他项目，**绝不占用**。

---

## 6. 文档导航（新会话按需读取）

| 任务类型 | 读什么 |
|---------|--------|
| 任意任务（必读） | `README.md` + 本文件 |
| 第四阶段产品化任务 | 外部公共控制包中的 `01-全员必读.md`、任务分发清单和当前 Agent 任务卡 |
| 新增/改需求 | 仓库内 `docs/`（ADR + 阶段计划书 + 文档导览）为主；外部公共控制包仅适用于 stage4 工作流任务 |
| 改业务代码 | `README.md` 结构树 → **直接读对应 `.java`**（代码最权威，勿读已删除的旧 README） |
| 改 API / 查接口 | 启动后访问 `/api/swagger-ui.html`（实时从注解生成） |
| 学习某模块原理 | 直接读对应源码、测试、Swagger 注解和公共控制包中的共享契约 |
| 涉及架构决策 | `git log` + 相关代码注释 |

---

## 7. 当前状态

- **当前基线**：`refactor/depivot` 分支（阶段八真实用户打磨完成，最新提交见 `git log`）
- **协作主仓库**：`git@github.com:wssuifeng/dcim-iot-platform.git`
- **上游基础仓库**：`git@github.com:wssuifeng/iot-platform.git`，仅作 v1.0 参考
- **集成目标**：`stage4/integration`（第四阶段工作流的历史目标；阶段五/六工作直接在 `refactor/depivot` 进行）
- **当前工作流**：阶段五/六沿 README 路线图直接在 `refactor/depivot` 提交；stage4 多 Agent 门控流程仅适用于第四阶段任务

---

## 8. 关键业务模型速查

> 以实体类与 Flyway 迁移（V1–V24）为准。机房即接入主体，设备零预置，首包自动注册建档。

| 实体 | 表 | 要点 |
|------|----|------|
| CloudRoom | sys_cloud_room | 机房 = 接入主体：创建即初始化 AccessKey/SecretKey/TB token/MQTT 子凭证，网关凭签名上报；驾驶舱健康度/网关状态由统计聚合计算 |
| Device | devices | `deviceCode` 全局寻址；`alarmTemplateId` 设备级模版绑定（可空，为空跟随类型） |
| DeviceType | device_types | 类型目录 + `alarmTemplateId` 基础模版绑定；类型级指标模板（device_type_point_template） |
| PointDefinition / DevicePointBinding | point_definition / device_point_binding | 平台级指标字典与设备指标绑定 |
| MetricSample / MetricLatest / MetricRetentionPolicy | metric_sample / metric_latest / metric_retention_policy | 采样时序（V21 起按月分区，主键 `(id, sample_time)`）+ 最新值 + TTL 保留策略 |
| MetricSampleHourly | metric_sample_hourly | V21 小时聚合表：>48h 趋势查询走此表（cnt 加权均值） |
| AlarmTemplate / AlarmTemplateItem | alarm_template / alarm_template_item | V15 一级告警模版实体：规则融合模版，类型绑基础版、设备可改绑自定义版、复制即自定义起点；V19 起规则项含三机制字段（连续越限 streak / 窗口变化量 windowDelta / 再告警 resend） |
| AlarmWebhookChannel | alarm_webhook_channel | V20 IM webhook 渠道：WECOM_AIBOT（长连接）/DINGTALK/FEISHU/NTFY/TELEGRAM/GENERIC，`credentialJson` 结构随类型 |
| AlarmEvent / AlarmStateTransition / AlarmHandlingRecord | alarm_event / alarm_state_transition / alarm_handling_record | 告警状态机 TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED；`alarmLevel` 1-5（数字越小越紧急），逻辑删除；V22 起机房可配 ALARM_UPGRADED 下行动作 webhook |
| InAppNotification / AlarmNotificationLog | in_app_notification / alarm_notification_log | 站内通知（头部铃铛模态框）与通知发送日志/去重 |
| SysUser | sys_user | 单租户三角色：ADMIN/OPERATOR/VIEWER（role 写入 JWT） |
| SysAuditLog | sys_audit_log | 结构化操作审计 |

**告警模版解析链**：设备 `alarmTemplateId` 优先，为空降级类型 `alarmTemplateId`（自动注册设备零配置跟随类型）；解绑必须用 `LambdaUpdateWrapper.set` 显式置空。

**告警评估与升级**：`AlarmEvaluateServiceImpl` 对上报采样做模版规则阈值评估，命中最严重级别触发；`rule.level < 现有告警级别` 时升级（数字小 = 更紧急）。

**数据入口（四条，汇入同一条传输无关核心）**：
1. HMAC 通道 HTTP `POST /api/ingest/samples`（`Authorization: Gateway {AK}:{sha256(SK+body)}`）；
2. HMAC 通道 MQTT `dcim/{accessKey}/samples`（报文 `{signature,batch}`，含遗嘱/LWT 网关离线即时告警）；
3. TB 兼容开放通道 `POST /api/v1/{token}/telemetry`（token 鉴权无签名，TB 三形态报文 + device 字段，`TelemetryCompatController`，ADR-008）；
4. MQTT 子凭证开放通道 `dcim-open/{mqttUsername}/samples`（账号密码连接层鉴权无签名，broker 侧 dynamic-security）。

处理链：机房归属校验 → `deviceCode+pointCode+seq` 幂等去重（开放通道无 seq 降级 `+sampleTime`）→ 采样落库 → 告警评估与通知。旧 v1.0 链（DeviceData/Alert/DictMetric/NotificationLog）已删除，勿再引用。

**仿真服务**：`simulator/` 子目录（阶段八并入本仓库，独立进程独立构建，默认 :8090）——虚拟 Modbus TCP 设备 + 智能网关 + 北向双传输客户端，是阶段七/八的主力验证工具（压测、MQTT 通道、三机制演练均基于它）；详见 `simulator/README.md`。
