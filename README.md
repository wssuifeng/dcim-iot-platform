# dcim-iot-platform 数据中心动环监控平台

> **产品化仓库**: 基于 `wssuifeng/iot-platform` v1.0 演进 | **当前基线**: `5d26eeb`（认证与产品化基础集成） | **第四阶段**: 分阶段实现与测试

本仓库是数据中心动环监控平台（DCIM）产品化版本。v1.0 单租户物联网平台已演进为多组织多租户架构，本文档按模块拆分描述当前后端 30 个控制器 / 29 个实体 / 44 个服务与前端 15 个业务页面的真实状态。

第四阶段产品化开发的事实来源是外部公共控制包：

```text
第四阶段-分阶段实现与测试/05-公共协作区/01-实施控制包/
```

如果当前 Agent 只打开了本仓库，无法访问主控文档工作区，请先读取本地分发包：

```text
开发控制文档/00-本地分发包说明.md
```

`开发控制文档/` 是未纳入 Git 的分发副本，不是产品代码或正式项目文档，禁止提交。

若本 README 与公共控制包在角色模型、数据范围、迁移策略、分支与合并顺序上冲突，以公共控制包为准。开发时应读取本 README 和 `AGENTS.md` 了解技术栈、目录结构、构建命令与各模块行为。

---

## 一、项目简介

dcim-iot-platform 是面向数据中心动力环境监控的多组织 SaaS 化平台。后端基于 Spring Boot 4 + MyBatis-Plus，通过 JWT 认证 + 组织上下文（`X-Organization-Id`）+ 四级数据范围实现"一个平台、多组织隔离"；前端基于 Vue 3 + Vite + Element Plus，提供 15 个业务页面与日间/夜间双主题管理后台。

### 核心能力

| 能力 | 说明 |
|------|------|
| 多组织多租户 | 组织/机房两级资源模型、成员关系、创建/加入组织审批流、顶栏组织切换 |
| 数据范围控制 | PLATFORM / ORG_ALL / ROOMS / READ_ONLY 四级数据范围，机房粒度授权，越权防护 |
| 认证与鉴权 | JWT 无状态认证、四角色 RBAC（PLATFORM_ADMIN/ORG_ADMIN/OPERATOR/VIEWER）、Token 黑名单、账号锁定、IP 限流、邮箱验证码注册、平台初始化向导 |
| 设备与点位 | 设备/设备类型 CRUD 与 Excel 导入导出、点位定义、类型级点位模板、设备点位绑定 |
| 指标时序 | 指标字典（Redis 缓存）、metric_sample 时序采样、metric_latest 最新值、保留策略与每日 TTL 清理 |
| 告警状态机 | 设备级/类型级规则引擎（降级策略）、五级告警、TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED 状态流转、处理与流转留痕 |
| 通知 | 邮件通知、WebSocket（STOMP）用户隔离摘要推送、站内通知未读管理、通知日志与去重 |
| 仿真推演 | 模拟场景/步骤编排、异步运行与取消、生成数据引用可追溯 |
| 审计与可观测 | `@AuditLog` 切面结构化审计、TraceId 贯穿日志与异步线程、Druid 慢 SQL 监控、Actuator 健康检查 |
| 离线检测 | 定时任务扫描离线设备，自动生成离线告警（去重） |
| 前端管理后台 | 15 个业务页面 + 注册/平台初始化公开页、双主题（SaaS 浅色 / SCADA 深色）、实时摘要驱动刷新 |
| 容器化部署 | Dockerfile + docker-compose（app + MySQL 9.6 + Redis 7）一键启动，生产安全门控 |

---

## 二、技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架（4.x 而非 3.x，Flyway/Security API 有差异） |
| Spring Security | - | JWT 过滤链、四角色 RBAC、组织上下文过滤、端点访问控制 |
| Spring WebSocket | - | STOMP + SockJS 实时摘要推送，端点 `/api/ws` |
| MyBatis-Plus | 3.5.16 | ORM（spring-boot4-starter），分页/乐观锁/自动填充 |
| Druid | 1.2.28 | 连接池 + SQL 监控（druid-spring-boot-4-starter） |
| MySQL Connector | 9.6.0 | mysql-connector-j 驱动 |
| Flyway | - | V1–V10 版本化迁移，`validate-on-migrate` 强制校验 checksum |
| jjwt | 0.12.6 | JWT 签发与解析 |
| SpringDoc OpenAPI | 3.0.3 | `/swagger-ui.html` 交互式文档 |
| EasyExcel | 4.0.3 | Excel 流式导入导出 |
| Spring Retry | 2.0.11 | 数据上报死锁自动重试 |
| Spring Mail | - | 告警邮件与验证码邮件 |
| Redis / Lettuce | 7.x / 6.x | 缓存（统一走 `CacheService`，不直接用 RedisTemplate） |
| commons-pool2 | 2.x | Lettuce 连接池 |
| Jakarta Validation | - | 声明式参数校验（CreateGroup/UpdateGroup 分组） |
| Lombok / Jackson | - | 样板代码 / JSON（`JacksonTypeHandler` 处理 JSON 字段） |
| Vue | 3.5.32 | 前端框架（组合式 API） |
| Vite | 8.0.8 | 前端开发服务器与构建，端口 `5173` |
| Element Plus | 2.14.1 | UI 组件库（主题变量整表覆盖） |
| Vue Router / Pinia | 4.6.4 / 3.0.4 | 路由与登录态/组织/数据范围守卫 / 状态管理 |
| Axios | 1.16.1 | HTTP 客户端，统一请求/响应拦截 |
| ECharts / vue-echarts | 6.1.0 / 8.0.1 | 仪表盘与数据趋势图表（主题自适应重绘） |
| @stomp/stompjs + sockjs-client | 7.3.0 / 1.6.1 | 前端 STOMP 实时通道 |

---

## 三、项目结构

```
iot-platform/
├── src/
│   ├── main/
│   │   ├── java/com/example/platform/          # 后端主源码（282 个 Java 文件）
│   │   │   ├── IotPlatformApplication.java     # 启动入口
│   │   │   ├── common/                         # 通用基础设施
│   │   │   │   ├── Result / ResultCode         # 统一响应（50 个错误码 + 14 个稳定字符串码）
│   │   │   │   ├── annotation/aspect/          # @AuditLog 注解 + 审计切面
│   │   │   │   ├── exception/                  # BusinessException 体系 + GlobalExceptionHandler
│   │   │   │   ├── filter/                     # 限流 / TraceId / XSS 过滤器
│   │   │   │   ├── constant/ group/ util/      # 登录失败 Lua 脚本、校验分组、JwtUtil 等工具
│   │   │   ├── config/                         # Security/Redis/MyBatis-Plus/CORS/WebSocket/Async/OpenAPI/生产安全门控
│   │   │   ├── security/                       # JWT 过滤器、PlatformRole 角色模型、组织上下文、WebSocket 鉴权（14 个类）
│   │   │   ├── controller/                     # 30 个 REST 控制器
│   │   │   ├── dto/                            # 请求/响应 DTO 与 VO（55 个）
│   │   │   ├── entity/                         # 实体（29 个，见"四、后端模块详解"数据模型总览）
│   │   │   ├── mapper/                         # Mapper 接口（29 个，BaseMapper + 注解式，无 XML）
│   │   │   ├── service/ + service/impl/        # 44 个服务接口 + 42 个实现（含 AlertRuleEngine）
│   │   │   └── task/                           # DeviceMonitorTask（离线检测）、DataCleanupTask（TTL 清理）
│   │   └── resources/
│   │       ├── application.yaml                # 公共配置（profile 默认 dev）
│   │       ├── application-dev.yaml            # 开发环境（Druid 监控、限流放宽）
│   │       ├── application-prod.yaml           # 生产环境（全量环境变量化 + SSL + 安全门控）
│   │       └── db/migration/                   # Flyway V1–V10
│   └── test/                                   # 64 个测试类（controller/service/mapper/migration/security/config）
│       └── jmeter/                             # 性能基准测试计划（perf-quick.jmx 等）
├── frontend/                                   # 前端（Vue3 + Vite + Element Plus，见"五、前端模块详解"）
├── SQL/                                        # SQL 参考脚本
├── docs/                                       # 设计规格与实施计划（superpowers/specs|plans）
├── Dockerfile / docker-compose.yml             # 容器化部署（app + MySQL 9.6 + Redis 7）
├── .env.example                                # 环境变量模板（15 个变量）
├── AGENTS.md                                   # AI Agent 上下文（技术栈/约定/禁忌）
├── pom.xml / mvnw / mvnw.cmd                   # Maven 配置与 Wrapper
└── README.md                                   # 本文件
```

---

## 四、后端模块详解

### 4.0 数据模型总览

```
sys_user ──< organization_membership >── sys_organization ──< sys_cloud_room
    │                                              └──< devices
    └──< sys_data_scope_assignment（四级数据范围）

device_types ──< devices ──< device_data / alerts（v1.0 遗留兼容）
                   │
                   ├──< device_point_binding >── point_definition
                   │          └──< metric_sample ── metric_latest
                   └──< alarm_event ──< alarm_handling_record / alarm_state_transition

alarm_event ──< notification_log / in_app_notification
simulation_scene ──< simulation_scene_step / simulation_run_record ──< simulation_generated_data_ref
```

**v1.0 遗留实体（8 个，仍在线使用）**

| 实体 | 表 | 说明 |
|------|----|------|
| Device | devices | 设备档案，含 `alert_rule_config` JSON（设备级告警规则） |
| DeviceType | device_types | 设备类型，类型级告警规则作为降级方案；V3 起挂载点位模板 |
| DeviceData | device_data | v1.0 上报数据，每日 TTL 清理；新数据走 metric_sample |
| Alert | alerts | v1.0 告警，保留兼容；权威告警为 alarm_event |
| DictMetric | dict_metric | 指标字典，Redis 缓存，告警消息填充中文名/单位 |
| SysUser | sys_user | 全局身份（BCrypt 密码、角色、锁定机制） |
| SysAuditLog | sys_audit_log | 审计日志，V5 扩展结构化字段 |
| NotificationLog | notification_log | 通知发送日志（邮件/WebSocket 双通道追溯） |

**第四阶段新增实体（21 个）**

| 域 | 实体（表） |
|----|-----------|
| 组织与身份 | Organization（sys_organization）、CloudRoom（sys_cloud_room）、OrganizationMembership（organization_membership）、OrganizationCreationApplication、OrganizationJoinApplication、DataScopeAssignment（sys_data_scope_assignment）、PlatformRegistrationSettings（platform_registration_settings） |
| 设备点位 | PointDefinition（point_definition）、DeviceTypePointTemplate（device_type_point_template）、DevicePointBinding（device_point_binding） |
| 指标时序 | MetricSample（metric_sample）、MetricLatest（metric_latest）、MetricRetentionPolicy（metric_retention_policy） |
| 告警状态机 | AlarmEvent（alarm_event）、AlarmHandlingRecord（alarm_handling_record）、AlarmStateTransition（alarm_state_transition） |
| 站内通知 | InAppNotification（in_app_notification） |
| 仿真 | SimulationScene、SimulationSceneStep、SimulationRunRecord、SimulationGeneratedDataRef |

### 4.1 用户认证与鉴权模块

**功能**：登录（账号锁定 + IP 限流）、登出（Token 黑名单）、邮箱验证码注册、平台初始化向导、JWT 无状态认证与四角色 RBAC。

| 能力 | 说明 |
|------|------|
| 登录/登出 | `POST /auth/login` 连续 5 次失败锁定 30 分钟（Lua 脚本原子计数）；`POST /auth/logout` Token 入黑名单自动过期 |
| 注册 | `POST /auth/registration-codes` 申请邮箱验证码（HMAC 防篡改）→ `POST /auth/register`；开关与策略由 PlatformRegistrationSettings 控制 |
| 平台初始化 | `/setup` 向导：首次部署用 bootstrap token（`platform.bootstrap-token`）创建 PLATFORM_ADMIN |
| 角色模型 | `PlatformRole`：PLATFORM_ADMIN / ORG_ADMIN / OPERATOR / VIEWER；旧 `ADMIN` 归一化为 ORG_ADMIN 并授予双 Authority 兼容 |
| JWT 过滤链 | `JwtAuthenticationFilter` 提取 Bearer Token，`JwtAuthenticationService` 统一校验签名/有效期/黑名单（HTTP 与 WebSocket 共用） |
| 请求防护 | `RateLimitFilter`（Redis 滑动窗口）、`TraceIdFilter`（MDC）、`XssFilter`（参数 HTML 转义） |

**关键类**：`AuthController`、`AuthServiceImpl`、`PublicRegistrationServiceImpl`、`EmailVerificationServiceImpl` + `QqVerificationMailSender`、`PlatformSetupServiceImpl`、`TokenBlacklistService`、`common/util/JwtUtil`。

**数据表**：`sys_user`、`platform_registration_settings`。

**测试**：`AuthServiceImplTest`、`JwtAuthenticationFilterTest`、`JwtAuthenticationServiceTest`、`PlatformRoleTest`、`SecurityConfigRoleContractTest` 等 8 个 security 包测试类。

---

### 4.2 组织与数据范围模块

**功能**：组织/机房两级资源模型、成员关系管理、创建/加入组织审批流、四级数据范围授权与组织上下文隔离。

| 能力 | 说明 |
|------|------|
| 组织上下文 | 业务请求携带 `X-Organization-Id` 头，`OrganizationContextFilter` 校验成员资格后注入 `CurrentOrganizationContext`（ThreadLocal）；切换到非成员组织抛 `DataScopeViolationException`（4071/4072） |
| 数据范围 | `sys_data_scope_assignment.scopeType` 四级：PLATFORM（全平台）/ ORG_ALL（组织全部）/ ROOMS（指定机房）/ READ_ONLY（只读），`DeviceScopeHelper` 据此做设备级过滤 |
| 成员管理 | 成员列表、角色变更、移除成员、组织加入设置（开放加入/需审批） |
| 审批流 | 创建组织申请（用户提交 → PLATFORM_ADMIN 审批 → `OrganizationProvisioningService` 开通）；加入组织申请（提交 → ORG_ADMIN 审批），冲突抛 `MembershipConflictException`（4511-4518） |
| 当前上下文查询 | `GET /auth/memberships`（我的组织列表）、`GET /auth/current-scope`（当前数据范围） |

**关键类**：`OrganizationController`、`CloudRoomController`、`OrganizationMemberController`、`OrganizationCreationApplicationController`、`OrganizationJoinApplicationController`、`AccountOrganizationController`、`CurrentScopeController`、`UserDataScopeController`；服务层 `OrganizationService`、`CloudRoomService`、`OrganizationMembershipService`、`OrganizationProvisioningService`、`DataScopeService`、`UserDataScopeService`、`DeviceScopeHelper`。

**数据表**：`sys_organization`、`sys_cloud_room`、`organization_membership`、`organization_creation_application`、`organization_join_application`、`sys_data_scope_assignment`。

### 4.3 用户与平台管理模块

**功能**：全局用户管理（PLATFORM_ADMIN 专属）、用户数据范围指派、平台注册策略与组织申请审批。

| 能力 | 说明 |
|------|------|
| 用户管理 | `/users` 8 个接口：分页/搜索/详情/创建/更新/重置密码/启停/删除；BCrypt 加密、登录失败 SQL 原子递增、更新字段白名单防注入 |
| 数据范围指派 | `/users/{id}/data-scopes`：列表/指派/移除，控制目标用户可见的组织与机房 |
| 注册策略 | `/platform/registration-settings`：注册开关与默认策略（PLATFORM_ADMIN 专属 `/platform/**`） |
| 组织申请审批 | `/platform/organization-applications`：申请列表/批准/拒绝，批准后自动开通组织并建立成员关系 |

**关键类**：`SysUserController`、`SysUserServiceImpl`、`UserDataScopeController`、`PlatformRegistrationSettingsController`、`PlatformRegistrationSettingsService`。

**数据表**：`sys_user`、`sys_data_scope_assignment`、`platform_registration_settings`。

**测试**：`SysUserMapperScopeTest`、`GlobalIdentityContractTest`、`OrganizationMembershipMigrationContractTest`、`RegistrationIdentityMigrationContractTest` 等。

---

### 4.4 设备与点位模块

**功能**：设备/设备类型管理（含 v1.0 的 Excel 导入导出、离线检测）、点位定义、类型级点位模板、设备点位绑定。

| 能力 | 说明 |
|------|------|
| 设备管理 | `/devices` 13 个接口：CRUD、分页/模糊搜索、在线设备、状态更新、设备级告警规则配置、Excel 导入（上限 500 条）/导出/模板下载 |
| 设备类型 | `/device-types` 6 个接口：CRUD + 类型级告警规则（作为设备级缺失时的降级方案）；V3 迁移内置 UPS/PDU_METER/PRECISION_AC/TEMP_HUMIDITY/LEAK_DETECTOR 五类产品类型种子 |
| 点位定义 | `/point-definitions` 4 个接口：点位（温度、湿度、电压等监测点）元数据 CRUD |
| 类型点位模板 | `/point-templates/device-types/{typeId}`：查看/批量保存，新建该类型设备时按模板初始化点位 |
| 设备点位绑定 | `/device-points` 6 个接口：查询/绑定/启停/解绑/按设备类型模板初始化；`DevicePointMetricContract` 约束设备-点位-指标三者关系 |
| 离线检测 | `DeviceMonitorTask` 每 5 分钟扫描（阈值 `task.offline-threshold-seconds`），自动生成去重离线告警 |

**关键类**：`DeviceController`、`DeviceTypeController`、`PointDefinitionController`、`DeviceTypePointTemplateController`、`DevicePointBindingController`；服务层 `DeviceService`、`DeviceTypeService`、`PointDefinitionService`、`DeviceTypePointTemplateService`、`DevicePointBindingService`。

**数据表**：`devices`、`device_types`、`point_definition`、`device_type_point_template`、`device_point_binding`。

### 4.5 指标数据模块

**功能**：指标字典、时序采样、最新值缓存、保留策略，以及 v1.0 遗留设备数据通道。

| 能力 | 说明 |
|------|------|
| 指标字典 | `/metric` 5 个接口：分页/CRUD/缓存刷新；元数据存 Redis（统一走 `CacheService`），告警消息自动填充中文名与单位 |
| 指标采样 | `/metric-samples` 3 个接口：单条/批量写入（触发告警检查）、分页查询；时序数据落 `metric_sample` |
| 最新值 | `/metric-latest` 2 个接口：按设备查询/分页；每个设备-点位仅保留最新值（`metric_latest`） |
| 保留策略 | `/metric-retention-policies` 5 个接口：全局/按点位/生效值查询与 upsert |
| v1.0 数据通道 | `/device-data` 9 个接口：单条/批量上报（`@Retryable` 死锁重试）、历史/区间/统计查询、导出、清理；`DataCleanupTask` 每日 2 点按 `task.device-data-ttl-days`（默认 90 天）清理 |

**关键类**：`MetricSampleService`、`MetricLatestService`、`MetricRetentionPolicyService`、`DictMetricService`、`DeviceDataService`、`DataCleanupTask`。

**数据表**：`metric_sample`、`metric_latest`、`metric_retention_policy`、`dict_metric`、`device_data`。

**测试**：`MetricSampleServiceImplTest`、`MetricLatestServiceImplTest`、`MetricRetentionPolicyServiceImplTest`、`DeviceDataServiceImplTest` 等。

---

### 4.6 告警模块

**功能**：配置化规则引擎、v1.0 告警兼容通道、第四阶段告警事件状态机与处理留痕、告警统计。

| 能力 | 说明 |
|------|------|
| 规则引擎 | `AlertRuleEngine` 评估上报数据：设备级 `alert_rule_config` 优先，缺失时降级类型级配置（`getEffectiveRuleConfig`）；五级告警（1-紧急 … 5-提示，数字越小越紧急）；`rule.level < existing.level` 时升级 |
| 告警事件状态机 | `alarm_event` 权威状态：TRIGGERED → ACKED（确认）→ HANDLING（处理中）→ RECOVERED（恢复）→ CLOSED（关闭）；非法流转抛 4081/4082 |
| 处理留痕 | `alarm_handling_record`（处理记录）与 `alarm_state_transition`（流转记录）全程可审计回溯 |
| v1.0 告警兼容 | `/alerts` 8 个接口：分页/详情/未处理/批量处理/导出/按设备与时间段统计/设备排名；`alerts` 表保留 |
| 告警统计 | `/dashboard/alarm-stats` 告警看板统计；`/dashboard/stats` 聚合统计（Redis 缓存 30s + 互斥锁防击穿） |
| 接收人解析 | `AlarmRecipientResolver` 按数据范围解析告警通知接收人 |

**关键类**：`AlarmEventController`、`AlertController`、`DashboardAlarmStatsController`、`AlertRuleEngine`、`AlarmEventServiceImpl`、`AlertServiceImpl`、`AlertConfigServiceImpl`（规则缓存走 Redis）。

**数据表**：`alarm_event`、`alarm_handling_record`、`alarm_state_transition`、`alerts`（v1.0 兼容）。

**测试**：`AlarmEventServiceImplTest`、`AlertServiceImplTest`、`AlertConfigServiceTest`、`AlertScopedServiceImplTest` 等。

### 4.7 通知模块

**功能**：邮件、WebSocket 用户隔离摘要、站内通知、通知日志与去重的多通道通知体系。

| 能力 | 说明 |
|------|------|
| 邮件通知 | `EmailNotificationServiceImpl`（Spring Mail，`notify.mail` 默认关闭，生产按需开启） |
| WebSocket 推送 | `AlertWebSocketService` + `UserScopedWebSocketPublisher` 向 `/user/queue/summary` 推送最小事件摘要（`WebSocketSummary`），客户端收到后 REST 回查详情，不广播敏感数据 |
| 站内通知 | `/in-app-notifications` 4 个接口：分页/单条已读/全部已读/未读数；前端铃铛徽标实时联动 |
| 通知日志 | `notification_log` 记录邮件/WebSocket 双通道发送结果，支持状态追溯 |
| 通知去重 | `NotificationDedupService`：AlertId 粒度 + 事件驱动删除 + 48h TTL |

**关键类**：`InAppNotificationController`、`InAppNotificationServiceImpl`、`EmailNotificationServiceImpl`、`AlertWebSocketService`、`NotificationDedupServiceImpl`、`security/UserScopedWebSocketPublisher`。

**数据表**：`in_app_notification`、`notification_log`。

**测试**：`InAppNotificationServiceImplTest`、`NotificationDedupServiceTest`、`UserScopedWebSocketPublisherTest`、`WebSocketAuthenticationInterceptorTest`。

---

### 4.8 仿真模块

**功能**：模拟场景与步骤编排、异步运行与取消、生成数据引用追溯，用于演示与压测数据构造。

| 能力 | 说明 |
|------|------|
| 场景管理 | `/simulation/scenes` 9 个接口：场景 CRUD/状态切换、场景步骤（pointId、目标值、持续时间等）增删查 |
| 运行管理 | `/simulation/runs` 5 个接口：启动/取消（异步执行，可中途取消）、运行分页/详情/运行事件 |
| 异步执行 | `SimulationRunExecutionService` + `SimulationStepExecutionService` 按步骤逐步生成指标采样，`@Async` 线程池携带 MDC traceId |
| 数据追溯 | `simulation_generated_data_ref` 记录每次运行生成的数据引用，可回溯删除 |
| 写入开关 | `simulation.write-enabled` 默认 `false`，生产环境默认关闭模拟写入 |

**关键类**：`SimulationSceneController`、`SimulationRunController`、`SimulationSceneServiceImpl`、`SimulationRunServiceImpl`、`SimulationRunExecutionServiceImpl`、`SimulationStepExecutionServiceImpl`。

**数据表**：`simulation_scene`、`simulation_scene_step`、`simulation_run_record`、`simulation_generated_data_ref`。

**测试**：`SimulationSceneServiceImplTest`、`SimulationRunServiceImplTest`、`SimulationRunExecutionServiceImplTest`、`SimulationStepExecutionServiceImplTest`、`SimulationModelRepairMigrationContractTest`。

### 4.9 审计与可观测模块

**功能**：注解式结构化审计、TraceId 全链路、Druid 慢 SQL 监控、Actuator 健康检查、生产安全门控。

| 能力 | 说明 |
|------|------|
| 注解审计 | `@AuditLog(operation, detail)` 切面自动落审计日志，`detail` 支持 SpEL 模板（如 `#dto.scopeType`）；19 个 Controller 关键写操作使用 |
| 结构化字段 | V5 迁移扩展 `sys_audit_log`：actorUserId/actorUsername/role/organizationId/cloudRoomId/action/targetType/targetId/result/errorCode/ip/traceId/createdAt |
| 审计查询 | `/audit-logs/page` 多条件分页（ADMIN/OPERATOR）；`/audit-logs/cleanup` 按时间清理 |
| TraceId | `TraceIdFilter` 生成 MDC traceId，`AsyncConfig` 使异步线程继承，日志与审计贯穿 |
| 运行时监控 | Druid 监控 `/druid/index.html`（dev 默认 admin/admin，含慢 SQL 统计）、Actuator `/actuator/health`、SpringDoc `/swagger-ui.html` |
| 生产安全门控 | `ProductionSafetyGuard` 启动期阻断：默认 JWT 密钥、通配 CORS Origin、危险功能开关，违例抛 `ProductionSafetyException` |

**关键类**：`common/annotation/AuditLog`、`common/aspect/AuditLogAspect`、`SysAuditLogController`、`SysAuditLogServiceImpl`、`config/ProductionSafetyGuard`。

### 4.10 公共基础设施模块

**功能**：统一响应、异常体系、缓存、限流、XSS 防御、分页校验等横切能力。

| 能力 | 说明 |
|------|------|
| 统一响应 | `Result<T>` + `ResultCode`：50 个 int 错误码（按域分段：安全 4011-4015、设备 4021-4023、数据范围 4071-4072、状态机 4081-4082、成员 4511-4518 等）+ 14 个稳定字符串码双轨映射 |
| 异常体系 | `BusinessException` 基类 + 11 个业务异常子类，`GlobalExceptionHandler` 20 个分支统一兜底（禁止硬编码 HTTP 状态码） |
| 统一缓存 | `CacheService` 接口封装 Redis（穿透/击穿/雪崩防御），禁止直接使用 `RedisTemplate` |
| 限流 | `RateLimitFilter` Redis 滑动窗口，默认 60s/1000 次，dev 放宽至 30000，可 `RATE_LIMIT_ENABLED=false` 关闭 |
| XSS 防御 | `XssFilter` + `XssRequestWrapper` 对请求参数 HTML 转义 |
| 分页校验 | `PaginationUtils.validate()`：`size ≤ 100, current ≥ 1` |
| 登录失败计数 | `LoginFailScript` Lua 脚本保证"读取-递增-锁定判定"原子性 |

---

## 五、环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 项目基于 Java 17 编译 |
| Maven | 3.8+ | 或使用项目自带的 Maven Wrapper |
| MySQL | 9.6 | 数据库服务，驱动 mysql-connector-j 9.6.0；库名默认 `iot_device_db` |
| Redis | 7.0+ | 缓存数据库（指标字典、Token 黑名单、限流计数器、通知去重等） |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端 Vite 8 开发与构建要求 |
| npm | 随 Node.js 安装 | 安装前端依赖与运行脚本 |
| Docker / Docker Compose | 可选 | 一键启动 app + MySQL + Redis（方式一） |
| SMTP 邮箱 | 可选 | 验证码注册与告警邮件（如 QQ 邮箱授权码） |
| 浏览器 | Chrome / Edge / Firefox 等现代浏览器 | 推荐安装 Vue.js devtools 进行前端调试 |

---

## 六、快速开始

> 表结构由 Flyway V1–V10 自动创建与迁移（`validate-on-migrate` 强制校验），**无需手动建表**；只需先创建空数据库。

### 6.1 配置环境变量

复制 `.env.example` 为 `.env`（已被 .gitignore 排除），按需修改：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `dev` | 激活的 profile（生产用 `prod`） |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `localhost:3306/iot_device_db` / root / 空 | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | localhost / 6379 / 空 | Redis 连接 |
| `JWT_SECRET` | 示例值 | **生产必须替换**为 ≥32 字符随机串（否则 ProductionSafetyGuard 阻断启动） |
| `JWT_EXPIRATION` | `7200` | Token 有效期（秒） |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_AUTH_CODE` | QQ SMTP 示例 | 验证码与告警邮件 |
| `VERIFICATION_CODE_HMAC_SECRET` | 示例值 | 验证码 HMAC 签名密钥 |
| `PLATFORM_BOOTSTRAP_TOKEN` | 示例值 | 平台初始化向导一次性令牌 |
| `DRUID_USERNAME` / `DRUID_PASSWORD` | admin / admin | Druid 监控台账号（仅 dev） |
| `CORS_ALLOWED_ORIGINS` | - | 生产环境强制要求的前端来源 |

### 6.2 方式一：Docker 一键启动

```bash
docker compose up -d --build
```

编排内容：`app`（Spring Boot，8080，依赖 MySQL/Redis 健康检查）+ `mysql:9.6.0`（数据卷 `mysql_data`）+ `redis:7-alpine`，应用读取 `.env` 并以 `prod` profile 启动。

### 6.3 方式二：本地启动

```sql
-- 1. 创建空数据库（表由 Flyway 自动创建）
CREATE DATABASE iot_device_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
# 2. 启动后端（需 MySQL + Redis 运行中）
./mvnw spring-boot:run          # Windows 使用 mvnw.cmd

# 3. 启动前端
cd frontend && npm install && npm run dev
```

### 6.4 平台初始化与首次登录

1. 访问 `http://localhost:5173/setup`，使用 `.env` 中的 `PLATFORM_BOOTSTRAP_TOKEN`（及邮箱验证码）创建平台管理员；
2. 平台管理员登录后创建组织与机房，或在"注册审核"中处理用户申请；
3. 普通用户经 `/register` 邮箱验证码注册后，通过"创建组织申请/加入组织申请"进入组织；
4. 未加入任何组织的用户登录后被引导至"我的组织"页面。

### 6.5 验证启动

| 验证地址 | 说明 |
|---------|------|
| `http://localhost:8080/api/actuator/health` | 健康检查（公开） |
| `http://localhost:8080/api/swagger-ui.html` | Swagger 交互式 API 文档 |
| `POST http://localhost:8080/api/auth/login` | 登录获取 JWT Token |
| `http://localhost:8080/api/druid/index.html` | Druid 监控（dev） |
| `http://localhost:5173/dashboard` | 前端仪表盘 |

> **认证方式**：登录后获取 JWT Token，后续请求在 Header 中添加 `Authorization: Bearer <token>`；跨组织操作还需携带 `X-Organization-Id: <组织ID>`。

---

## 七、前端模块详解

前端位于 `frontend/`，Vue 3（组合式 API）+ Vite + Element Plus + Pinia 单页应用。开发时先启动后端 `http://localhost:8080`，再 `cd frontend && npm install && npm run dev`，访问 `http://localhost:5173`；`vite.config.js` 已将 `/api/**` 代理转发到后端。

### 7.1 页面与路由模块

**公开页面**（`meta.public: true`，未登录可访问）：`/login` 登录、`/register` 注册（邮箱验证码）、`/setup` 平台初始化向导。

**业务页面**（挂载于 `layout/index.vue` 主布局，需登录）：

| 路由 | 页面 | 说明 | 可见角色 |
|------|------|------|---------|
| `/account/organizations` | 我的组织 | 成员关系列表、创建/加入组织申请入口 | 登录即可 |
| `/dashboard` | 仪表盘 | 统计卡、近七日趋势、状态/级别分布、设备告警排名，实时摘要刷新 | ORG_ADMIN/OPERATOR/VIEWER |
| `/organizations` | 组织管理 | 组织 CRUD 与状态管理 | PLATFORM_ADMIN/ORG_ADMIN |
| `/cloud-rooms` | 机房管理 | 机房 CRUD | ORG_ADMIN/OPERATOR/VIEWER |
| `/devices` | 设备管理 | 设备 CRUD、筛选、导入导出、点位绑定抽屉 | ORG_ADMIN/OPERATOR/VIEWER |
| `/data-monitor` | 数据监控 | 设备侧栏 + 实时/历史数据 + 趋势图 | ORG_ADMIN/OPERATOR/VIEWER |
| `/simulation` | 模拟运行 | 场景/步骤编排、运行启动与事件查看 | ORG_ADMIN/OPERATOR/VIEWER |
| `/alerts` | 告警管理 | 告警事件筛选、状态机操作、详情抽屉（流转/处理记录） | ORG_ADMIN/OPERATOR/VIEWER |
| `/notifications` | 站内通知 | 未读标记、全部已读、实时摘要刷新 | 全部角色 |
| `/metric` | 指标字典 | 指标 CRUD、点位定义、保留策略对话框 | 全部角色 |
| `/device-types` | 设备类型 | 类型 CRUD、告警配置、点位模板编辑 | 全部角色 |
| `/users` | 用户管理 | 用户 CRUD、数据范围分配对话框 | PLATFORM_ADMIN |
| `/platform/registration` | 注册审核 | 注册策略、组织申请审批 | PLATFORM_ADMIN |
| `/organization/members` | 组织成员 | 成员角色、加入申请审批、开放加入开关 | ORG_ADMIN |
| `/audit-logs` | 审计日志 | 多条件查询与清理 | PLATFORM_ADMIN/ORG_ADMIN/OPERATOR |

**导航守卫**（`router/index.js` 全局 `beforeEach`）：

1. 从 localStorage 同步 Token（`authStore.syncFromStorage()`）；
2. 已登录访问公开页 → 加载组织成员关系后跳转首个可访问路由；
3. 未登录访问受保护页 → 跳 `/login`（携带 `redirect` 回跳参数）；
4. 已登录且组织未加载 → `organizationStore.loadMemberships()`；
5. **组织引导**：非平台管理员且无任何组织成员关系 → 强制跳"我的组织"页；
6. 路由 `meta.roles` 与当前组织角色/平台角色不匹配 → 跳首个可访问路由（兜底顺序：平台管理员 → 注册审核页；组织角色 → 仪表盘；无组织 → 我的组织）。

> 前端角色控制只用于导航与操作按钮显隐，最终权限以后端 RBAC 与数据范围为准。

### 7.2 状态管理模块

`src/stores/` 下 4 个 Pinia Setup Store：

| Store | 职责 |
|-------|------|
| `auth.js` | Token 持久化（localStorage `token`）、`jwt-decode` 解析 `platformRole`/`username`；`normalizedRole` 将旧 `ADMIN` 别名映射为 `ORG_ADMIN`；`hasAnyRole`/`logout` |
| `organization.js` | 组织成员关系加载与持久化（`currentOrganizationId`）、当前组织角色 `currentRole`、单组织自动选中、越权切换拦截 |
| `scope.js` | 调用 `/auth/current-scope` 获取数据范围（scopeType/organizationIds/cloudRoomIds），`hasWritePermission`（非 READ_ONLY）；Token 切换防抖 + 请求版本号防竞态 |
| `theme.js` | 日间/夜间主题状态：`init()` 优先 localStorage（`app-theme`）否则跟随 `prefers-color-scheme`，切换时同步 `<html data-theme>` 属性 |

### 7.3 API 服务层模块

`src/utils/request.js` 统一 Axios 实例，`src/api/` 下 26 个业务模块：

| 约定 | 说明 |
|------|------|
| `baseURL` | `/api`（Vite 代理转发到 8080） |
| 超时 / Cookie | `timeout: 15000`；`withCredentials: true` |
| 请求头 | 拦截器自动附加 `Authorization: Bearer <token>` 与 `X-Organization-Id: <当前组织>` |
| 响应处理 | HTTP 200 返回 `response.data`（保留 `Result<T>` 结构）；401 清 Token 跳登录；403/其他错误 ElMessage 提示（`skipErrorMessage` 可静默） |
| 文件下载 | 设备导出/模板、告警导出使用原生 `axios` + `responseType: 'blob'` 手动携带 Token |

| 模块 | 典型能力 |
|------|---------|
| `auth.js` / `setup.js` | 登录、注册验证码、注册；平台初始化状态/验证码/完成 |
| `accountOrganization.js` / `organization.js` / `organizationMembership.js` | 我的组织与申请；组织 CRUD；成员与加入审批 |
| `scope.js` | 当前数据范围 |
| `device.js` / `deviceType.js` / `devicePoint.js` / `pointDefinition.js` / `pointTemplate.js` | 设备、类型、点位绑定、点位定义、类型点位模板 |
| `metric.js` / `metricSample.js` / `metricLatest.js` / `metricRetention.js` / `deviceData.js` | 指标字典、采样写入、最新值、保留策略、v1.0 历史数据 |
| `alert.js` / `alarmEvent.js` | v1.0 告警统计/导出；告警事件状态机操作与记录 |
| `notification.js` | 站内通知分页/已读/未读数 |
| `cloudRoom.js` / `simulation.js` | 机房 CRUD；场景/步骤/运行 |
| `user.js` / `platformRegistration.js` | 用户 CRUD/数据范围；注册策略与组织申请审批 |
| `dashboard.js` / `auditLog.js` | 仪表盘统计；审计查询/清理 |

### 7.4 实时通信模块

`src/api/realtime.js` 基于 `@stomp/stompjs` + `sockjs-client` 建立 STOMP 实时通道：

| 要素 | 说明 |
|------|------|
| 端点 | `new SockJS('/api/ws')`（Vite 代理转发到后端 STOMP 端点） |
| 认证 | `beforeConnect` 从 localStorage 取 Token，以 `Authorization: Bearer` 连接头传递；无 Token 不连接（后端在 STOMP CONNECT 阶段完成认证） |
| 订阅 | 仅订阅用户隔离队列 `/user/queue/summary`，校验 `resourceType/resourceId/eventType` 后分发；**不订阅业务数据 topic，业务数据一律 REST 拉取** |
| 参数 | 重连间隔 5000ms，心跳收发各 10000ms |
| 消费方 | `layout`（未读徽标）、`dashboard`、`notification`、`alerts` 收到摘要后各自 REST 刷新 |

### 7.5 主题系统模块

日间/夜间双主题，由 `<html data-theme="light|dark">` 属性驱动，无 Element Plus 官方 dark CSS 依赖：

| 组成 | 说明 |
|------|------|
| `src/styles/theme.css` | 产品语义 Token（`--color-*`/`--radius-*`/`--shadow-*`/`--chart-*`/`--data-font`）+ Element Plus 变量整表覆盖 + 暗色组件手工适配；light 为 SaaS 浅色（主色 #2563eb），dark 为工业 SCADA 深色（背景 #0b1120，霓虹青主色 #00d4ff） |
| `src/stores/theme.js` | 主题状态与 localStorage 持久化，`main.js` 挂载前 `init()` 防止首屏闪烁 |
| `layout/index.vue` | 顶栏日/夜切换按钮（Moon/Sunny 图标） |
| 图表适配 | `AlertTrendChart`/`DataTrendChart` 按 `isDark` 计算 tooltip/坐标轴/线条颜色并监听主题切换重绘 |
| 迁移工具 | `scripts/migrate-theme.mjs` 一次性 codemod，将历史硬编码色值批量替换为 `var(--color-*)`；`public/ui-mockups/index.html` 为三套主题设计稿预览 |
| 枚举色值 | `src/utils/constants.js` 中设备状态/告警级别/角色等枚举颜色全部使用主题变量 |

### 7.6 构建与开发工具链

```bash
cd frontend

npm run dev        # 开发服务器（端口 5173）
npm run build      # 生产构建（含 legacy 浏览器兼容）
npm run preview    # 预览构建产物
npm run lint       # oxlint + eslint 检查并自动修复
npm run format     # prettier 格式化 src/
```

Node 版本要求 `^20.19.0 || >=22.12.0`；浏览器兼容目标见 `package.json` browserslist。推荐 VS Code + Vue Official 插件与 Vue.js devtools 调试。

---

## 八、API 概览

> **首选方式**：启动后访问 `/swagger-ui.html`（SpringDoc 从注解实时生成，支持 JWT 调试）。以下为 30 个控制器清单，各模块端点细节见"四、后端模块详解"。

### 8.1 认证与身份

| 控制器 | 基路径 | 职责 | 端点 |
|--------|--------|------|------|
| `AuthController` | `/auth` | 登录、登出、注册、注册验证码 | 4 |
| `PlatformSetupController` | `/setup` | 平台初始化向导（状态/验证码/完成） | 3 |
| `AccountOrganizationController` | `/auth/memberships` | 我的组织成员关系 | 1 |
| `CurrentScopeController` | `/auth/current-scope` | 当前用户数据范围 | 1 |

### 8.2 组织与平台管理

| 控制器 | 基路径 | 职责 | 端点 |
|--------|--------|------|------|
| `OrganizationController` | `/organizations` | 组织 CRUD 与状态 | 6 |
| `OrganizationMemberController` | `/organizations/{orgId}` | 成员列表/改角色/移除/加入设置 | 4 |
| `OrganizationCreationApplicationController` | `/organization-applications`、`/platform/organization-applications` | 创建组织申请提交/查询/审批 | 5 |
| `OrganizationJoinApplicationController` | `/organizations/{orgId}/join-applications` | 加入组织申请 | 4 |
| `PlatformRegistrationSettingsController` | `/platform/registration-settings` | 注册策略读写 | 2 |
| `SysUserController` | `/users` | 用户管理/重置密码/状态 | 8 |
| `UserDataScopeController` | `/users/{id}/data-scopes` | 数据范围指派/移除/列表 | 3 |

### 8.3 资源与采集

| 控制器 | 基路径 | 职责 | 端点 |
|--------|--------|------|------|
| `CloudRoomController` | `/cloud-rooms` | 机房 CRUD 与按组织查询 | 5 |
| `DeviceController` | `/devices` | 设备 CRUD/分页/导入导出/状态/告警配置 | 13 |
| `DeviceTypeController` | `/device-types` | 设备类型 CRUD/告警配置 | 6 |
| `PointDefinitionController` | `/point-definitions` | 点位定义 CRUD | 4 |
| `DeviceTypePointTemplateController` | `/point-templates/device-types/{typeId}` | 类型点位模板查看/批量保存 | 2 |
| `DevicePointBindingController` | `/device-points` | 设备点位绑定管理 | 6 |
| `DeviceDataController` | `/device-data` | v1.0 数据上报/查询/导出/清理 | 9 |
| `DictMetricController` | `/metric` | 指标字典 CRUD/缓存刷新 | 5 |
| `MetricSampleController` | `/metric-samples` | 指标采样写入/查询 | 3 |
| `MetricLatestController` | `/metric-latest` | 最新指标查询 | 2 |
| `MetricRetentionPolicyController` | `/metric-retention-policies` | 保留策略 CRUD/生效值 | 5 |

### 8.4 告警、通知与统计

| 控制器 | 基路径 | 职责 | 端点 |
|--------|--------|------|------|
| `AlertController` | `/alerts` | v1.0 告警查询/处理/导出/统计 | 8 |
| `AlarmEventController` | `/alarm-events` | 告警事件分页/详情/ack/处理/恢复/关闭/记录 | 8 |
| `InAppNotificationController` | `/in-app-notifications` | 站内通知分页/已读/未读数 | 4 |
| `DashboardController` | `/dashboard` | 仪表盘聚合统计 | 1 |
| `DashboardAlarmStatsController` | `/dashboard/alarm-stats` | 告警看板统计 | 1 |
| `SysAuditLogController` | `/audit-logs` | 审计分页/清理 | 2 |

### 8.5 仿真与实时

| 控制器/端点 | 基路径 | 职责 | 端点 |
|------------|--------|------|------|
| `SimulationSceneController` | `/simulation/scenes` | 场景与步骤管理 | 9 |
| `SimulationRunController` | `/simulation/runs` | 运行启动/取消/查询/事件 | 5 |
| WebSocket（STOMP + SockJS） | `/ws` | CONNECT 认证后订阅 `/user/queue/summary` | - |

---

## 九、RBAC 权限模型

| 角色 | 权限范围 |
|------|----------|
| **ADMIN** | 全部操作 + 用户管理 + 删除操作 + Druid 监控 + 审计日志清理 |
| **OPERATOR** | 设备管理 + 告警处理 + 数据上报 + 审计日志查询 + 只读查询 |
| **VIEWER** | 只读查询（设备/数据/告警/日志） |

### 端点访问规则

| 规则 | 路径/方法 | 允许角色 |
|------|-----------|----------|
| 公开端点 | `/auth/login` | 无需认证 |
| Druid 监控 | `/druid/**` | ADMIN |
| DELETE 操作 | 所有 DELETE 请求 | ADMIN |
| 用户管理 | `/users/**` | ADMIN |
| 审计日志 | `/audit-logs/**` | ADMIN, OPERATOR |
| 设备写入/数据上报/指标创建 | POST 相关端点 | ADMIN, OPERATOR |
| 设备更新/类型更新/指标更新 | PUT 相关端点 | ADMIN, OPERATOR |
| 只读查询 | 所有 GET 请求 | 已认证用户（含 VIEWER） |

---

## 十、版本历史

| 版本 | 日期 | Commit | 变更内容 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | - | 初始版本，项目骨架搭建 |
| v1.1 | 2026-05-08 | `f88934db` | 修复已知问题：补全 @Service 注解、启用分页插件、实现 Controller 接口、自动填充 |
| v1.2 | 2026-05-09 | `0e2c8e48` | 新增 DeviceNotFoundException(Integer)、CorsConfig、分页/删除接口、getDeviceOrThrow |
| v1.3 | 2026-05-15 | `05b8ff81` | 新增 DTO 层、告警规则引擎、指标字典缓存、DeviceData.metric 字段、alertRuleConfig JSON 字段 |
| v1.4 | 2026-05-16 | `240739a` | AlertServiceImpl 告警统计方法、AlertMapper 自定义 SQL、DeviceMonitorTask 定时任务、@EnableScheduling |
| v2.0 | 2026-05-17 | - | 代码审查修复：DictMetricController统一响应、URL前缀去重、@Validated分组校验、ConstraintViolationException处理器、乐观锁插件、CORS配置外部化、告警升级方向修正、NPE防御、事务注解、Controller逻辑下沉Service |
| v3.0 | 2026-05-24 | - | 安全与认证模块：Spring Security + JWT 无状态认证、RBAC 三级角色授权（ADMIN/OPERATOR/VIEWER）、SecurityConfig 安全过滤链、JwtAuthenticationFilter/CustomAuthenticationEntryPoint/CustomAccessDeniedHandler、BCrypt 密码加密；用户管理：SysUser 实体（含账号锁定/停用机制）、SysUserController 7个接口、SysUserServiceImpl（SQL原子递增/更新字段白名单防注入）；认证服务：AuthController 登录接口、AuthServiceImpl（IP频率限制/账号锁定检查/JWT签发/审计日志）；审计日志：SysAuditLog 实体、SysAuditLogController 2个接口、SysAuditLogServiceImpl（多条件查询/过期清理）；新增异常类：UserNotFoundException/UsernameAlreadyExistsException/AccountLockedException/DeviceTypeCodeAlreadyExistsException；新增 DTO：LoginDTO/LoginResponseDTO/PasswordResetDTO；新增工具类：JwtUtil |
| v4.0 | 2026-05-28 | `3d96056` | 缓存体系与安全防护模块：CacheService 统一缓存接口（穿透/击穿/雪崩防御）、RedisConfig 序列化与连接池配置、CacheOperationException 缓存异常体系；告警通知去重模块：NotificationDedupService 基于 AlertId 粒度 + 事件驱动删除 + 48h TTL 方案；仪表盘统计模块：DashboardStatsService 互斥锁防缓存击穿；Token 黑名单：TokenBlacklistService 登出与密码重置场景；告警配置缓存：AlertConfigService 设备/类型级规则缓存迁移至 Redis；指标字典缓存迁移：DictMetricServiceImpl 从本地内存迁移至 Redis 分布式缓存；安全增强：AuthServiceImpl 重构、JwtAuthenticationFilter 集成 Token 黑名单校验、JwtUtil 增强 |
| v4.1 | 2026-06-20 | `caaff15` | P2 功能增强：设备 Excel 批量导入与导出（EasyExcel 4.0.3）、数据上报死锁重试（@Retryable + @Recover）、Druid 连接池监控与慢SQL统计、SQL 文档体系重构（存储过程造数据/查询示例）、前端导入导出按钮与交互（文件上传/模板下载）、限流配置环境变量化与开发环境调优 |
| v4.2 | 2026-06-21 | — | 模块补完：DictMetric 补全 Jakarta Validation 校验注解与 createTime/updateTime 自动填充、Flyway V5 修正 dict_metric 列命名+alerts 告警级别注释、AuthController 新增用户登出接口 `POST /api/auth/logout`、Flyway V6 新增 notification_log 通知发送日志表、EmailNotificationServiceImpl / AlertWebSocketService 写通知日志、需求文档与 README 全面修正（账号锁定 5次、Redis 环境依赖、deleted 字段、status 描述统一） |
| v4.3 | 2026-06-23 | — | README 合并前端说明：补充 Vue 3 + Vite 管理后台、前端目录结构、启动/构建命令、路由、Axios 联调约定与开发工具建议 |
| v4.4 | 2026-06-23 | — | 前端 README 审查修正：补充 JWT 角色解析、菜单显隐、请求超时/Cookie、文件下载 blob 例外，以及各前端 API 模块能力映射 |

---

## 十一、文档导航

| 文档 | 用途 | 何时读 |
|------|------|--------|
| `AGENTS.md` | AI Agent 上下文（技术栈/约定/禁忌/构建命令） | 新 AI 会话第一步（与本 README 一起） |
| `frontend/README.md` | 前端目录入口说明 | 指向根 README 的“前端管理后台”章节，避免重复维护 |
| 外部公共控制包 `01-全员必读.md`、任务分发清单、当前 Agent 任务卡 | 第四阶段产品化事实来源 | 第四阶段任意任务 |
| 第三阶段最终设计和追踪矩阵 | 产品化需求、架构约束、P0 门控来源 | 新增/改需求或确认边界 |
| 对应源码、测试和 Swagger 注解 | 现有模块真实行为 | 学习模块原理或改业务代码 |
| `/swagger-ui.html`（启动后访问） | 交互式 API 文档，从代码注解实时生成 | 查接口/联调 |
| `git log` | 变更历史 | 排查问题 |

> **提示**：原 `src` 各包下的 README 文档和 `开发流程学习文档/` 均已退出第四阶段事实来源；新会话请勿再读取或创建这些旧文档入口。

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-06-23。*
