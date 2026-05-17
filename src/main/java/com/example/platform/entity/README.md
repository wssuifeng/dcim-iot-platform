# entity — 实体类包

> **版本**: v1.3 | **更新日期**: 2026-05-16

---

## 一、功能用途

`entity` 包定义与数据库表一一对应的 Java 实体类，是整个项目数据流转的基础载体。每个实体类映射一张数据库表，类的属性映射表的字段。

### 核心作用

- **数据建模**：将数据库表结构映射为 Java 对象
- **数据传递**：在 Controller、Service、Mapper 各层之间传递业务数据
- **ORM 映射**：通过 MyBatis-Plus 注解实现对象关系映射
- **参数校验**：通过 Jakarta Validation 注解定义字段校验规则

### 与其他模块的关系

```
数据库表 ←→ Entity 实体类 ←→ Mapper 数据访问 ←→ Service 业务逻辑 ←→ Controller 接口
                ↓ @Validated 分组校验
         GlobalExceptionHandler 拦截校验异常
```

- **Mapper 层**：以 Entity 为操作单元进行数据库 CRUD
- **Service 层**：处理 Entity 对象的业务逻辑
- **Controller 层**：接收/返回 Entity 数据，通过 `@Validated` 触发校验
- **common/exception**：校验失败时由 `GlobalExceptionHandler` 统一处理

---

## 二、接口说明

### 2.1 Device — 设备实体

**完整类名**: `com.example.platform.entity.Device`
**对应表名**: `devices`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 校验规则 | 说明 |
|--------|-----------|-----------|------|--------|----------|------|
| `id` | `Integer` | `id` | 更新时必填 | 自增 | `@NotNull(groups=UpdateGroup)` | 设备ID（主键） |
| `deviceName` | `String` | `device_name` | 是 | - | `@NotBlank(groups={Create,Update})` `@Size(min=2,max=50)` | 设备名称 |
| `deviceCode` | `String` | `device_code` | 是 | - | `@NotBlank(groups={Create,Update})` `@Pattern(^[A-Za-z0-9_-]+$)` | 设备编码（唯一标识） |
| `typeId` | `Integer` | `type_id` | 是 | - | `@NotNull(groups={Create,Update})` | 设备类型ID |
| `status` | `Integer` | `status` | 是 | - | `@NotNull(groups={Create,Update})` `@Min(0)` `@Max(2)` | 设备状态：0-离线，1-在线，2-故障 |
| `location` | `String` | `location` | 是 | - | `@NotBlank(groups={Create,Update})` `@Size(max=100)` | 设备位置 |
| `ipAddress` | `String` | `ip_address` | 否 | - | `@Pattern(IP格式，允许空串)` | IP 地址（可选） |
| `lastOnlineTime` | `LocalDateTime` | `last_online_time` | 否 | - | 无（系统自动记录） | 最后在线时间 |
| `createTime` | `LocalDateTime` | `create_time` | 否 | 自动填充 | 无 | 创建时间 |
| `updateTime` | `LocalDateTime` | `update_time` | 否 | 自动填充 | 无 | 更新时间 |
| `alertRuleConfig` | `AlertRuleConfig` | `alert_rule_config` | 否 | - | 无 | 设备级告警规则配置（JSON 格式，使用 `JacksonTypeHandler`） |

#### 校验注解详解

| 注解 | 作用 | 分组 | 说明 |
|------|------|------|------|
| `@NotNull` | 值不能为 null | `UpdateGroup` | `id` 仅在更新时必填 |
| `@NotBlank` | 字符串不能为 null/空/纯空格 | `CreateGroup` + `UpdateGroup` | `deviceName`、`deviceCode`、`location` 在创建和更新时都必填 |
| `@Size(min=2, max=50)` | 字符串长度范围 | 默认（所有分组） | `deviceName` 长度 2-50 |
| `@Pattern(regexp=...)` | 正则表达式匹配 | 默认（所有分组） | `deviceCode` 仅允许字母、数字、下划线、短横线；`ipAddress` 允许空串或合法 IP |
| `@Min(0)` / `@Max(2)` | 数值范围 | `CreateGroup` + `UpdateGroup` | `status` 只能为 0/1/2 |

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
| `alertRuleConfig` | `AlertRuleConfig` | `alert_rule_config` | 否 | - | 设备类型级告警规则配置（JSON 格式，使用 `JacksonTypeHandler`） |

### 2.3 DeviceData — 设备数据实体

**完整类名**: `com.example.platform.entity.DeviceData`
**对应表名**: `device_data`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Long` | `id` | 是 | 自增 | 数据ID（主键，BIGINT 类型） |
| `deviceId` | `Integer` | `device_id` | 是 | - | 设备ID（关联 devices 表），`@NotNull` |
| `metric` | `String` | `metric` | 否 | - | 数据指标标识（如 temperature、humidity），替代原 dataUnit |
| `dataValue` | `BigDecimal` | `data_value` | 是 | - | 数据值，`@NotNull` + `@DecimalMin(-999.99)` + `@DecimalMax(999.99)` |
| `collectTime` | `LocalDateTime` | `collect_time` | 否 | 自动填充 | 采集时间 |

### 2.4 Alert — 告警实体

**完整类名**: `com.example.platform.entity.Alert`
**对应表名**: `alerts`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Integer` | `id` | 是 | 自增 | 告警ID（主键） |
| `deviceId` | `Integer` | `device_id` | 是 | - | 设备ID（关联 devices 表） |
| `alertType` | `String` | `alert_type` | 是 | - | 告警类型（如"高温告警"） |
| `alertLevel` | `Integer` | `alert_level` | 否 | 1 | 告警级别：1-紧急，2-严重，3-一般，4-警告，5-提示，`@Min(1)` `@Max(5)` |
| `alertMessage` | `String` | `alert_message` | 否 | - | 告警详细描述 |
| `isHandled` | `Integer` | `is_handled` | 否 | 0 | 是否处理：0-未处理，1-已处理 |
| `handleTime` | `LocalDateTime` | `handle_time` | 否 | - | 处理时间 |
| `handleUser` | `String` | `handle_user` | 否 | - | 处理人 |
| `createTime` | `LocalDateTime` | `create_time` | 否 | 自动填充 | 告警产生时间 |

### 2.5 DictMetric — 指标字典实体

**完整类名**: `com.example.platform.entity.DictMetric`
**对应表名**: `dict_metric`

#### 字段说明

| 字段名 | Java 类型 | 数据库字段 | 必填 | 默认值 | 说明 |
|--------|-----------|-----------|------|--------|------|
| `id` | `Integer` | `id` | 是 | 自增 | 主键 |
| `metricKey` | `String` | `metric_key` | 是 | - | 指标标识（如 temperature） |
| `metricName` | `String` | `metric_name` | 是 | - | 指标中文名（如 温度） |
| `metricUnit` | `String` | `metric_unit` | 否 | - | 数据单位（如 ℃） |
| `description` | `String` | `description` | 否 | - | 描述说明 |

### 2.6 实体间关系

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

#### MyBatis-Plus 注解

| 注解 | 用途 | 示例 |
|------|------|------|
| `@Data` | Lombok 注解，自动生成 getter/setter/toString/equals/hashCode | 类级别 |
| `@TableName` | 指定对应的数据库表名 | `@TableName("devices")` |
| `@TableId` | 标记主键字段 | `@TableId(value = "id", type = IdType.AUTO)` |
| `@TableField` | 映射数据库字段名 | `@TableField("device_name")` |
| `@TableField(fill = ...)` | 自动填充字段 | `@TableField(value = "create_time", fill = FieldFill.INSERT)` |
| `@TableField(typeHandler = ...)` | 指定字段类型处理器 | `@TableField(value = "alert_rule_config", typeHandler = JacksonTypeHandler.class)` |
| `@TableName(autoResultMap = true)` | 启用自动结果映射（typeHandler 必需） | `@TableName(value = "devices", autoResultMap = true)` |
| `@TableLogic` | 逻辑删除标记 | 当前已注释，未启用 |

#### Jakarta Validation 注解

| 注解 | 用途 | 来源 |
|------|------|------|
| `@NotNull` | 值不能为 null | `jakarta.validation.constraints` |
| `@NotBlank` | 字符串不能为 null/空/纯空格 | `jakarta.validation.constraints` |
| `@Size(min, max)` | 字符串长度范围 | `jakarta.validation.constraints` |
| `@Pattern(regexp)` | 正则表达式匹配 | `jakarta.validation.constraints` |
| `@Min(value)` | 数值最小值 | `jakarta.validation.constraints` |
| `@Max(value)` | 数值最大值 | `jakarta.validation.constraints` |
| `@Validated(Group.class)` | 触发分组校验 | `org.springframework.validation.annotation` |

---

## 三、使用示例

### 示例 1：创建设备（校验自动生效）

```java
// Controller 中使用 @Validated 触发校验
@PostMapping
public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
    deviceService.save(device);
    return Result.success();
}

// 请求体（合法数据）
{
  "deviceName": "4号楼温度传感器",
  "deviceCode": "TEMP-004",
  "typeId": 1,
  "status": 0,
  "location": "4号楼1层大厅"
}
// createTime 和 updateTime 由 MyMetaObjectHandler 自动填充，无需传入
```

### 示例 2：校验失败时的响应

```java
// 请求体（deviceName 为空，deviceCode 包含非法字符）
{
  "deviceName": "",
  "deviceCode": "TEMP@#$",
  "typeId": null,
  "status": 5,
  "location": ""
}

// GlobalExceptionHandler 自动返回：
{
  "code": 400,
  "message": "设备名称不能为空; 设备编码只能包含字母、数字、下划线和短横线; 设备类型不能为空; 设备状态不能为空; 设备位置不能为空"
}
```

### 示例 3：创建告警记录

```java
Alert alert = new Alert();
alert.setDeviceId(1);
alert.setAlertType("高温告警");
alert.setAlertLevel(3);
alert.setAlertMessage("温度超过 80℃，当前值 85.6℃");

alertService.save(alert);
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 字段映射失败 | Java 驼峰与数据库下划线不匹配 | 配置了 `map-underscore-to-camel-case: true`，自动转换；也可用 `@TableField` 显式指定 |
| 校验不生效 | `@Validated` 未触发校验 | 确保添加了 `spring-boot-starter-validation` 依赖，且 Controller 参数上有 `@Validated` 注解并指定了分组 |
| 分组校验遗漏 | 未指定 `groups` 导致校验不执行 | Entity 字段的校验注解必须通过 `groups` 声明所属分组，Controller 的 `@Validated` 也必须指定对应分组 |
| 逻辑删除未启用 | `deleted` 字段已预留但被注释 | 取消注释并修改 `application.yaml` 配置即可启用 |
| 时间自动填充 | `createTime`/`updateTime` 由 `MyMetaObjectHandler` 自动填充 | ✅ 已实现，无需手动设置 |
| Alert alertLevel 注释不一致 | 源码 `Alert.java` 注释为"1-一般，2-严重，3-紧急"（3级），但 `AlertRuleDefinition.level` 校验为 `@Min(1) @Max(5)`（5级） | 实际业务逻辑使用5级体系，源码注释待更新 |
| BigDecimal 精度 | `DeviceData.dataValue` 使用 `BigDecimal` | 避免使用 `double`，防止浮点精度丢失 |
| JSON 字段存储 | `alertRuleConfig` 使用 `JacksonTypeHandler` | `@TableName` 必须设置 `autoResultMap = true`，否则查询时 JSON 不会反序列化 |
| 指标标识替代单位 | `DeviceData` 新增 `metric` 字段替代原 `dataUnit` | `metric` 关联 `DictMetric.metricKey`，通过字典服务获取中文单位 |
| Lombok 不生效 | IDE 未安装 Lombok 插件 | IntelliJ IDEA 需安装 Lombok 插件并开启注解处理 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，包含 4 个实体类基本字段说明 |
| v1.1 | 2026-05-08 | `[f88934db]` | Device 实体新增 Jakarta Validation 校验注解（@NotBlank/@NotNull/@Size/@Pattern/@Min/@Max）及分组校验说明；更新字段必填状态；新增校验注解详解章节 |
| v1.2 | 2026-05-15 | `[05b8ff81]` | Device/DeviceType 新增 `alertRuleConfig` JSON 字段（JacksonTypeHandler）；DeviceData 新增 `metric` 字段替代 `dataUnit`，新增校验注解（@NotNull/@DecimalMin/@DecimalMax），`collectTime` 改为自动填充；Alert 告警级别更新为 1-5 级，`createTime` 改为自动填充；新增 DictMetric 指标字典实体；新增 JSON 字段和 autoResultMap 注解说明 |
| v1.3 | 2026-05-16 | `[240739a]` | 标注 Alert alertLevel 源码注释（3级）与业务逻辑（5级）不一致问题 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-16。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
