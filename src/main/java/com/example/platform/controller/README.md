# controller — 控制器包

> **版本**: v1.4 | **更新日期**: 2026-05-16

---

## 一、功能用途

`controller` 包是项目的 HTTP 请求入口层，负责接收前端请求、调用 Service 层处理业务逻辑、封装统一响应结果并返回。

### 核心作用

- **请求路由**：通过 `@RequestMapping` 等注解将 URL 映射到具体方法
- **参数校验**：通过 `@Validated` + 分组校验自动验证请求参数
- **响应封装**：使用 `Result` 类统一封装返回数据
- **异常委托**：业务异常直接抛出，由 `GlobalExceptionHandler` 统一处理

### 与其他模块的关系

```
前端请求 → Controller → Service → Mapper → 数据库
               ↓ 抛出异常
         GlobalExceptionHandler → Result.error()
               ↓ 正常返回
         Result.success()
```

- **调用 Service**：Controller 不直接操作数据库，而是调用 Service 层方法
- **使用 common**：通过 `Result.success()` / `Result.error()` 封装响应；抛出 `BusinessException` 子类
- **接收 entity**：请求参数使用 entity 类接收，配合 `@Validated` 分组校验
- **接收 dto**：复杂请求参数使用 DTO 类接收，如 `AlertRuleConfig`、`DeviceDataBatchDTO`

---

## 二、接口说明

### 2.1 DeviceController — 设备管理控制器

**完整类名**: `com.example.platform.controller.DeviceController`

**当前状态**：✅ 已实现 10 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/devices` | 查询设备列表 | `Result<List<Device>>` | 无 |
| `GET` | `/devices/{id}` | 根据 ID 查询设备 | `Result<Device>` | 无 |
| `POST` | `/devices` | 新增设备 | `Result<Void>` | `@Validated(CreateGroup.class)` |
| `PUT` | `/devices/{id}` | 更新设备信息 | `Result<Void>` | `@Validated(UpdateGroup.class)` |
| `DELETE` | `/devices/{id}` | 删除设备 | `Result<Void>` | 无 |
| `GET` | `/devices/online` | 查询在线设备 | `Result<List<Device>>` | 无 |
| `GET` | `/devices/search?name=` | 模糊搜索设备 | `Result<List<Device>>` | 手动校验 name 非空 |
| `PUT` | `/devices/{id}/status?status=` | 更新设备状态 | `Result<Void>` | 手动校验 status 范围 0-2 |
| `GET` | `/devices/page` | 分页查询设备 | `Result<Page<Device>>` | 可选 status、name 筛选 |
| `PUT` | `/devices/{id}/alert-config` | 更新设备级告警规则 | `Result<?>` | `@Validated` + `@RequestBody AlertRuleConfig` |

#### 接口详细说明

**GET /devices/{id}**

- 内部调用 `deviceService.getDeviceOrThrow(id)`，设备不存在时抛出 `DeviceNotFoundException`
- 设备存在时返回 `Result.success(device)`

**POST /devices**

- 使用 `@Validated(CreateGroup.class)` 触发创建场景的参数校验
- Service 层 `save()` 方法内部校验设备编码唯一性和设备类型存在性
- 校验失败时由 `GlobalExceptionHandler` 自动返回错误信息

**PUT /devices/{id}**

- 先调用 `deviceService.getDeviceOrThrow(id)` 确保设备存在（不存在会抛异常，由全局处理器返回 404）
- 防御性校验：请求体 ID 与路径 ID 不一致时返回 400 错误；一致或为空则强制以路径 ID 为准
- 乐观锁场景：`updateById` 返回 false 时返回 `"更新设备失败，数据可能已被他人修改"`

**DELETE /devices/{id}**

- 先调用 `deviceService.getDeviceOrThrow(id)` 确保设备存在
- 存在则执行 `removeById(id)` 删除

**GET /devices/page**

- 支持分页查询，参数：`current`（页码，默认1）、`size`（每页条数，默认10）
- 可选筛选：`status`（按状态筛选）、`name`（按设备名称模糊搜索）
- 结果按 `updateTime` 倒序排列

**PUT /devices/{id}/status**

- 手动校验 `status` 值范围（0-2），超出范围返回 400 错误

**PUT /devices/{id}/alert-config**

- 更新设备级别的告警规则配置，请求体为 `AlertRuleConfig` DTO
- 使用 `@Validated` 自动校验规则列表非空、字段完整性
- 配置通过 `JacksonTypeHandler` 以 JSON 格式存入 `alert_rule_config` 字段

#### 注入方式

```java
private final DeviceService deviceService;

public DeviceController(DeviceService deviceService) {
    this.deviceService = deviceService;
}
```

使用构造器注入（推荐），而非 `@Autowired` 字段注入。

#### 常用注解说明

| 注解 | 用途 | 示例 |
|------|------|------|
| `@RestController` | 标记为 REST 控制器 | 类级别注解 |
| `@RequestMapping` | 设置基础路径 | `@RequestMapping("/devices")` |
| `@GetMapping` | 映射 GET 请求 | `@GetMapping("/{id}")` |
| `@PostMapping` | 映射 POST 请求 | `@PostMapping` |
| `@PutMapping` | 映射 PUT 请求 | `@PutMapping("/{id}")` |
| `@DeleteMapping` | 映射 DELETE 请求 | `@DeleteMapping("/{id}")` |
| `@PathVariable` | 获取路径参数 | `@PathVariable Integer id` |
| `@RequestParam` | 获取查询参数 | `@RequestParam String name` |
| `@RequestBody` | 获取请求体 JSON | `@RequestBody Device device` |
| `@Validated` | 触发分组参数校验 | `@Validated(CreateGroup.class) @RequestBody Device device` |
| `@DateTimeFormat` | 格式化日期参数 | `@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")` |

### 2.2 DeviceDataController — 设备数据控制器

**完整类名**: `com.example.platform.controller.DeviceDataController`

**当前状态**：✅ 已实现 7 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `POST` | `/device-data` | 单条数据上报（含告警检查） | `Result<Void>` | `@Valid @RequestBody DeviceData` |
| `POST` | `/device-data/batch` | 批量数据上报（含告警检查） | `Result<Void>` | `@Valid @RequestBody DeviceDataBatchDTO` |
| `GET` | `/device-data/device/{deviceId}` | 分页查询设备历史数据 | `Result<Page<DeviceData>>` | 默认 current=1, size=20 |
| `GET` | `/device-data/device/{deviceId}/latest` | 查询设备最新一条数据 | `Result<DeviceData>` | 无 |
| `GET` | `/device-data/device/{deviceId}/range` | 按时间段查询数据 | `Result<List<DeviceData>>` | `startTime`, `endTime` 必填 |
| `GET` | `/device-data/device/{deviceId}/statistics` | 时间段内统计数据 | `Result<Map<String, Object>>` | `startTime`, `endTime` 必填 |
| `GET` | `/device-data/page` | 全局分页查询数据 | `Result<Page<DeviceData>>` | 可选 deviceId 筛选 |

#### 接口详细说明

**POST /device-data**

- 接收单条设备数据，调用 `deviceDataService.saveWithAlertCheck()` 保存并触发告警检查
- 设备不存在时抛出 `DeviceNotFoundException`

**POST /device-data/batch**

- 批量接收设备数据（1-1000条），逐条保存并触发告警检查
- 请求体使用 `DeviceDataBatchDTO` 封装

**GET /device-data/device/{deviceId}/range**

- 查询指定设备在时间段内的采集数据，参数格式 `yyyy-MM-dd HH:mm:ss`
- 先校验设备存在性，再查询数据

**GET /device-data/device/{deviceId}/statistics**

- 返回统计数据：`minVal`（最小值）、`maxVal`（最大值）、`avgVal`（平均值）、`count`（数据条数）
- 无数据时返回 null 值占位

#### 注入方式

```java
private final DeviceDataService deviceDataService;
private final DeviceService deviceService;
```

使用构造器注入，同时依赖 `DeviceDataService` 和 `DeviceService`（用于设备存在性校验）。

### 2.3 DeviceTypeController — 设备类型控制器

**完整类名**: `com.example.platform.controller.DeviceTypeController`

**当前状态**：✅ 已实现 1 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `PUT` | `/api/device-types/{id}/alert-config` | 更新设备类型级告警规则 | `Result<?>` | `@Validated @RequestBody AlertRuleConfig` |

#### 接口详细说明

**PUT /api/device-types/{id}/alert-config**

- 更新设备类型的默认告警规则配置
- 设备类型不存在时抛出 `DeviceTypeNotFound`
- 该配置作为设备级告警规则的降级方案（设备未配规则时使用类型级规则）

### 2.4 DictMetricController — 指标字典控制器

**完整类名**: `com.example.platform.controller.DictMetricController`

**当前状态**：✅ 已实现 5 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/api/metric/page` | 分页查询指标字典 | `Page<DictMetric>` | 默认 current=1, size=10 |
| `POST` | `/api/metric` | 新增指标 | `boolean` | `@RequestBody DictMetric` |
| `PUT` | `/api/metric` | 更新指标 | `boolean` | `@RequestBody DictMetric` |
| `DELETE` | `/api/metric/{id}` | 删除指标 | `boolean` | 无 |
| `POST` | `/api/metric/refreshCache` | 手动刷新缓存 | `String` | 无 |

#### 特点

- 使用 `@RequiredArgsConstructor` 替代手写构造器
- CRUD 操作自动维护内存缓存一致性
- 返回类型未使用 `Result<T>` 统一封装（直接返回 MyBatis-Plus 的 `Page` 和 `boolean`）

---

## 三、使用示例

### 示例 1：新增设备（带分组校验）

**请求**：

```
POST /api/devices
Content-Type: application/json

{
  "deviceName": "温度传感器A",
  "deviceCode": "SN-001",
  "typeId": 1,
  "status": 1,
  "location": "3号楼2层"
}
```

**成功响应**：

```json
{
  "code": 200,
  "message": "操作成功"
}
```

**校验失败响应**（设备名称为空）：

```json
{
  "code": 400,
  "message": "设备名称不能为空; 设备编码不能为空"
}
```

**业务异常响应**（设备编码已存在）：

```json
{
  "code": 4002,
  "message": "设备代码已存在:SN-001"
}
```

### 示例 2：查询设备（异常由全局处理器处理）

**请求**：

```
GET /api/devices/99999
```

**响应**（设备不存在，抛出 `DeviceNotFoundException`）：

```json
{
  "code": 4003,
  "message": "设备未找到:99999"
}
```

### 示例 3：分页查询设备

**请求**：

```
GET /api/devices/page?current=1&size=10&status=1&name=温度
```

**响应**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [...],
    "total": 15,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

### 示例 4：删除设备

**请求**：

```
DELETE /api/devices/5
```

**成功响应**：

```json
{
  "code": 200,
  "message": "操作成功"
}
```

**设备不存在响应**：

```json
{
  "code": 4003,
  "message": "设备未找到:5"
}
```

### 示例 5：更新设备状态

**请求**：

```
PUT /api/devices/1/status?status=0
```

**成功响应**：

```json
{
  "code": 200,
  "message": "操作成功"
}
```

**参数错误响应**（status 值超出范围）：

```json
{
  "code": 400,
  "message": "状态值无效，允许值：0-离线，1-在线，2-故障"
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 404 Not Found | 请求路径不匹配 | 注意项目配置了 `context-path: /api`，所有请求需加 `/api` 前缀 |
| 参数校验不生效 | `@Validated` 未触发 | 确保添加了 `spring-boot-starter-validation` 依赖，且指定了分组类 |
| 分组校验遗漏 | 未指定 `groups` 导致校验不执行 | `@Validated` 必须指定分组接口（如 `CreateGroup.class`），Entity 字段校验注解也需声明 `groups` |
| 设备不存在时行为 | `GET /devices/{id}` 和 `PUT /devices/{id}` 均通过 `getDeviceOrThrow` 抛出异常 | 异常由 `GlobalExceptionHandler` 统一处理，返回 HTTP 404 |
| 更新时 ID 不一致 | 路径 ID 与请求体 ID 不匹配 | 当请求体 ID 不为 null 且与路径 ID 不一致时返回 400 错误；否则强制以路径 ID 为准 |
| 更新失败 | `updateById` 返回 false | 可能是乐观锁冲突，返回 `"更新设备失败，数据可能已被他人修改"` |
| 跨域问题 | 前后端分离开发时可能遇到 | ✅ 已配置 `CorsConfig`，支持 localhost 和 127.0.0.1 跨域访问 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，Controller 为空类，文档为推荐设计 |
| v1.1 | 2026-05-08 | `[f88934db]` | DeviceController 已实现 7 个完整接口；新增 `@Validated` 分组校验说明；更新注入方式为构造器注入；异常处理改为委托 GlobalExceptionHandler |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | 新增 `GET /devices/page` 分页查询接口和 `DELETE /devices/{id}` 删除接口（共 9 个接口）；`getById` 和 `update` 改用 `getDeviceOrThrow`；`update` 逻辑优化（防御性ID校验+乐观锁提示）；跨域问题已由 CorsConfig 解决 |
| v1.3 | 2026-05-15 | `[05b8ff81]` | DeviceController 新增 `PUT /devices/{id}/alert-config` 告警配置接口（共 10 个）；新增 DeviceDataController（7 个接口：数据上报/批量上报/历史查询/最新数据/时间段查询/统计/分页）；新增 DeviceTypeController（1 个接口：类型级告警配置）；新增 DictMetricController（5 个接口：指标字典 CRUD + 缓存刷新） |
| v1.4 | 2026-05-16 | `[240739a]` | DeviceDataController/DeviceController 注释风格统一为块注释；DeviceDataController.getStatistics 新增设备存在性校验（委托 DeviceService.getDeviceOrThrow） |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-16。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
