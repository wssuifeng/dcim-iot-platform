# dto — 数据传输对象包

> **版本**: v2.0 | **更新日期**: 2026-05-17

---

## 一、功能用途

`dto`（Data Transfer Object）包定义了前后端交互的数据传输对象，用于封装请求参数、实现参数校验，并与 Entity 层解耦。DTO 只承载需要传输的数据，不直接映射数据库表。

### 核心作用

- **请求参数封装**：将复杂请求参数封装为结构化对象，替代零散的 `@RequestParam`
- **参数校验**：通过 Jakarta Validation 注解在 DTO 层实现声明式校验
- **层级解耦**：DTO 与 Entity 分离，避免直接暴露数据库字段结构
- **批量操作**：提供批量操作的请求体结构（如批量上报数据、批量处理告警）

### 与其他模块的关系

```
前端 JSON → Controller 接收 DTO → 校验通过 → 转换为 Entity → Service 处理
Controller 返回 DTO ← Service 返回 Entity ← Mapper 查询数据库
```

- **Controller**：使用 `@RequestBody` 将请求体反序列化为 DTO 对象
- **Service**：将 DTO 转换为 Entity 后执行业务逻辑
- **Entity**：`Device` 和 `DeviceType` 中的 `alertRuleConfig` 字段使用 `AlertRuleConfig` DTO 作为 JSON 类型处理器

### 目录结构

```
dto/
├── AlertRuleConfig.java       # 告警规则配置顶层对象
├── AlertRuleDefinition.java   # 单条告警规则定义
├── BatchHandleAlertDTO.java   # 批量处理告警请求
├── DeviceDataBatchDTO.java    # 批量上报设备数据请求
└── HandleAlertDTO.java        # 单条告警处理请求
```

---

## 二、接口说明

### 2.1 AlertRuleConfig — 告警规则配置

**完整类名**: `com.example.platform.dto.AlertRuleConfig`

告警规则配置的顶层对象，包含一组告警规则列表。存储在 `Device` 和 `DeviceType` 的 `alert_rule_config` 数据库字段中（JSON 格式）。

#### 字段说明

| 字段 | 类型 | 校验规则 | 说明 |
|------|------|----------|------|
| `rules` | `List<AlertRuleDefinition>` | `@NotEmpty` + `@Valid` | 告警规则列表，至少包含一条规则 |

#### 使用场景

- `PUT /devices/{id}/alert-config`：更新设备级告警规则
- `PUT /api/device-types/{id}/alert-config`：更新设备类型级告警规则
- `AlertRuleEngine` 读取该配置进行告警评估

### 2.2 AlertRuleDefinition — 单条告警规则定义

**完整类名**: `com.example.platform.dto.AlertRuleDefinition`

定义单条告警规则的触发条件、级别和消息模板。

#### 字段说明

| 字段 | 类型 | 校验规则 | 说明 |
|------|------|----------|------|
| `metricKey` | `String` | `@NotBlank` | 指标标识，如 `temperature`、`humidity`，需与 `dict_metric` 表中的 `metric_key` 对应 |
| `operator` | `String` | `@NotBlank` + `@Pattern(^(GT\|LT\|GTE\|LTE\|EQ)$)` | 比较运算符：GT(大于)、LT(小于)、GTE(大于等于)、LTE(小于等于)、EQ(等于) |
| `threshold` | `Double` | `@NotNull` | 触发阈值 |
| `level` | `Integer` | `@NotNull` + `@Min(1)` + `@Max(5)` | 告警级别（1-紧急 → 5-提示） |
| `messageTemplate` | `String` | `@NotBlank` | 消息模板，支持占位符 `{metricName}`、`{unit}`、`{value}`、`{threshold}` |

#### 告警级别对照

| 级别 | 含义 | 英文 |
|------|------|------|
| 1 | 紧急 | Critical |
| 2 | 严重 | Major |
| 3 | 一般 | Minor |
| 4 | 警告 | Warning |
| 5 | 提示 | Info |

### 2.3 BatchHandleAlertDTO — 批量处理告警请求

**完整类名**: `com.example.platform.dto.BatchHandleAlertDTO`

#### 字段说明

| 字段 | 类型 | 校验规则 | 说明 |
|------|------|----------|------|
| `ids` | `List<Integer>` | `@NotEmpty` + `@Size(max=100)` | 告警ID列表，单次最多100条 |
| `handleUser` | `String` | `@NotBlank` + `@Size(max=50)` | 处理人姓名 |

### 2.4 DeviceDataBatchDTO — 批量上报设备数据请求

**完整类名**: `com.example.platform.dto.DeviceDataBatchDTO`

#### 字段说明

| 字段 | 类型 | 校验规则 | 说明 |
|------|------|----------|------|
| `dataList` | `List<DeviceData>` | `@NotEmpty` + `@Valid` | 设备数据列表 |

### 2.5 HandleAlertDTO — 单条告警处理请求

**完整类名**: `com.example.platform.dto.HandleAlertDTO`

#### 字段说明

| 字段 | 类型 | 校验规则 | 说明 |
|------|------|----------|------|
| `handleUser` | `String` | `@NotBlank` | 处理人姓名 |
| `alertId` | `Integer` | `@NotNull` + `@Min(1)` | 告警ID |

---

## 三、使用示例

### 示例 1：配置设备告警规则

**请求**：

```
PUT /api/devices/1/alert-config
Content-Type: application/json

{
  "rules": [
    {
      "metricKey": "temperature",
      "operator": "GT",
      "threshold": 80,
      "level": 1,
      "messageTemplate": "{metricName} 触发规则，当前值 {value}{unit}，阈值 {threshold}{unit}"
    },
    {
      "metricKey": "humidity",
      "operator": "GTE",
      "threshold": 90,
      "level": 3,
      "messageTemplate": "{metricName} 超过阈值，当前值 {value}{unit}"
    }
  ]
}
```

### 示例 2：批量上报设备数据

**请求**：

```
POST /api/device-data/batch
Content-Type: application/json

{
  "dataList": [
    {
      "deviceId": 1,
      "metric": "temperature",
      "dataValue": 85.5
    },
    {
      "deviceId": 1,
      "metric": "humidity",
      "dataValue": 65.2
    }
  ]
}
```

### 示例 3：批量处理告警

**请求**：

```
POST /api/alerts/batch-handle
Content-Type: application/json

{
  "ids": [10, 11, 12],
  "handleUser": "张三"
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| DTO 与 Entity 混用 | `DeviceDataBatchDTO` 内嵌了 `DeviceData` Entity | 当前为简化设计；如需严格解耦，可创建独立的 `DeviceDataDTO` |
| 校验不生效 | Controller 方法参数缺少 `@Valid` 或 `@Validated` | 确保 `@RequestBody` 前添加 `@Valid` 或 `@Validated` |
| 嵌套校验不生效 | `AlertRuleConfig.rules` 列表内元素未校验 | 必须在 `rules` 字段上同时添加 `@Valid` 注解 |
| JSON 存储字段 | `AlertRuleConfig` 作为 JSON 存入数据库 | Entity 字段需配置 `@TableField(typeHandler = JacksonTypeHandler.class)` + `@TableName(autoResultMap = true)` |
| 消息模板占位符 | `messageTemplate` 支持的占位符有限 | 当前支持 `{metricName}`、`{unit}`、`{value}`、`{threshold}` 四个占位符 |
| BatchHandleAlertDTO 类型注解 | `ids` 字段已修复无效的 `List<@Min(1) Integer>` 类型注解 | ✅ 已修复，改为 `List<Integer>`，元素级校验由业务层处理 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-15 | `[05b8ff81]` | 初始版本，包含 AlertRuleConfig、AlertRuleDefinition、BatchHandleAlertDTO、DeviceDataBatchDTO、HandleAlertDTO 五个 DTO 类 |
| v2.0 | 2026-05-17 | — | AlertRuleDefinition 字段名 `metric` → `metricKey`；`operator` 正则改为 GT/LT/GTE/LTE/EQ；`threshold` 类型由 `BigDecimal` 改为 `Double`；删除 `alertType` 字段；`messageTemplate` 占位符改为 `{metricName}/{unit}/{value}/{threshold}`；BatchHandleAlertDTO `ids` 字段移除无效类型注解，改为 `List<Integer>`；DeviceDataBatchDTO `dataList` 校验改为 `@NotEmpty` + `@Valid`；HandleAlertDTO 补充 `alertId` 字段说明 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-17。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
