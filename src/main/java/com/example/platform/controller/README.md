# controller — 控制器包

> **版本**: v1.1 | **更新日期**: 2026-05-08

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

---

## 二、接口说明

### 2.1 DeviceController — 设备管理控制器

**完整类名**: `com.example.platform.controller.DeviceController`

**当前状态**：✅ 已实现 7 个接口

#### 接口列表

| HTTP 方法 | 路径 | 说明 | 返回类型 | 参数校验 |
|-----------|------|------|----------|----------|
| `GET` | `/devices` | 查询设备列表 | `Result<List<Device>>` | 无 |
| `GET` | `/devices/{id}` | 根据 ID 查询设备 | `Result<Device>` | 无 |
| `POST` | `/devices` | 新增设备 | `Result<Void>` | `@Validated(CreateGroup.class)` |
| `PUT` | `/devices/{id}` | 更新设备信息 | `Result<Void>` | `@Validated(UpdateGroup.class)` |
| `GET` | `/devices/online` | 查询在线设备 | `Result<List<Device>>` | 无 |
| `GET` | `/devices/search?name=` | 模糊搜索设备 | `Result<List<Device>>` | 手动校验 name 非空 |
| `PUT` | `/devices/{id}/status?status=` | 更新设备状态 | `Result<Void>` | 手动校验 status 范围 0-2 |

#### 接口详细说明

**GET /devices/{id}**

- 设备不存在时抛出 `DeviceNotFoundException`，由全局异常处理器返回 HTTP 404
- 设备存在时返回 `Result.success(device)`

**POST /devices**

- 使用 `@Validated(CreateGroup.class)` 触发创建场景的参数校验
- Service 层 `save()` 方法内部校验设备编码唯一性和设备类型存在性
- 校验失败时由 `GlobalExceptionHandler` 自动返回错误信息

**PUT /devices/{id}**

- 使用 `@Validated(UpdateGroup.class)` 触发更新场景的参数校验
- 额外校验：路径 ID 与请求体 ID 必须一致
- 设备不存在时返回 `Result.error("设备不存在，无法更新")`

**PUT /devices/{id}/status**

- 手动校验 `status` 值范围（0-2），超出范围返回 400 错误

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
| `@PathVariable` | 获取路径参数 | `@PathVariable Integer id` |
| `@RequestParam` | 获取查询参数 | `@RequestParam String name` |
| `@RequestBody` | 获取请求体 JSON | `@RequestBody Device device` |
| `@Validated` | 触发分组参数校验 | `@Validated(CreateGroup.class) @RequestBody Device device` |

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
  "message": "设备未找到:设备不存在"
}
```

### 示例 3：更新设备状态

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
| 设备不存在时行为 | `GET /devices/{id}` 抛出异常而非返回 `Result.error()` | 这是当前设计选择：异常由 `GlobalExceptionHandler` 统一处理，返回 HTTP 404 |
| 更新时 ID 不一致 | 路径 ID 与请求体 ID 不匹配 | Controller 会校验两者一致性，不一致时返回 400 错误 |
| 跨域问题 | 前后端分离开发时可能遇到 | 在 config 包中添加 CORS 配置类 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，Controller 为空类，文档为推荐设计 |
| v1.1 | 2026-05-08 | `[f88934db]` | DeviceController 已实现 7 个完整接口；新增 `@Validated` 分组校验说明；更新注入方式为构造器注入；异常处理改为委托 GlobalExceptionHandler |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-08。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
