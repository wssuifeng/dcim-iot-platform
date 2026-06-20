# IoT-Platform 物联网设备管理平台

> **版本**: v0.0.1-SNAPSHOT | **更新日期**: 2026-06-20 | **文档版本**: v4.1

---

## 一、项目简介

IoT-Platform 是一个基于 Spring Boot 4 + MyBatis-Plus 的物联网设备管理平台后端服务。项目以真实业务场景驱动，覆盖设备管理、数据采集、告警规则引擎、定时监控、安全认证与审计等核心功能，适合初学者理解分层架构设计与工程化开发流程。

### 核心能力

| 能力 | 说明 |
|------|------|
| 设备管理 | 设备 CRUD、状态管理、分页查询、模糊搜索 |
| 数据采集 | 单条/批量数据上报、历史查询、统计聚合 |
| 告警引擎 | 配置化规则引擎，支持设备级/类型级规则、5级告警、去重与升级 |
| 离线检测 | 定时任务扫描离线设备，自动生成离线告警 |
| 设备导入导出 | Excel 批量导入（上限500条）、设备数据/告警记录导出（上限10000条）、模板下载与前端交互 |
| 指标字典 | 指标元数据 Redis 缓存，告警消息自动填充中文名和单位 |
| 安全认证 | JWT 无状态认证、RBAC 三级角色授权（ADMIN/OPERATOR/VIEWER）、BCrypt 密码加密 |
| 用户管理 | 用户 CRUD、账号锁定（连续3次失败锁定30分钟）、IP频率限制、密码重置 |
| 审计日志 | 登录/操作审计记录、多条件分页查询、过期日志自动清理 |
| 缓存服务 | Redis 多层缓存、穿透/击穿/雪崩防御、通知去重、统一 CacheService 接口 |
| Token 黑名单 | 登出/密码重置时 Token 失效控制，黑名单自动过期清理 |
| 仪表盘统计 | 设备/告警聚合统计、互斥锁防缓存击穿 |

---

## 二、技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架，自动配置与快速开发 |
| Spring Security | - | 安全框架，JWT 认证、RBAC 授权、端点访问控制 |
| MyBatis-Plus | 3.5.16 | ORM 框架，CRUD 增强、分页插件、乐观锁、自动填充 |
| Druid | 1.2.28 | 数据库连接池，SQL 监控与性能统计 |
| MySQL Connector | 9.6.0 | MySQL 数据库驱动 |
| JWT (jjwt) | - | JSON Web Token，无状态用户认证与授权 |
| BCrypt | - | 密码哈希加密算法，安全存储用户密码 |
| Jakarta Validation | - | 声明式参数校验，支持分组校验 |
| Lombok | - | 减少样板代码（getter/setter/构造器） |
| Jackson | - | JSON 序列化，`JacksonTypeHandler` 处理 JSON 字段 |
| Redis | 7.x | 缓存数据库，支持字符串/哈希等多种数据结构 |
| Lettuce | 6.x | Redis 客户端，基于 Netty 的异步非阻塞连接 |
| commons-pool2 | 2.x | Lettuce 连接池依赖 |
| EasyExcel | 4.0.3 | Excel 流式导入导出，基于 Apache POI |
| Spring Retry | 2.0.11 | 声明式重试机制，数据上报死锁自动重试 |

---

## 三、项目结构

```
iot-platform/
├── .mvn/wrapper/                              # Maven Wrapper，无需本地安装 Maven
├── src/
│   ├── main/
│   │   ├── java/com/example/platform/
│   │   │   ├── IotPlatformApplication.java    # 启动入口（@EnableScheduling）
│   │   │   ├── common/                        # 通用基础设施
│   │   │   │   ├── Result.java                # 统一响应封装
│   │   │   │   ├── ResultCode.java            # 响应码常量
│   │   │   │
│   │   │   │   ├── exception/                 # 异常体系
│   │   │   │   │   ├── BusinessException.java           # 业务异常基类
│   │   │   │   │   ├── DeviceNotFoundException.java     # 设备不存在异常(4003)
│   │   │   │   │   ├── DeviceCodeAlreadyExistsException.java  # 设备编码重复异常(4002)
│   │   │   │   │   ├── DeviceTypeNotFound.java          # 设备类型不存在异常(4001)
│   │   │   │   │   ├── DeviceTypeCodeAlreadyExistsException.java  # 设备类型编码重复异常(4051)
│   │   │   │   ├── UserNotFoundException.java       # 用户不存在异常(4004)
│   │   │   │   ├── UsernameAlreadyExistsException.java  # 用户名已存在异常(4005)
│   │   │   │   ├── AccountLockedException.java      # 账号锁定异常(4006)
│   │   │   │   ├── CacheOperationException.java     # 缓存操作异常(5001)
│   │   │   │   └── GlobalExceptionHandler.java      # 全局异常处理器
│   │   │   │   ├── util/                      # 工具类
│   │   │   │   │   └── JwtUtil.java           # JWT 工具类（Token 生成/解析/验证）
│   │   │   │   └── group/                     # 校验分组接口
│   │   │   │       ├── CreateGroup.java
│   │   │   │       └── UpdateGroup.java
│   │   │   ├── config/                        # 框架配置
│   │   │   │   ├── SecurityConfig.java        # Spring Security 配置（JWT过滤链/RBAC/密码加密）
│   │   │   │   ├── RedisConfig.java           # Redis 配置（序列化策略/Lettuce连接池）
│   │   │   │   ├── MybatisPlusConfig.java     # 分页+乐观锁插件配置
│   │   │   │   ├── MyMetaObjectHandler.java   # 自动填充（createTime/updateTime/collectTime）
│   │   │   │   ├── CorsConfig.java            # 跨域配置（支持配置文件动态读取）
│   │   │   │
│   │   │   ├── security/                      # 安全组件
│   │   │   │   ├── JwtAuthenticationFilter.java        # JWT Token 提取与校验过滤器
│   │   │   │   ├── CustomAuthenticationEntryPoint.java # 未认证请求返回 401 JSON
│   │   │   │   └── CustomAccessDeniedHandler.java      # 权限不足请求返回 403 JSON
│   │   │   ├── controller/                    # 控制器层
│   │   │   │   ├── DeviceController.java      # 设备管理（13 个接口：CRUD + 导出/导入/模板 + 状态更新 + 告警配置）
│   │   │   │   ├── DeviceDataController.java  # 数据采集与导出（9 个接口）
│   │   │   │   ├── DeviceTypeController.java  # 设备类型管理（6 个接口：CRUD + 告警配置）
│   │   │   │   ├── DictMetricController.java  # 指标字典（5 个接口）
│   │   │   │   ├── AlertController.java       # 告警管理与导出（8 个接口）
│   │   │   │   ├── AuthController.java        # 认证（1 个接口：登录）
│   │   │   │   ├── SysUserController.java     # 用户管理（8 个接口，ADMIN 专属）
│   │   │   │   ├── SysAuditLogController.java # 审计日志（2 个接口）
│   │   │   │   ├── DashboardController.java   # 仪表盘统计（1 个接口）
│   │   │   │
│   │   │   ├── dto/                           # 数据传输对象
│   │   │   │   ├── AlertRuleConfig.java       # 告警规则配置（含规则列表）
│   │   │   │   ├── AlertRuleDefinition.java   # 告警规则定义（指标/操作符/阈值/级别）
│   │   │   │   ├── BatchHandleAlertDTO.java   # 批量处理告警请求
│   │   │   │   ├── DeviceDataBatchDTO.java    # 批量数据上报请求
│   │   │   │   ├── HandleAlertDTO.java        # 单条告警处理请求
│   │   │   │   ├── LoginDTO.java              # 登录请求 DTO
│   │   │   │   ├── LoginResponseDTO.java      # 登录成功响应 DTO（含 JWT Token）
│   │   │   │   ├── PasswordResetDTO.java      # 密码重置请求 DTO
│   │   │   │   ├── DashboardStatsDTO.java      # 仪表盘统计响应 DTO
│   │   │   │   ├── DeviceExportRow.java       # 设备导出行映射 DTO（@ExcelProperty）
│   │   │   │
│   │   │   ├── entity/                        # 实体类
│   │   │   │   ├── Device.java                # 设备（含 alertRuleConfig JSON 字段）
│   │   │   │   ├── DeviceData.java            # 设备数据（含 metric 指标字段）
│   │   │   │   ├── DeviceType.java            # 设备类型（含 alertRuleConfig JSON 字段）
│   │   │   │   ├── Alert.java                 # 告警记录
│   │   │   │   ├── DictMetric.java            # 指标字典
│   │   │   │   ├── SysUser.java               # 用户（含 BCrypt 密码/锁定机制/角色）
│   │   │   │   ├── SysAuditLog.java           # 审计日志
│   │   │   │
│   │   │   ├── mapper/                        # 数据访问层
│   │   │   │   ├── DeviceMapper.java          # 含 getOnlineDetection 自定义 SQL
│   │   │   │   ├── DeviceDataMapper.java      # 含统计聚合 SQL
│   │   │   │   ├── AlertMapper.java           # 含 3 个告警统计 SQL
│   │   │   │   ├── DeviceTypeMapper.java
│   │   │   │   ├── DictMetricMapper.java
│   │   │   │   ├── SysUserMapper.java         # 含 findByUsername 自定义 SQL
│   │   │   │   ├── SysAuditLogMapper.java     # 含 deleteByCreateTimeBefore 清理 SQL
│   │   │   │
│   │   │   ├── service/                       # 业务接口层
│   │   │   │   ├── DeviceService.java
│   │   │   │   ├── DeviceDataService.java
│   │   │   │   ├── DeviceTypeService.java
│   │   │   │   ├── AlertService.java
│   │   │   │   ├── AlertConfigService.java     # 告警规则配置缓存服务
│   │   │   │   ├── DictMetricService.java
│   │   │   │   ├── CacheService.java           # 统一缓存服务接口
│   │   │   │   ├── DashboardStatsService.java  # 仪表盘统计服务
│   │   │   │   ├── NotificationDedupService.java # 通知去重服务
│   │   │   │   ├── TokenBlacklistService.java  # Token 黑名单服务
│   │   │   │   ├── AuthService.java           # 认证服务接口
│   │   │   │   ├── SysUserService.java        # 用户服务接口
│   │   │   │   ├── SysAuditLogService.java    # 审计日志服务接口
│   │   │   │
│   │   │   │   └── impl/                      # 业务实现层
│   │   │   │       ├── DeviceServiceImpl.java
│   │   │   │       ├── DeviceDataServiceImpl.java   # saveWithAlertCheck 事务方法
│   │   │   │       ├── DeviceTypeServiceImpl.java
│   │   │   │       ├── AlertServiceImpl.java        # 含离线告警生成+6个统计方法
│   │   │   │       ├── AlertConfigServiceImpl.java  # 告警规则配置缓存实现
│   │   │   │       ├── AlertRuleEngine.java         # 告警规则引擎核心
│   │   │   │       ├── DictMetricServiceImpl.java   # Redis 分布式缓存
│   │   │   │       ├── CacheServiceImpl.java        # 统一缓存服务实现
│   │   │   │       ├── DashboardStatsServiceImpl.java # 仪表盘统计（互斥锁防击穿）
│   │   │   │       ├── NotificationDedupServiceImpl.java # 通知去重实现
│   │   │   │       ├── AuthServiceImpl.java         # 认证实现（IP频率限制/账号锁定/JWT签发）
│   │   │   │       ├── SysUserServiceImpl.java      # 用户管理（BCrypt加密/SQL原子递增/字段白名单）
│   │   │   │       ├── SysAuditLogServiceImpl.java  # 审计日志（多条件查询/过期清理）
│   │   │   │
│   │   │   └── task/                          # 定时任务
│   │   │       └── DeviceMonitorTask.java     # 设备离线检测（cron+阈值配置化）
│   │   └── resources/
│   │       └── application.yaml               # 项目配置
│   └── test/                                  # 测试代码
│       ├── IotPlatformApplicationTests.java  # Spring Boot 启动测试
│       ├── service/impl/
│       │   ├── AlertServiceImplTest.java      # 17个Mockito纯单元测试
│       │   ├── AlertServiceTest.java          # AlertService 集成测试
│       │   ├── AlertConfigServiceTest.java    # AlertConfigService 单元测试
│       │   ├── DashboardStatsServiceTest.java # DashboardStatsService 单元测试
│       │   ├── DictMetricServiceTest.java     # DictMetricService 单元测试
│       │   ├── NotificationDedupServiceTest.java # 通知去重单元测试
│       │   ├── DeviceServiceImplTest.java     # Mapper基础集成测试
│       │   ├── DeviceServiceTest.java         # Service业务逻辑集成测试
│       │   └── AuthServiceImplTest.java       # AuthService 单元测试
│       ├── service/
│       │   ├── CacheServiceTest.java          # CacheService 单元测试
│       │   └── TokenBlacklistServiceTest.java # TokenBlacklist 单元测试
│       ├── mapper/
│       │   └── DeviceMapperTest.java          # DeviceMapper 自定义SQL集成测试
├── AGENTS.md                                  # AI Agent 上下文（技术栈/约定/文档导航）
├── pom.xml                                    # Maven 配置
├── mvnw / mvnw.cmd                            # Maven Wrapper 脚本
└── README.md                                  # 本文件
```

---

## 四、数据模型

### 实体关系图（ER）

```
device_types ──（1:N）──▶ devices ──（1:N）──▶ device_data
     │                       │
     │                       │（1:N）
     │                       ▼
     │                     alerts
     │
     └──（引用）──▶ dict_metric
```

### 核心实体

| 实体 | 表 | 核心字段 | 说明 |
|------|----|---------|------|
| **Device** | devices | id, device_name, device_code, type_id, status(0/1/2), location, ip_address, alert_rule_config(JSON) | 设备基础信息，设备级告警规则优先于类型级 |
| **DeviceType** | device_types | id, type_name, type_code, description, alert_rule_config(JSON) | 设备分类，类型级告警规则作为降级方案 |
| **DeviceData** | device_data | id, device_id, metric, data_value, collect_time | 设备上报数据，触发告警检查 |
| **Alert** | alerts | id, device_id, alert_type, alert_level(1-5), alert_message, is_handled, handle_user | 告警记录，级别越小越紧急 |
| **DictMetric** | dict_metric | id, metric_key, metric_name, metric_unit | 指标元数据，Redis 缓存，告警消息填充中文名/单位 |
| **SysUser** | sys_user | id, username, password(BCrypt), nickname, role, status, login_fail_count, lock_time | 用户认证，RBAC 三级角色，连续3次失败锁定30分钟 |
| **SysAuditLog** | sys_audit_log | id, user_id, username, operation, ip, detail, create_time | 操作审计，过期自动清理 |

**关系说明**：
- `device_types` 1:N `devices`：一个设备类型下有多个设备
- `devices` 1:N `device_data`：一个设备产生多条采集数据
- `devices` 1:N `alerts`：一个设备产生多条告警记录
- `dict_metric` 被告警引擎引用，用于告警消息中填充指标中文名和单位

---

## 五、环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 项目基于 Java 17 编译 |
| Maven | 3.8+ | 或使用项目自带的 Maven Wrapper |
| MySQL | 9.6 | 数据库服务，驱动 mysql-connector-j 9.6.0 |

---

## 六、快速开始

### 5.1 创建数据库

```sql
CREATE DATABASE iot_device_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 5.2 建表

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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    alert_level INT DEFAULT 1 COMMENT '告警级别',
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
    description VARCHAR(200) COMMENT '描述'
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
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) COMMENT '审计日志表';
```

### 5.3 插入测试数据

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

### 5.4 修改配置

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

### 5.5 构建与运行

```bash
# 编译（跳过测试）
./mvnw clean compile -DskipTests

# 启动
./mvnw spring-boot:run

# Windows 用户使用 mvnw.cmd
```

### 5.6 验证启动

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

## 七、API 文档

> **首选方式**：启动应用后访问 `/swagger-ui.html` 即可查看完整的交互式 API 文档（含请求/响应示例、JWT 认证、字段说明）。以下为静态参考清单。

### 6.1 认证 `/auth`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/auth/login` | 用户登录，返回 JWT Token | 公开 |

### 6.2 设备管理 `/devices`

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

### 6.3 数据采集 `/device-data`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/device-data` | 单条数据上报（触发告警检查） | ADMIN, OPERATOR |
| POST | `/device-data/batch` | 批量数据上报（逐条触发告警检查） | ADMIN, OPERATOR |
| GET | `/device-data/device/{deviceId}` | 设备历史数据分页 | 已认证 |
| GET | `/device-data/device/{deviceId}/latest` | 设备最新数据 | 已认证 |
| GET | `/device-data/device/{deviceId}/range` | 时间段查询 | 已认证 |
| GET | `/device-data/device/{deviceId}/statistics` | 统计聚合（min/max/avg/count） | 已认证 |
| GET | `/device-data/page` | 数据分页查询 | 已认证 |

### 6.4 设备类型 `/device-types`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| PUT | `/device-types/{id}/alert-config` | 更新类型级告警规则 | ADMIN, OPERATOR |

### 6.5 告警管理 `/alerts`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/alerts` | 分页查询告警（支持多条件筛选） | 已认证 |
| GET | `/alerts/{id}` | 查询告警详情 | 已认证 |
| GET | `/alerts/unhandled` | 查询所有未处理告警 | 已认证 |
| PUT | `/alerts/batch-handle` | 批量处理告警 | ADMIN, OPERATOR |
| GET | `/alerts/stats/device/{deviceId}` | 按设备统计告警 | 已认证 |
| GET | `/alerts/stats/time-range` | 按时间段统计告警 | 已认证 |
| GET | `/alerts/stats/device-ranking` | 设备告警排名 | 已认证 |

### 6.6 指标字典 `/metric`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/metric/page` | 分页查询指标 | 已认证 |
| POST | `/metric` | 新增指标 | ADMIN, OPERATOR |
| PUT | `/metric` | 更新指标 | ADMIN, OPERATOR |
| DELETE | `/metric/{id}` | 删除指标 | ADMIN |
| POST | `/metric/cache/refresh` | 手动刷新缓存 | 已认证 |

### 6.7 用户管理 `/users`

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

### 6.8 审计日志 `/audit-logs`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/audit-logs/page` | 分页查询审计日志（支持多条件筛选） | ADMIN, OPERATOR |
| DELETE | `/audit-logs/cleanup?beforeTime=` | 清理过期日志 | ADMIN |

### 6.9 仪表盘 `/dashboard`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/dashboard/stats` | 获取仪表盘聚合统计（设备状态分布 + 告警统计） | 已认证 |

---

## 八、RBAC 权限模型

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

## 九、版本历史

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

---

## 十、文档导航

| 文档 | 用途 | 何时读 |
|------|------|--------|
| `AGENTS.md` | AI Agent 上下文（技术栈/约定/禁忌/构建命令） | 新 AI 会话第一步（与本 README 一起） |
| `开发流程学习文档/需求文档/IoT-Platform需求规格说明书.md` | 需求真相源，51 条需求 + 追踪矩阵 | 新增/改需求 |
| `开发流程学习文档/需求文档/开发优先级计划.md` | P0→P3 路线图 | 规划迭代 |
| `开发流程学习文档/学习笔记/` | 模块学习指南（生活类比 + 技术盲区字典） | 学习某模块原理 |
| `/swagger-ui.html`（启动后访问） | 交互式 API 文档，从代码注解实时生成 | 查接口/联调 |
| `git log` | 变更历史 | 排查问题 |

> **提示**：原 `src` 各包下的 README 文档已退役（内容迁移至 Swagger 注解和本 README），新会话请勿再读取或创建 src 内 README。

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-06-19。*
