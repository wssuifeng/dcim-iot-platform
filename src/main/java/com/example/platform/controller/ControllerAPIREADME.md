# controller — 控制器包

> **版本**: v3.0 | **更新日期**: 2026-05-24

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
- **接收 dto**：复杂请求参数使用 DTO 类接收，如 `AlertRuleConfig`、`DeviceDataBatchDTO`、`LoginDTO`、`BatchHandleAlertDTO`、`PasswordResetDTO`

---

## 二、接口说明

### 2.1 DeviceController — 设备管理控制器

**完整类名**: `com.example.platform.controller.DeviceController`

**URL前缀**: `/devices`（不带 `/api`，因为 `context-path` 已配置 `/api`）

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
| `PUT` | `/devices/{id}/alert-config` | 更新设备级告警规则 | `Result<Void>` | `@Validated` + `@RequestBody AlertRuleConfig` |

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
- 分页查询已下沉到 Service 层 `pageByCondition` 方法，结果按 `updateTime` 倒序排列

**PUT /devices/{id}/status**

- 手动校验 `status` 值范围（0-2），超出范围返回 400 错误

**PUT /devices/{id}/alert-config**

- 更新设备级别的告警规则配置，请求体为 `AlertRuleConfig` DTO
- 使用 `@Validated` 自动校验规则列表非空、字段完整性
- 配置通过 `JacksonTypeHandler` 以 JSON 格式存入 `alert_rule_config` 字段
- 返回类型已改为 `Result<Void>`（v2.0 修复）

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

**URL前缀**: `/device-data`

**当前状态**：✅ 已实现 7 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `POST` | `/device-data` | 单条数据上报（含告警检查） | `Result<Void>` | `@Validated(CreateGroup.class) @RequestBody DeviceData` |
| `POST` | `/device-data/batch` | 批量数据上报（含告警检查） | `Result<Void>` | `@Validated @RequestBody DeviceDataBatchDTO` |
| `GET` | `/device-data/device/{deviceId}` | 分页查询设备历史数据 | `Result<Page<DeviceData>>` | 默认 current=1, size=20 |
| `GET` | `/device-data/device/{deviceId}/latest` | 查询设备最新一条数据 | `Result<DeviceData>` | 无 |
| `GET` | `/device-data/device/{deviceId}/range` | 按时间段查询数据 | `Result<List<DeviceData>>` | `startTime`, `endTime` 必填 |
| `GET` | `/device-data/device/{deviceId}/statistics` | 时间段内统计数据 | `Result<Map<String, Object>>` | `startTime`, `endTime` 必填 |
| `GET` | `/device-data/page` | 全局分页查询数据 | `Result<Page<DeviceData>>` | 可选 deviceId 筛选 |

#### 接口详细说明

**POST /device-data**

- 接收单条设备数据，调用 `deviceDataService.saveWithAlertCheck()` 保存并触发告警检查
- 已改用 `@Validated(CreateGroup.class)` 替代旧版 `@Valid`（v2.0 修复）
- 设备不存在时抛出 `DeviceNotFoundException`

**POST /device-data/batch**

- 批量接收设备数据（1-1000条），逐条保存并触发告警检查
- 请求体使用 `DeviceDataBatchDTO` 封装
- 批量上报已下沉到 Service 层 `saveBatchWithAlertCheck` 方法（v2.0 修复）

**GET /device-data/device/{deviceId}**

- 分页查询已下沉到 Service 层 `pageByDeviceId` 方法（v2.0 修复）

**GET /device-data/page**

- 全局分页查询已下沉到 Service 层 `pageByCondition` 方法（v2.0 修复）

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

**URL前缀**: `/device-types`（已去掉 `/api` 前缀，避免与 `context-path` 重复）

**当前状态**：✅ 已实现 1 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `PUT` | `/device-types/{id}/alert-config` | 更新设备类型级告警规则 | `Result<Void>` | `@Validated @RequestBody AlertRuleConfig` |

#### 接口详细说明

**PUT /device-types/{id}/alert-config**

- 更新设备类型的默认告警规则配置
- 设备类型不存在时抛出 `DeviceTypeNotFound`
- 该配置作为设备级告警规则的降级方案（设备未配规则时使用类型级规则）
- 返回类型已改为 `Result<Void>`（v2.0 修复，旧版为 `Result<?>`）

### 2.4 DictMetricController — 指标字典控制器

**完整类名**: `com.example.platform.controller.DictMetricController`

**URL前缀**: `/metric`（已去掉 `/api` 前缀，避免与 `context-path` 重复）

**当前状态**：✅ 已实现 5 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/metric/page` | 分页查询指标字典 | `Result<Page<DictMetric>>` | 默认 current=1, size=10 |
| `POST` | `/metric` | 新增指标 | `Result<Void>` | `@RequestBody DictMetric` |
| `PUT` | `/metric` | 更新指标 | `Result<Void>` | `@RequestBody DictMetric` |
| `DELETE` | `/metric/{id}` | 删除指标 | `Result<Void>` | 无 |
| `POST` | `/metric/cache/refresh` | 手动刷新缓存 | `Result<Void>` | 无 |

#### 特点

- 使用 `@RequiredArgsConstructor` 替代手写构造器
- CRUD 操作自动维护内存缓存一致性
- 所有接口已统一使用 `Result<T>` 响应包装（v2.0 修复，旧版直接返回 `Page` 和 `boolean`）
- `refreshCache` URL 已改为 `POST /cache/refresh`（v2.0 修复，旧版为 `POST /refreshCache`）

### 2.5 AlertController — 告警管理控制器

**完整类名**: `com.example.platform.controller.AlertController`

**URL前缀**: `/alerts`

**当前状态**：✅ 已实现 7 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/alerts` | 分页查询告警列表 | `Result<Page<Alert>>` | 可选 deviceId、isHandled、alertLevel、startTime、endTime 筛选 |
| `GET` | `/alerts/{id}` | 根据 ID 查询告警 | `Result<Alert>` | 无 |
| `GET` | `/alerts/unhandled` | 查询所有未处理告警 | `Result<List<Alert>>` | 无 |
| `PUT` | `/alerts/batch-handle` | 批量处理告警 | `Result<Integer>` | `@Validated @RequestBody BatchHandleAlertDTO` |
| `GET` | `/alerts/stats/device/{deviceId}` | 按设备统计告警 | `Result<Map<String, Object>>` | 无 |
| `GET` | `/alerts/stats/time-range` | 按时间段统计告警 | `Result<Map<String, Object>>` | startTime、endTime 必填 |
| `GET` | `/alerts/stats/device-ranking` | 设备告警排名 | `Result<List<Map<String, Object>>>` | startTime、endTime 必填 |

#### 接口详细说明

**GET /alerts**

- 多条件分页查询，参数：`current`（默认1）、`size`（默认10）、`deviceId`、`isHandled`、`alertLevel`、`startTime`、`endTime`
- 时间参数格式：`yyyy-MM-dd HH:mm:ss`
- 查询逻辑已下沉到 Service 层 `pageByCondition` 方法

**PUT /alerts/batch-handle**

- 请求体为 `BatchHandleAlertDTO`，包含 `ids`（告警ID列表）和 `handleUser`（处理人）
- 返回成功处理的告警数量

**GET /alerts/stats/device/{deviceId}**

- 返回指定设备的告警统计数据（总数、已处理数、未处理数、各级别数量）

**GET /alerts/stats/time-range**

- 返回指定时间段内的告警统计数据

**GET /alerts/stats/device-ranking**

- 返回指定时间段内各设备的告警数量排名

#### 注入方式

```java
private final AlertService alertService;
```

### 2.6 AuthController — 认证控制器

**完整类名**: `com.example.platform.controller.AuthController`

**URL前缀**: `/auth`

**当前状态**：✅ 已实现 1 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `POST` | `/auth/login` | 用户登录 | `Result<LoginResponseDTO>` | `@Validated @RequestBody LoginDTO` |

#### 接口详细说明

**POST /auth/login**

- 请求体为 `LoginDTO`，包含 `username` 和 `password`
- 内部调用 `authService.login()` 验证凭据并返回 JWT Token
- 自动提取客户端真实 IP 地址（支持 `X-Forwarded-For`、`X-Real-IP` 代理转发场景）
- 登录失败时抛出 `UserNotFoundException` 或 `AccountLockedException`
- 此端点在 SecurityConfig 中配置为 `permitAll()`，无需认证

#### IP 提取逻辑

```
1. X-Forwarded-For 请求头（取第一个IP，多级代理时用逗号分隔）
2. X-Real-IP 请求头（Nginx 等反向代理设置）
3. request.getRemoteAddr()（直连场景）
```

#### 注入方式

```java
private final AuthService authService;
```

### 2.7 SysUserController — 用户管理控制器

**完整类名**: `com.example.platform.controller.SysUserController`

**URL前缀**: `/users`

**当前状态**：✅ 已实现 7 个接口

**权限要求**：ADMIN 角色（SecurityConfig 配置 `/users/**` 仅 ADMIN 可访问）

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/users/page` | 分页查询用户列表 | `Result<Page<SysUser>>` | 可选 keyword 搜索 |
| `GET` | `/users/search?username=` | 按用户名查找 | `Result<SysUser>` | 无 |
| `GET` | `/users/{id}` | 根据 ID 查询用户 | `Result<SysUser>` | 无 |
| `POST` | `/users` | 创建用户 | `Result<Void>` | `@Validated(CreateGroup.class)` |
| `PUT` | `/users/{id}` | 更新用户信息 | `Result<Void>` | `@Validated(UpdateGroup.class)` |
| `PUT` | `/users/{id}/password` | 重置密码 | `Result<Void>` | `@Validated @RequestBody PasswordResetDTO` |
| `PUT` | `/users/{id}/status?status=` | 启用/停用账号 | `Result<Void>` | 手动校验 status 范围 0-1 |
| `DELETE` | `/users/{id}` | 删除用户 | `Result<Void>` | 无 |

#### 接口详细说明

**GET /users/page**

- 支持按 `keyword` 模糊搜索用户名
- 参数：`current`（默认1）、`size`（默认10）、`keyword`（可选）

**GET /users/search**

- 按用户名精确查找，未找到时抛出 `UserNotFoundException`

**POST /users**

- 创建用户，密码由 Service 层 BCrypt 加密后存储
- 用户名重复时抛出 `UsernameAlreadyExistsException`

**PUT /users/{id}**

- 更新用户基本信息（昵称、角色），不支持密码变更
- 防御性校验：请求体 ID 与路径 ID 不一致时返回 400 错误

**PUT /users/{id}/password**

- 重置密码，新密码由 Service 层 BCrypt 加密后存储
- 请求体为 `PasswordResetDTO`

**PUT /users/{id}/status**

- 启用（0）或停用（1）账号，超出范围返回 400 错误

**DELETE /users/{id}**

- 先调用 `getUserOrThrow(id)` 确保用户存在，再执行删除

#### 注入方式

```java
private final SysUserService sysUserService;
```

### 2.8 SysAuditLogController — 审计日志控制器

**完整类名**: `com.example.platform.controller.SysAuditLogController`

**URL前缀**: `/audit-logs`

**当前状态**：✅ 已实现 2 个接口

**权限要求**：ADMIN 或 OPERATOR 角色可查询；仅 ADMIN 可执行 DELETE 清理

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/audit-logs/page` | 分页查询审计日志 | `Result<Page<SysAuditLog>>` | 可选 userId、operation、startTime、endTime 筛选 |
| `DELETE` | `/audit-logs/cleanup?beforeTime=` | 清理过期日志 | `Result<Integer>` | beforeTime 必填 |

#### 接口详细说明

**GET /audit-logs/page**

- 多条件分页查询，参数：`current`（默认1）、`size`（默认10）、`userId`、`operation`、`startTime`、`endTime`
- 时间参数格式：`yyyy-MM-dd HH:mm:ss`

**DELETE /audit-logs/cleanup**

- 清理指定时间之前的过期审计日志（数据保留策略）
- 返回删除的记录条数
- 此端点受 SecurityConfig 中 `HttpMethod.DELETE → hasRole("ADMIN")` 规则控制，仅 ADMIN 可执行

#### 注入方式

```java
private final SysAuditLogService sysAuditLogService;
```

---

### 2.9 DashboardController — 仪表盘统计控制器（v4.0 新增）

**完整类名**: `com.example.platform.controller.DashboardController`
**路径前缀**: `/api/dashboard`
**注解**: `@RestController` + `@RequestMapping("/api/dashboard")`

#### 接口清单

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/stats` | 获取仪表盘聚合统计（设备状态分布 + 告警统计） | 已认证 |

#### 请求示例

```
GET /api/dashboard/stats
Authorization: Bearer <token>
```

#### 响应示例

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalDevices": 42,
    "onlineDevices": 35,
    "statusDistribution": {
      "1": 35,
      "0": 7
    },
    "totalAlerts": 128,
    "unhandledAlerts": 5
  }
}
```

#### 依赖关系

```
DashboardController
    → DashboardStatsService.getDashboardStats()
        → CacheService (Redis 缓存)
        → DeviceMapper / AlertMapper (数据库)
```

**缓存策略**：使用 `stats:dashboard` Key（TTL 30s），互斥锁 `stats:dashboard:lock` 防缓存击穿

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
| 类级 @Validated | 所有 Controller 已添加类级 `@Validated` | 类级注解提供方法级参数校验支持（如 `@PathVariable`、`@RequestParam` 上的约束注解） |
| 统一响应包装 | `DictMetricController` 已修复统一响应包装 | v2.0 已将所有接口返回类型统一为 `Result<T>`，不再直接返回 `boolean` 或 `Page` |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，Controller 为空类，文档为推荐设计 |
| v1.1 | 2026-05-08 | `[f88934db]` | DeviceController 已实现 7 个完整接口；新增 `@Validated` 分组校验说明；更新注入方式为构造器注入；异常处理改为委托 GlobalExceptionHandler |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | 新增 `GET /devices/page` 分页查询接口和 `DELETE /devices/{id}` 删除接口（共 9 个接口）；`getById` 和 `update` 改用 `getDeviceOrThrow`；`update` 逻辑优化（防御性ID校验+乐观锁提示）；跨域问题已由 CorsConfig 解决 |
| v1.3 | 2026-05-15 | `[05b8ff81]` | DeviceController 新增 `PUT /devices/{id}/alert-config` 告警配置接口（共 10 个）；新增 DeviceDataController（7 个接口：数据上报/批量上报/历史查询/最新数据/时间段查询/统计/分页）；新增 DeviceTypeController（1 个接口：类型级告警配置）；新增 DictMetricController（5 个接口：指标字典 CRUD + 缓存刷新） |
| v1.4 | 2026-05-16 | `[240739a]` | DeviceDataController/DeviceController 注释风格统一为块注释；DeviceDataController.getStatistics 新增设备存在性校验（委托 DeviceService.getDeviceOrThrow） |
| v2.0 | 2026-05-17 | — | 代码审查修复：DeviceController.updateAlertConfig 返回类型改为 `Result<Void>`；DeviceDataController.reportData 改用 `@Validated(CreateGroup.class)`；批量上报下沉到 Service 层 `saveBatchWithAlertCheck`；分页查询下沉到 Service 层 `pageByCondition`/`pageByDeviceId`；DeviceTypeController URL 前缀去掉 `/api`，返回类型改为 `Result<Void>`；DictMetricController URL 前缀去掉 `/api`，所有接口统一 `Result<T>` 响应包装，refreshCache URL 改为 `POST /cache/refresh`；所有 Controller 添加类级 `@Validated` |
| v3.0 | 2026-05-24 | — | 新增 AlertController（7个接口：告警分页查询/详情/未处理列表/批量处理/设备统计/时间段统计/设备排名）；新增 AuthController（1个接口：登录认证，支持代理IP提取）；新增 SysUserController（7个接口：用户分页查询/搜索/详情/创建/更新/密码重置/状态管理/删除，仅ADMIN可访问）；新增 SysAuditLogController（2个接口：审计日志分页查询/过期清理） |
| v4.0 | 2026-05-28 | `3d96056` | 新增 DashboardController（1个接口：仪表盘聚合统计，含设备状态分布 + 告警统计，互斥锁防缓存击穿） |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
