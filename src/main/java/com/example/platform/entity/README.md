# entity — 实体类包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`entity` 包定义与数据库表一一对应的 Java 实体类，是整个项目数据流转的基础载体。每个实体类映射一张数据库表，类的属性映射表的字段。

### 核心作用

- **数据建模**：将数据库表结构映射为 Java 对象
- **数据传递**：在 Controller、Service、Mapper 各层之间传递业务数据
- **ORM 映射**：通过 MyBatis-Plus 注解实现对象关系映射

### 与其他模块的关系

```
数据库表 ←→ Entity 实体类 ←→ Mapper 数据访问 ←→ Service 业务逻辑 ←→ Controller 接口
```

- **Mapper 层**：以 Entity 为操作单元进行数据库 CRUD
- **Service 层**：处理 Entity 对象的业务逻辑
- **Controller 层**：接收/返回 Entity 数据

---

## 二、接口说明

### 2.1 Device — 设备实体

**完整类名**: `com.example.platform.entity.Device`
**对应表名**: `devices`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Integer` | `id` | 是 | 自增 | 设备ID（主键） |
| `deviceName` | `String` | `device_name` | 是 | - | 设备名称 |
| `deviceCode` | `String` | `device_code` | 是 | - | 设备编码（唯一标识，如 SN 码） |
| `typeId` | `Integer` | `type_id` | 否 | - | 设备类型ID（关联 device_types 表） |
| `status` | `Integer` | `status` | 否 | 0 | 设备状态：0-离线，1-在线，2-故障 |
| `location` | `String` | `location` | 否 | - | 设备位置 |
| `ipAddress` | `String` | `ip_address` | 否 | - | IP 地址 |
| `lastOnlineTime` | `LocalDateTime` | `last_online_time` | 否 | - | 最后在线时间 |
| `createTime` | `LocalDateTime` | `create_time` | 否 | 自动填充 | 创建时间 |
| `updateTime` | `LocalDateTime` | `update_time` | 否 | 自动填充 | 更新时间 |

### 2.2 DeviceType — 设备类型实体

**完整类名**: `com.example.platform.entity.DeviceType`
**对应表名**: `device_types`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Integer` | `id` | 是 | 自增 | 类型ID（主键） |
| `typeName` | `String` | `type_name` | 是 | - | 类型名称（如"温度传感器"） |
| `typeCode` | `String` | `type_code` | 是 | - | 类型编码（程序内标识） |
| `description` | `String` | `description` | 否 | - | 类型描述 |
| `createTime` | `LocalDateTime` | `create_time` | 否 | 自动填充 | 创建时间 |
| `updateTime` | `LocalDateTime` | `update_time` | 否 | 自动填充 | 更新时间 |

### 2.3 DeviceData — 设备数据实体

**完整类名**: `com.example.platform.entity.DeviceData`
**对应表名**: `device_data`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Long` | `id` | 是 | 自增 | 数据ID（主键，BIGINT 类型） |
| `deviceId` | `Integer` | `device_id` | 是 | - | 设备ID（关联 devices 表） |
| `dataValue` | `BigDecimal` | `data_value` | 否 | - | 数据值（保留两位小数） |
| `dataUnit` | `String` | `data_unit` | 否 | - | 数据单位（如 ℃、%） |
| `collectTime` | `LocalDateTime` | `collect_time` | 否 | - | 采集时间 |

### 2.4 Alert — 告警实体

**完整类名**: `com.example.platform.entity.Alert`
**对应表名**: `alerts`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Integer` | `id` | 是 | 自增 | 告警ID（主键） |
| `deviceId` | `Integer` | `device_id` | 是 | - | 设备ID（关联 devices 表） |
| `alertType` | `String` | `alert_type` | 是 | - | 告警类型（如"高温告警"） |
| `alertLevel` | `Integer` | `alert_level` | 否 | 1 | 告警级别：1-一般，2-严重，3-紧急 |
| `alertMessage` | `String` | `alert_message` | 否 | - | 告警详细描述 |
| `isHandled` | `Integer` | `is_handled` | 否 | 0 | 是否处理：0-未处理，1-已处理 |
| `handleTime` | `LocalDateTime` | `handle_time` | 否 | - | 处理时间 |
| `handleUser` | `String` | `handle_user` | 否 | - | 处理人 |
| `createTime` | `LocalDateTime` | `create_time` | 否 | - | 告警产生时间 |

---

### 2.5 实体间关系

```
device_types (1) ──── (N) devices (1) ──── (N) device_data
                            │
                            └──── (1) ──── (N) alerts
```

| 关系 | 说明 |
|------|------|
| `device_types` → `devices` | 一个设备类型可对应多个设备（通过 `type_id` 关联） |
| `devices` → `device_data` | 一个设备可产生多条采集数据（通过 `device_id` 关联） |
| `devices` → `alerts` | 一个设备可产生多条告警记录（通过 `device_id` 关联） |

### 2.6 常用注解说明

| 注解 | 用途 | 示例 |
|------|------|------|
| `@Data` | Lombok 注解，自动生成 getter/setter/toString/equals/hashCode | 类级别 |
| `@TableName` | 指定对应的数据库表名 | `@TableName("devices")` |
| `@TableId` | 标记主键字段 | `@TableId(value = "id", type = IdType.AUTO)` |
| `@TableField` | 映射数据库字段名 | `@TableField("device_name")` |
| `@TableField(fill = ...)` | 自动填充字段 | `@TableField(value = "create_time", fill = FieldFill.INSERT)` |
| `@TableLogic` | 逻辑删除标记 | 当前已注释，未启用 |

---

## 三、使用示例

### 示例 1：创建设备对象并保存

```java
Device device = new Device();
device.setDeviceName("4号楼温度传感器");
device.setDeviceCode("TEMP-004");
device.setTypeId(1);
device.setStatus(0);
device.setLocation("4号楼1层大厅");
device.setIpAddress("192.168.1.104");

deviceService.save(device);
// 保存后 device.getId() 可获取自增主键
```

### 示例 2：创建告警记录

```java
Alert alert = new Alert();
alert.setDeviceId(1);
alert.setAlertType("高温告警");
alert.setAlertLevel(3);
alert.setAlertMessage("温度超过 80℃，当前值 85.6℃");

alertService.save(alert);
```

### 示例 3：记录设备采集数据

```java
DeviceData data = new DeviceData();
data.setDeviceId(1);
data.setDataValue(new BigDecimal("36.50"));
data.setDataUnit("℃");
data.setCollectTime(LocalDateTime.now());

deviceDataService.save(data);
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 字段映射失败 | Java 驼峰与数据库下划线不匹配 | 配置了 `map-underscore-to-camel-case: true`，自动转换；也可用 `@TableField` 显式指定 |
| 逻辑删除未启用 | `deleted` 字段已预留但被注释 | 取消注释并修改 `application.yaml` 配置即可启用 |
| 时间自动填充不生效 | 需实现 `MetaObjectHandler` | 当前仅定义了 `fill` 策略，需添加 Handler 实现类 |
| BigDecimal 精度 | `DeviceData.dataValue` 使用 `BigDecimal` | 避免使用 `double`，防止浮点精度丢失 |
| Lombok 不生效 | IDE 未安装 Lombok 插件 | IntelliJ IDEA 需安装 Lombok 插件并开启注解处理 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
