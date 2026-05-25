# IoT-Platform 物联网设备管理平台

> **版本**: v0.0.1-SNAPSHOT | **更新日期**: 2026-05-17 | **文档版本**: v3.0

---

## 一、项目简介

IoT-Platform 是一个基于 Spring Boot 4 + MyBatis-Plus 的物联网设备管理平台后端服务。项目以真实业务场景驱动，覆盖设备管理、数据采集、告警规则引擎、定时监控等核心功能，适合初学者理解分层架构设计与工程化开发流程。

### 核心能力

| 能力 | 说明 |
|------|------|
| 设备管理 | 设备 CRUD、状态管理、分页查询、模糊搜索 |
| 数据采集 | 单条/批量数据上报、历史查询、统计聚合 |
| 告警引擎 | 配置化规则引擎，支持设备级/类型级规则、5级告警、去重与升级 |
| 离线检测 | 定时任务扫描离线设备，自动生成离线告警 |
| 指标字典 | 指标元数据内存缓存，告警消息自动填充中文名和单位 |

---

## 二、技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 4.0.6 | 应用框架，自动配置与快速开发 |
| MyBatis-Plus | 3.5.16 | ORM 框架，CRUD 增强、分页插件、乐观锁、自动填充 |
| Druid | 1.2.28 | 数据库连接池，SQL 监控与性能统计 |
| MySQL Connector | 9.6.0 | MySQL 数据库驱动 |
| Jakarta Validation | - | 声明式参数校验，支持分组校验 |
| Lombok | - | 减少样板代码（getter/setter/构造器） |
| Jackson | - | JSON 序列化，`JacksonTypeHandler` 处理 JSON 字段 |

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
│   │   │   │   ├── 通用基础设施文档.md           # 模块文档
│   │   │   │   ├── exception/                 # 异常体系
│   │   │   │   │   ├── BusinessException.java           # 业务异常基类
│   │   │   │   │   ├── DeviceNotFoundException.java     # 设备不存在异常(4003)
│   │   │   │   │   ├── DeviceCodeAlreadyExistsException.java  # 设备编码重复异常(4002)
│   │   │   │   │   ├── DeviceTypeNotFound.java          # 设备类型不存在异常(4001)
│   │   │   │   │   └── GlobalExceptionHandler.java      # 全局异常处理器
│   │   │   │   └── group/                     # 校验分组接口
│   │   │   │       ├── CreateGroup.java
│   │   │   │       └── UpdateGroup.java
│   │   │   ├── config/                        # 框架配置
│   │   │   │   ├── MybatisPlusConfig.java     # 分页+乐观锁插件配置
│   │   │   │   ├── MyMetaObjectHandler.java   # 自动填充（createTime/updateTime/collectTime）
│   │   │   │   ├── CorsConfig.java            # 跨域配置（支持配置文件动态读取）
│   │   │   │   └── 框架配置文档.md              # 模块文档
│   │   │   ├── controller/                    # 控制器层
│   │   │   │   ├── DeviceController.java      # 设备管理（10 个接口）
│   │   │   │   ├── DeviceDataController.java  # 数据采集（7 个接口）
│   │   │   │   ├── DeviceTypeController.java  # 设备类型告警配置（1 个接口）
│   │   │   │   ├── DictMetricController.java  # 指标字典（5 个接口）
│   │   │   │   └── Controller接口文档.md       # 模块文档
│   │   │   ├── dto/                           # 数据传输对象
│   │   │   │   ├── AlertRuleConfig.java       # 告警规则配置（含规则列表）
│   │   │   │   ├── AlertRuleDefinition.java   # 告警规则定义（指标/操作符/阈值/级别）
│   │   │   │   ├── BatchHandleAlertDTO.java   # 批量处理告警请求
│   │   │   │   ├── DeviceDataBatchDTO.java    # 批量数据上报请求
│   │   │   │   ├── HandleAlertDTO.java        # 单条告警处理请求
│   │   │   │   └── 数据传输对象文档.md          # 模块文档
│   │   │   ├── entity/                        # 实体类
│   │   │   │   ├── Device.java                # 设备（含 alertRuleConfig JSON 字段）
│   │   │   │   ├── DeviceData.java            # 设备数据（含 metric 指标字段）
│   │   │   │   ├── DeviceType.java            # 设备类型（含 alertRuleConfig JSON 字段）
│   │   │   │   ├── Alert.java                 # 告警记录
│   │   │   │   ├── DictMetric.java            # 指标字典
│   │   │   │   └── 实体类文档.md               # 模块文档
│   │   │   ├── mapper/                        # 数据访问层
│   │   │   │   ├── DeviceMapper.java          # 含 getOnlineDetection 自定义 SQL
│   │   │   │   ├── DeviceDataMapper.java      # 含统计聚合 SQL
│   │   │   │   ├── AlertMapper.java           # 含 3 个告警统计 SQL
│   │   │   │   ├── DeviceTypeMapper.java
│   │   │   │   ├── DictMetricMapper.java
│   │   │   │   └── 数据访问层文档.md            # 模块文档
│   │   │   ├── service/                       # 业务接口层
│   │   │   │   ├── DeviceService.java
│   │   │   │   ├── DeviceDataService.java
│   │   │   │   ├── DeviceTypeService.java
│   │   │   │   ├── AlertService.java
│   │   │   │   ├── DictMetricService.java
│   │   │   │   ├── 业务接口文档.md              # 模块文档
│   │   │   │   └── impl/                      # 业务实现层
│   │   │   │       ├── DeviceServiceImpl.java
│   │   │   │       ├── DeviceDataServiceImpl.java   # saveWithAlertCheck 事务方法
│   │   │   │       ├── DeviceTypeServiceImpl.java
│   │   │   │       ├── AlertServiceImpl.java        # 含离线告警生成+6个统计方法
│   │   │   │       ├── AlertRuleEngine.java         # 告警规则引擎核心
│   │   │   │       ├── DictMetricServiceImpl.java   # ConcurrentHashMap 内存缓存
│   │   │   │       └── 核心业务实现文档.md          # 模块文档
│   │   │   └── task/                          # 定时任务
│   │   │       └── DeviceMonitorTask.java     # 设备离线检测（cron+阈值配置化）
│   │   └── resources/
│   │       └── application.yaml               # 项目配置
│   └── test/                                  # 测试代码
│       └── service/impl/
│           ├── AlertServiceImplTest.java      # 17个Mockito纯单元测试
│           ├── DeviceServiceImplTest.java     # Mapper基础集成测试
│           ├── DeviceServiceTest.java         # Service业务逻辑集成测试
│           └── 测试文档.md                      # 测试模块文档
├── docs/                                      # 项目文档
│   ├── README.md                              # 文档目录索引
│   ├── audit-record.md                        # 审查问题记录
│   ├── review-summary.md                      # 审查总结报告
│   └── images/                                # 文档图片资源
├── pom.xml                                    # Maven 配置
├── mvnw / mvnw.cmd                            # Maven Wrapper 脚本
└── README.md                                  # 本文件
```

---

## 四、环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 项目基于 Java 17 编译 |
| Maven | 3.8+ | 或使用项目自带的 Maven Wrapper |
| MySQL | 8.0+ | 数据库服务 |

---

## 五、快速开始

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
```

### 5.4 修改配置

编辑 `src/main/resources/application.yaml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iot_device_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root           # 修改为你的 MySQL 用户名
    password: your_password  # 修改为你的 MySQL 密码
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
| `http://localhost:8080/api/devices` | 设备列表 |
| `http://localhost:8080/api/devices/online` | 在线设备 |
| `http://localhost:8080/api/druid/index.html` | Druid 监控（admin/admin） |

---

## 六、API 文档

### 6.1 设备管理 `/devices`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/devices` | 查询所有设备 |
| GET | `/devices/{id}` | 查询设备详情 |
| POST | `/devices` | 新增设备（CreateGroup 校验） |
| PUT | `/devices/{id}` | 更新设备（UpdateGroup 校验） |
| DELETE | `/devices/{id}` | 删除设备 |
| GET | `/devices/online` | 查询在线设备 |
| GET | `/devices/search?name=` | 按名称模糊搜索 |
| PUT | `/devices/{id}/status?status=` | 更新设备状态 |
| GET | `/devices/page` | 分页查询（支持状态/名称筛选） |
| PUT | `/devices/{id}/alert-config` | 更新设备级告警规则 |

### 6.2 数据采集 `/device-data`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/device-data` | 单条数据上报（触发告警检查） |
| POST | `/device-data/batch` | 批量数据上报（逐条触发告警检查） |
| GET | `/device-data/device/{deviceId}` | 设备历史数据分页 |
| GET | `/device-data/device/{deviceId}/latest` | 设备最新数据 |
| GET | `/device-data/device/{deviceId}/range` | 时间段查询 |
| GET | `/device-data/device/{deviceId}/statistics` | 统计聚合（min/max/avg/count） |
| GET | `/device-data/page` | 数据分页查询 |

### 6.3 设备类型 `/device-types`

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/device-types/{id}/alert-config` | 更新类型级告警规则 |

### 6.4 指标字典 `/metric`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/metric/page` | 分页查询指标 |
| POST | `/metric` | 新增指标 |
| PUT | `/metric` | 更新指标 |
| DELETE | `/metric/{id}` | 删除指标 |
| POST | `/metric/cache/refresh` | 手动刷新缓存 |

---

## 七、版本历史

| 版本 | 日期 | Commit | 变更内容 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | - | 初始版本，项目骨架搭建 |
| v1.1 | 2026-05-08 | `f88934db` | 修复已知问题：补全 @Service 注解、启用分页插件、实现 Controller 接口、自动填充 |
| v1.2 | 2026-05-09 | `0e2c8e48` | 新增 DeviceNotFoundException(Integer)、CorsConfig、分页/删除接口、getDeviceOrThrow |
| v1.3 | 2026-05-15 | `05b8ff81` | 新增 DTO 层、告警规则引擎、指标字典缓存、DeviceData.metric 字段、alertRuleConfig JSON 字段 |
| v1.4 | 2026-05-16 | `240739a` | AlertServiceImpl 告警统计方法、AlertMapper 自定义 SQL、DeviceMonitorTask 定时任务、@EnableScheduling |
| v2.0 | 2026-05-17 | - | 代码审查修复：DictMetricController统一响应、URL前缀去重、@Validated分组校验、ConstraintViolationException处理器、乐观锁插件、CORS配置外部化、告警升级方向修正、NPE防御、事务注解、Controller逻辑下沉Service |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-17。*
