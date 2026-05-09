# IoT-Platform 物联网设备管理平台

> **版本**: v0.0.1-SNAPSHOT | **文档更新日期**: 2026-05-09 | **文档版本**: v1.2

---

## 一、项目概述

### 1.1 项目背景

随着物联网技术的快速发展，设备管理成为各类智能应用的核心需求。本项目以物联网设备管理为业务场景，搭建一套完整的后端服务骨架，帮助初学者从零开始理解项目开发的完整流程。

### 1.2 项目目标与价值

| 目标 | 说明 |
|------|------|
| **教学导向** | 通过真实业务场景，让初学者掌握从需求分析到代码实现的完整开发流程 |
| **骨架搭建** | 提供一个可直接运行的项目骨架，包含分层架构、数据库交互、统一响应等核心能力 |
| **最佳实践** | 展示 Spring Boot + MyBatis-Plus 技术栈的标准用法和项目组织方式 |
| **可扩展性** | 模块化设计，便于后续添加新功能（如权限管理、消息推送等） |

### 1.3 适用场景

- 初学者学习 Spring Boot 项目开发流程
- 理解分层架构（Controller → Service → Mapper）的设计思想
- 学习 MyBatis-Plus ORM 框架的基本使用
- 了解物联网设备管理系统的核心数据模型

---

## 二、核心功能介绍

本项目围绕物联网设备管理的核心业务，划分为以下功能包：

| 功能包 | 作用 | 关键类 |
|--------|------|--------|
| **common** | 统一响应结果封装、业务异常体系、全局异常处理、参数校验分组 | `Result`、`BusinessException`、`GlobalExceptionHandler`、`CreateGroup`/`UpdateGroup` |
| **config** | 框架配置类，管理 MyBatis-Plus 分页插件、自动填充和跨域配置 | `MybatisPlusConfig`、`MyMetaObjectHandler`、`CorsConfig` |
| **controller** | 接收 HTTP 请求，参数分组校验，调用 Service 层处理业务逻辑 | `DeviceController`（9 个接口） |
| **entity** | 数据库表对应的实体类，定义数据结构和校验规则 | `Device`、`DeviceData`、`DeviceType`、`Alert` |
| **mapper** | 数据访问层，负责与数据库交互 | `DeviceMapper`、`DeviceDataMapper` 等 |
| **service** | 业务逻辑层，封装核心业务规则和校验 | `DeviceService`、`DeviceTypeService` 等 |

---

## 三、目录结构说明

```
iot-platform/
├── .mvn/wrapper/                    # Maven Wrapper 配置，无需本地安装 Maven
├── src/
│   ├── main/
│   │   ├── java/com/example/platform/
│   │   │   ├── common/              # 通用工具包（统一响应、异常体系、校验分组）
│   │   │   │   ├── exception/       # 业务异常类（BusinessException 及子类）
│   │   │   │   └── group/           # 校验分组接口（CreateGroup、UpdateGroup）
│   │   │   ├── config/              # 框架配置类（分页插件、自动填充处理器、跨域配置）
│   │   │   ├── controller/          # 控制器层（接收 HTTP 请求）
│   │   │   ├── entity/              # 实体类（对应数据库表，含校验注解）
│   │   │   ├── mapper/              # 数据访问层（MyBatis-Plus Mapper）
│   │   │   ├── service/             # 业务逻辑层接口
│   │   │   │   └── impl/            # 业务逻辑层实现
│   │   │   └── IotPlatformApplication.java  # 项目启动入口
│   │   └── resources/
│   │       └── application.yaml     # 项目配置文件
│   └── test/                        # 测试代码
├── docs/                            # 项目文档目录
│   └── images/                      # 文档图片资源
├── pom.xml                          # Maven 项目配置（依赖管理）
├── mvnw / mvnw.cmd                  # Maven Wrapper 脚本
└── README.md                        # 本文件
```

### 各部分角色说明

| 目录/文件 | 角色 |
|-----------|------|
| `common/` | 基础设施层，提供统一响应、异常体系、全局异常处理和校验分组 |
| `common/exception/` | 业务异常定义，包含 `BusinessException` 基类和具体异常类 |
| `common/group/` | 校验分组接口，`CreateGroup` 和 `UpdateGroup` 用于区分创建/更新场景 |
| `config/` | 配置层，管理分页插件、自动填充处理器和跨域配置 |
| `controller/` | 入口层，负责请求路由、分组参数校验和响应封装 |
| `entity/` | 数据模型层，与数据库表一一对应，承载业务数据和校验规则 |
| `mapper/` | 持久层，继承 MyBatis-Plus `BaseMapper`，提供基础 CRUD |
| `service/` | 业务层，封装业务逻辑和业务校验，面向 Controller 提供服务 |
| `service/impl/` | 业务层实现，具体业务逻辑和校验规则编写位置 |
| `application.yaml` | 集中管理数据库连接、服务端口、日志等所有配置 |

---

## 四、环境配置指南

### 4.1 开发环境要求

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| **JDK** | 17+ | 项目基于 Java 17 编译 |
| **Maven** | 3.8+ | 或使用项目自带的 Maven Wrapper |
| **MySQL** | 8.0+ | 数据库服务 |
| **IDE** | IntelliJ IDEA（推荐）/ VS Code | 需安装 Lombok 插件 |

### 4.2 数据库准备

1. 登录 MySQL，创建数据库：

```sql
CREATE DATABASE iot_device_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

2. 创建数据表：

```sql
USE iot_device_db;

-- 设备类型表
CREATE TABLE device_types (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '类型ID',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    type_code VARCHAR(50) NOT NULL COMMENT '类型编码',
    description VARCHAR(200) COMMENT '类型描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '设备类型表';

-- 设备表
CREATE TABLE devices (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '设备ID',
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_code VARCHAR(50) NOT NULL UNIQUE COMMENT '设备编码',
    type_id INT COMMENT '设备类型ID',
    status INT DEFAULT 0 COMMENT '设备状态：0-离线，1-在线，2-故障',
    location VARCHAR(200) COMMENT '设备位置',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    last_online_time DATETIME COMMENT '最后在线时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '设备表';

-- 设备数据表
CREATE TABLE device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据ID',
    device_id INT NOT NULL COMMENT '设备ID',
    data_value DECIMAL(10,2) COMMENT '数据值',
    data_unit VARCHAR(20) COMMENT '数据单位',
    collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间'
) COMMENT '设备数据表';

-- 告警表
CREATE TABLE alerts (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '告警ID',
    device_id INT NOT NULL COMMENT '设备ID',
    alert_type VARCHAR(50) NOT NULL COMMENT '告警类型',
    alert_level INT DEFAULT 1 COMMENT '告警级别：1-一般，2-严重，3-紧急',
    alert_message VARCHAR(500) COMMENT '告警描述',
    is_handled INT DEFAULT 0 COMMENT '是否处理：0-未处理，1-已处理',
    handle_time DATETIME COMMENT '处理时间',
    handle_user VARCHAR(50) COMMENT '处理人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警产生时间'
) COMMENT '告警表';
```

3. 插入测试数据（可选）：

```sql
INSERT INTO device_types (type_name, type_code, description) VALUES
('温度传感器', 'TEMP_SENSOR', '用于环境温度监测'),
('湿度传感器', 'HUMIDITY_SENSOR', '用于环境湿度监测'),
('烟雾探测器', 'SMOKE_DETECTOR', '用于火灾烟雾检测');

INSERT INTO devices (device_name, device_code, type_id, status, location, ip_address) VALUES
('1号楼温度传感器', 'TEMP-001', 1, 1, '1号楼3层东侧', '192.168.1.101'),
('2号楼湿度传感器', 'HUM-001', 2, 1, '2号楼1层大厅', '192.168.1.102'),
('3号楼烟雾探测器', 'SMOKE-001', 3, 0, '3号楼2层西侧', '192.168.1.103');
```

### 4.3 修改配置文件

编辑 [application.yaml](src/main/resources/application.yaml)，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iot_device_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root          # 修改为你的 MySQL 用户名
    password: your_password # 修改为你的 MySQL 密码
```

### 4.4 安装依赖

项目使用 Maven 管理依赖，无需手动下载 jar 包：

```bash
# 下载依赖并编译（跳过测试，避免因未配置数据库导致测试失败）
./mvnw clean compile -DskipTests

# 或使用本地 Maven
mvn clean compile -DskipTests
```

> **说明**：`mvn clean install` 默认会执行集成测试（`@SpringBootTest`），需要连接数据库。若数据库尚未准备就绪，请使用 `-DskipTests` 跳过测试，待数据库配置完成后再运行测试。

> **Windows 用户**请使用 `mvnw.cmd` 替代 `./mvnw`

---

## 五、快速启动指南

按照以下步骤，5 分钟内即可启动项目：

### Step 1：确认环境

```bash
java -version
# 确认输出 Java 17 或更高版本
```

### Step 2：准备数据库

```bash
mysql -u root -p
# 执行上方「数据库准备」中的 SQL 语句
```

### Step 3：修改配置

修改 `src/main/resources/application.yaml` 中的数据库用户名和密码。

### Step 4：启动项目

```bash
# 在项目根目录下执行
./mvnw spring-boot:run

# Windows 用户
mvnw.cmd spring-boot:run
```

### Step 5：验证启动

启动成功后，控制台将输出：

```
========================================
物联网平台启动成功！
访问地址：http://localhost:8080/api
Druid监控：http://localhost:8080/api/druid
========================================
```

访问以下地址验证：

| 地址 | 说明 |
|------|------|
| `http://localhost:8080/api/devices` | 设备列表接口 |
| `http://localhost:8080/api/devices/online` | 在线设备列表 |
| `http://localhost:8080/api/druid/index.html` | Druid 数据库监控页面（账号/密码：admin/admin） |

---

## 六、技术栈概览

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架，提供自动配置和快速开发能力 |
| MyBatis-Plus | 3.5.16 | ORM 框架，简化数据库操作 |
| Druid | 1.2.28 | 数据库连接池，提供监控和性能统计 |
| MySQL Connector | 9.6.0 | MySQL 数据库驱动 |
| Lombok | - | 减少样板代码（getter/setter/构造器等） |
| Jakarta Validation | - | 参数校验框架，支持分组校验 |

---

## 七、已知问题

> 以下为项目骨架搭建阶段已知的代码问题。部分问题已在 `[f88934db]` 提交中修复。

| 问题 | 状态 | 说明 |
|------|------|------|
| ~~`application.yaml` 中 `type-aliases-package` 包名不一致~~ | ✅ 已修复 | 已修正为 `com.example.platform.entity` |
| ~~`application.yaml` 中日志包名配置不一致~~ | ✅ 已修复 | 已修正为 `com.example.platform` |
| ~~`DeviceDataServiceImpl`、`DeviceTypeServiceImpl`、`AlertServiceImpl` 缺少 `@Service` 注解~~ | ✅ 已修复 | 所有实现类已补全 `@Service` 注解 |
| ~~`MybatisPlusConfig` 分页配置类被整体注释~~ | ✅ 已修复 | 已取消注释，分页插件已启用 |
| ~~`DeviceController` 为空类，未实现任何接口~~ | ✅ 已修复 | 已实现 9 个完整接口，含分组校验、分页查询和删除 |
| ~~时间自动填充未生效~~ | ✅ 已修复 | 已实现 `MyMetaObjectHandler`，`createTime`/`updateTime` 自动填充 |

---

## 八、文档目录索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 项目总览 | [README.md](README.md) | 本文件，项目整体介绍 |
| 通用工具包 | [common/README.md](src/main/java/com/example/platform/common/README.md) | 统一响应、异常体系、校验分组 |
| 配置包 | [config/README.md](src/main/java/com/example/platform/config/README.md) | 框架配置与自动填充 |
| 控制器包 | [controller/README.md](src/main/java/com/example/platform/controller/README.md) | HTTP 接口层 |
| 实体包 | [entity/README.md](src/main/java/com/example/platform/entity/README.md) | 数据模型与校验规则 |
| 数据访问包 | [mapper/README.md](src/main/java/com/example/platform/mapper/README.md) | 数据库交互层 |
| 业务逻辑包 | [service/README.md](src/main/java/com/example/platform/service/README.md) | 业务逻辑与校验 |
| 测试包 | [service/impl/README.md](src/test/java/com/example/platform/service/impl/README.md) | Service 层集成测试 |

---

## 九、文档维护规范

### 9.1 版本控制

- 文档版本与代码版本保持对应，格式为 `v主版本.次版本.修订号`
- 每次文档更新需修改文件头部的版本号和更新日期

### 9.2 更新机制

- 代码发生重大变更时（如接口参数变化、功能调整），**必须同步更新**相关文档
- 新增模块时，需同时创建对应的 README.md 文档

### 9.3 视觉辅助

- 架构图、流程图等图片资源统一放置在 `docs/images/` 目录下
- 文档中使用相对路径引用图片

### 9.4 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-05-04 | 初始版本，创建全部项目文档和模块文档 |
| v1.1 | 2026-05-08 | `[f88934db]` 同步更新：已知问题全部修复；新增 validation 依赖；common 新增 exception/group 子包；config 新增 MyMetaObjectHandler；controller 已实现完整接口；entity 新增校验注解；service 补全 @Service 并新增业务校验 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` 同步更新：common 新增 DeviceNotFoundException(Integer id)；config 新增 CorsConfig 跨域配置；controller 新增分页查询和删除接口（共 9 个）；service 新增 getDeviceOrThrow/findByTypeId/countByStatus 等方法；新增测试目录文档 |

### 9.5 定期审核

- 每季度对文档进行一次全面审核，确保内容时效性和准确性

---

## 十、反馈与贡献

如果你在阅读文档或使用项目过程中发现任何问题，欢迎通过以下方式反馈：

- **GitHub Issues**: 提交 Bug 报告或改进建议
- **项目维护者**: 联系项目维护者获取帮助

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-09。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
> 如发现文档问题或有改进建议，请通过 GitHub Issues 提交反馈。
