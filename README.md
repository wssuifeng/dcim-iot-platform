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

## 五、环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 项目基于 Java 17 编译 |
| Maven | 3.8+ | 或使用项目自带的 Maven Wrapper |
| MySQL | 9.6 | 数据库服务，驱动 mysql-connector-j 9.6.0 |
| Redis | 7.0+ | 缓存数据库（指标字典、Token 黑名单、限流计数器等） |
| Node.js | `^20.19.0` 或 `>=22.12.0` | 前端 Vite 8 开发与构建要求 |
| npm | 随 Node.js 安装 | 安装前端依赖与运行脚本 |
| 浏览器 | Chrome / Edge / Firefox 等现代浏览器 | 推荐安装 Vue.js devtools 进行前端调试 |

---

## 六、快速开始

### 6.1 创建数据库

```sql
CREATE DATABASE iot_device_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 6.2 建表

```sql
USE iot_device_db;

CREATE TABLE device_types (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    type_code VARCHAR(50) NOT NULL COMMENT '类型编码',
    description VARCHAR(200) COMMENT '类型描述',
    alert_rule_config JSON COMMENT '告警规则配置（JSON）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '设备类型表';

CREATE TABLE devices (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_code VARCHAR(50) NOT NULL UNIQUE COMMENT '设备编码',
    type_id INT COMMENT '设备类型ID',
    status INT DEFAULT 0 COMMENT '状态：0-离线 1-在线 2-故障',
    location VARCHAR(200) COMMENT '设备位置',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    last_online_time DATETIME COMMENT '最后在线时间',
    alert_rule_config JSON COMMENT '设备级告警规则配置（JSON）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'
) COMMENT '设备表';

CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id INT NOT NULL COMMENT '设备ID',
    metric VARCHAR(50) COMMENT '指标标识（如 temperature）',
    data_value DECIMAL(10,2) COMMENT '数据值',
    collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间'
) COMMENT '设备数据表';

CREATE TABLE alerts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    device_id INT NOT NULL COMMENT '设备ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型',
    alert_level TINYINT DEFAULT 1 COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示',
    alert_message VARCHAR(500) COMMENT '告警描述',
    is_handled INT DEFAULT 0 COMMENT '是否处理：0-未处理 1-已处理',
    handle_time DATETIME COMMENT '处理时间',
    handle_user VARCHAR(50) COMMENT '处理人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警产生时间'
) COMMENT '告警表';

CREATE TABLE dict_metric (
    id INT AUTO_INCREMENT PRIMARY KEY,
    metric_key VARCHAR(50) COMMENT '指标标识',
    metric_name VARCHAR(50) COMMENT '指标中文名',
    metric_unit VARCHAR(20) COMMENT '数据单位',
    description VARCHAR(200) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'
) COMMENT '指标字典表';

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（登录账号）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密存储）',
    nickname VARCHAR(50) COMMENT '昵称',
    role VARCHAR(20) NOT NULL COMMENT '角色：ADMIN/OPERATOR/VIEWER',
    status INT DEFAULT 0 COMMENT '状态：0-正常 1-停用',
    login_fail_count INT DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME COMMENT '账号锁定截止时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户表';

CREATE TABLE sys_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '操作人ID',
    username VARCHAR(50) COMMENT '操作人账号',
    operation VARCHAR(50) COMMENT '操作类型（如 LOGIN_SUCCESS/LOGIN_FAIL/CREATE_USER）',
    ip VARCHAR(50) COMMENT '操作IP地址',
    detail VARCHAR(500) COMMENT '操作详情',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'
) COMMENT '审计日志表';
```

### 6.3 插入测试数据

```sql
INSERT INTO device_types (type_name, type_code, description) VALUES
('温度传感器', 'TEMP_SENSOR', '环境温度监测'),
('湿度传感器', 'HUMIDITY_SENSOR', '环境湿度监测'),
('烟雾探测器', 'SMOKE_DETECTOR', '火灾烟雾检测');

INSERT INTO devices (device_name, device_code, type_id, status, location, ip_address) VALUES
('1号楼温度传感器', 'TEMP-001', 1, 1, '1号楼3层东侧', '192.168.1.101'),
('2号楼湿度传感器', 'HUM-001', 2, 1, '2号楼1层大厅', '192.168.1.102'),
('3号楼烟雾探测器', 'SMOKE-001', 3, 0, '3号楼2层西侧', '192.168.1.103');

INSERT INTO dict_metric (metric_key, metric_name, metric_unit, description) VALUES
('temperature', '温度', '℃', '环境温度'),
('humidity', '湿度', '%', '环境湿度'),
('smoke', '烟雾浓度', 'ppm', '烟雾检测');

INSERT INTO sys_user (username, password, nickname, role, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'ADMIN', 0);
```

> **注意**：上述 `admin` 用户的密码为 BCrypt 加密后的 `Admin123`，登录时使用 `admin` / `Admin123`。

### 6.4 修改配置

编辑 `src/main/resources/application.yaml`，修改数据库连接信息和安全配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iot_device_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root           # 修改为你的 MySQL 用户名
    password: your_password  # 修改为你的 MySQL 密码

# JWT 配置
jwt:
  secret: your-jwt-secret-key-at-least-256-bits-long  # 修改为你的 JWT 密钥
  expiration: 86400000                                  # Token 有效期（毫秒），默认24小时

# CORS 配置
cors:
  allowed-origins: http://localhost:*,http://127.0.0.1:*  # 生产环境请修改为实际域名
```

### 6.5 构建与运行

```bash
# 编译（跳过测试）
./mvnw clean compile -DskipTests

# 启动
./mvnw spring-boot:run

# Windows 用户使用 mvnw.cmd
```

### 6.6 验证启动

启动成功后控制台输出：

```
========================================
物联网平台启动成功！
访问地址：http://localhost:8080/api
Druid监控：http://localhost:8080/api/druid
========================================
```

| 验证地址 | 说明 |
|---------|------|
| `POST http://localhost:8080/api/auth/login` | 用户登录获取 JWT Token |
| `http://localhost:8080/api/devices` | 设备列表（需携带 Token） |
| `http://localhost:8080/api/devices/online` | 在线设备（需携带 Token） |
| `http://localhost:8080/api/druid/index.html` | Druid 监控（ADMIN 角色） |

> **认证方式**：登录后获取 JWT Token，后续请求在 Header 中添加 `Authorization: Bearer <token>`。

---

## 七、前端管理后台

### 7.1 安装与启动

前端位于 `frontend/`，是一个 Vue 3 + Vite 单页后台管理应用。开发时先启动后端 `http://localhost:8080`，再启动前端开发服务器。

```bash
cd frontend
npm install
npm run dev
```

默认访问地址：

| 地址 | 说明 |
|------|------|
| `http://localhost:5173` | Vite 前端开发服务器 |
| `http://localhost:5173/login` | 登录页 |
| `http://localhost:5173/dashboard` | 登录后的仪表盘 |

`frontend/vite.config.js` 已配置开发代理：前端请求 `/api/**` 时会转发到 `http://localhost:8080`，因此业务代码中统一使用 `/api` 作为后端前缀。

### 7.2 构建、检查与格式化

```bash
cd frontend

# 生产构建
npm run build

# 预览构建产物
npm run preview

# 代码检查并自动修复（oxlint + eslint）
npm run lint

# 格式化 src 目录
npm run format
```

### 7.3 页面路由

| 路由 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录页 | 独立全屏页面，未认证用户入口 |
| `/dashboard` | 仪表盘 | 设备状态、告警统计、趋势与近期告警 |
| `/devices` | 设备管理 | 设备 CRUD、筛选、导入导出、告警配置 |
| `/device-types` | 设备类型 | 类型 CRUD、类型级告警规则配置 |
| `/data-monitor` | 数据监控 | 设备数据列表、统计面板与趋势图 |
| `/alerts` | 告警管理 | 告警查询、统计、处理与批量处理 |
| `/metric` | 指标字典 | 指标元数据管理与缓存刷新 |
| `/users` | 用户管理 | ADMIN 用户管理、状态维护、密码重置 |
| `/audit-logs` | 审计日志 | 登录/操作审计查询与清理 |

`frontend/src/router/index.js` 使用全局前置守卫读取 `localStorage.token`：未登录访问后台页面会跳转 `/login`，已登录访问 `/login` 会跳转 `/dashboard`。

角色信息由 `frontend/src/stores/auth.js` 使用 `jwt-decode` 从 JWT 中解析，侧边栏菜单会根据角色做入口显隐：

| 菜单 | 前端显隐规则 | 说明 |
|------|-------------|------|
| 用户管理 | 仅 `ADMIN` 显示 | 对应后端用户管理权限 |
| 审计日志 | `ADMIN`、`OPERATOR` 显示，`VIEWER` 隐藏 | 对应后端审计日志查询权限 |

> 前端角色控制只用于导航与操作按钮显隐，最终权限仍以后端 RBAC 为准。

### 7.4 API 请求约定

前端普通 JSON API 统一通过 `frontend/src/utils/request.js` 创建的 Axios 实例访问后端；文件导入仍走该实例，文件下载/导出因需要 `responseType: 'blob'`，在 `frontend/src/api/device.js` 和 `frontend/src/api/alert.js` 中使用原生 `axios` 并手动携带 Token。

| 约定 | 说明 |
|------|------|
| `baseURL` | `/api`，配合 Vite 代理转发到后端 |
| 超时 | 普通请求 `timeout: 15000` |
| Cookie | `withCredentials: true`，配合后端 CORS 凭证配置 |
| Token | 请求拦截器自动读取 `localStorage.token` 并添加 `Authorization: Bearer <token>` |
| 响应处理 | HTTP 200 直接返回 `response.data` |
| 401 | 清除本地 Token 并跳转 `/login` |
| 403 | 使用 Element Plus 消息提示权限不足 |
| 其他错误 | 优先展示后端返回的 `message` |
| 文件下载 | 设备导出、设备导入模板、告警导出使用 `responseType: 'blob'`，前端创建临时链接下载 `.xlsx` |

当前前端封装的主要后端交互包括：

| 模块 | 前端 API 文件 | 典型能力 |
|------|--------------|----------|
| 认证 | `src/api/auth.js` | 登录 |
| 仪表盘 | `src/api/dashboard.js` | 聚合统计 |
| 设备 | `src/api/device.js` | 分页、详情、CRUD、状态更新、告警配置、导入、模板下载、导出 |
| 设备类型 | `src/api/deviceType.js` | 列表、详情、CRUD、类型级告警配置 |
| 数据监控 | `src/api/deviceData.js` | 历史数据、最新数据、时间范围、统计聚合 |
| 告警 | `src/api/alert.js` | 分页、时间统计、批量处理、导出 |
| 指标字典 | `src/api/metric.js` | 分页、CRUD、缓存刷新 |
| 用户 | `src/api/user.js` | 分页、详情、搜索、CRUD、密码重置、状态更新 |
| 审计日志 | `src/api/auditLog.js` | 分页、过期日志清理 |

### 7.5 推荐开发环境

| 工具 | 建议 |
|------|------|
| IDE | VS Code + Vue Official 插件；如安装过 Vetur，建议禁用 |
| 浏览器 | Chrome、Edge、Brave、Firefox 等现代浏览器 |
| 调试插件 | Vue.js devtools |
| 浏览器设置 | 可开启 Custom Object Formatter，方便调试 Vue 响应式对象 |
| 配置参考 | Vite 配置见 `frontend/vite.config.js`，官方参考见 https://vite.dev/config/ |

---

## 八、API 文档

> **首选方式**：启动应用后访问 `/swagger-ui.html` 即可查看完整的交互式 API 文档（含请求/响应示例、JWT 认证、字段说明）。以下为静态参考清单。

### 8.1 认证 `/auth`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/auth/login` | 用户登录，返回 JWT Token | 公开 |

### 8.2 设备管理 `/devices`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/devices` | 查询所有设备 | 已认证 |
| GET | `/devices/{id}` | 查询设备详情 | 已认证 |
| POST | `/devices` | 新增设备（CreateGroup 校验） | ADMIN, OPERATOR |
| PUT | `/devices/{id}` | 更新设备（UpdateGroup 校验） | ADMIN, OPERATOR |
| DELETE | `/devices/{id}` | 删除设备 | ADMIN |
| GET | `/devices/online` | 查询在线设备 | 已认证 |
| GET | `/devices/search?name=` | 按名称模糊搜索 | 已认证 |
| PUT | `/devices/{id}/status?status=` | 更新设备状态 | ADMIN, OPERATOR |
| GET | `/devices/page` | 分页查询（支持状态/名称筛选） | 已认证 |
| PUT | `/devices/{id}/alert-config` | 更新设备级告警规则 | ADMIN, OPERATOR |

### 8.3 数据采集 `/device-data`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/device-data` | 单条数据上报（触发告警检查） | ADMIN, OPERATOR |
| POST | `/device-data/batch` | 批量数据上报（逐条触发告警检查） | ADMIN, OPERATOR |
| GET | `/device-data/device/{deviceId}` | 设备历史数据分页 | 已认证 |
| GET | `/device-data/device/{deviceId}/latest` | 设备最新数据 | 已认证 |
| GET | `/device-data/device/{deviceId}/range` | 时间段查询 | 已认证 |
| GET | `/device-data/device/{deviceId}/statistics` | 统计聚合（min/max/avg/count） | 已认证 |
| GET | `/device-data/page` | 数据分页查询 | 已认证 |

### 8.4 设备类型 `/device-types`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| PUT | `/device-types/{id}/alert-config` | 更新类型级告警规则 | ADMIN, OPERATOR |

### 8.5 告警管理 `/alerts`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/alerts` | 分页查询告警（支持多条件筛选） | 已认证 |
| GET | `/alerts/{id}` | 查询告警详情 | 已认证 |
| GET | `/alerts/unhandled` | 查询所有未处理告警 | 已认证 |
| PUT | `/alerts/batch-handle` | 批量处理告警 | ADMIN, OPERATOR |
| GET | `/alerts/stats/device/{deviceId}` | 按设备统计告警 | 已认证 |
| GET | `/alerts/stats/time-range` | 按时间段统计告警 | 已认证 |
| GET | `/alerts/stats/device-ranking` | 设备告警排名 | 已认证 |

### 8.6 指标字典 `/metric`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/metric/page` | 分页查询指标 | 已认证 |
| POST | `/metric` | 新增指标 | ADMIN, OPERATOR |
| PUT | `/metric` | 更新指标 | ADMIN, OPERATOR |
| DELETE | `/metric/{id}` | 删除指标 | ADMIN |
| POST | `/metric/cache/refresh` | 手动刷新缓存 | 已认证 |

### 8.7 用户管理 `/users`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/users/page` | 分页查询用户（支持关键字搜索） | ADMIN |
| GET | `/users/search?username=` | 按用户名查找 | ADMIN |
| GET | `/users/{id}` | 查询用户详情 | ADMIN |
| POST | `/users` | 创建用户 | ADMIN |
| PUT | `/users/{id}` | 更新用户信息 | ADMIN |
| PUT | `/users/{id}/password` | 重置密码 | ADMIN |
| PUT | `/users/{id}/status?status=` | 启用/停用账号 | ADMIN |
| DELETE | `/users/{id}` | 删除用户 | ADMIN |

### 8.8 审计日志 `/audit-logs`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/audit-logs/page` | 分页查询审计日志（支持多条件筛选） | ADMIN, OPERATOR |
| DELETE | `/audit-logs/cleanup?beforeTime=` | 清理过期日志 | ADMIN |

### 8.9 仪表盘 `/dashboard`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/dashboard/stats` | 获取仪表盘聚合统计（设备状态分布 + 告警统计） | 已认证 |

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
