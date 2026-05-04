# controller — 控制器包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`controller` 包是项目的 HTTP 请求入口层，负责接收前端请求、调用 Service 层处理业务逻辑、封装统一响应结果并返回。

### 核心作用

- **请求路由**：通过 `@RequestMapping` 等注解将 URL 映射到具体方法
- **参数校验**：接收并校验请求参数
- **响应封装**：使用 `Result` 类统一封装返回数据
- **异常处理**：捕获业务异常并返回友好的错误信息

### 与其他模块的关系

```
前端请求 → Controller → Service → Mapper → 数据库
                   ↑
              Result 封装响应
```

- **调用 Service**：Controller 不直接操作数据库，而是调用 Service 层方法
- **使用 common**：通过 `Result.success()` / `Result.error()` 封装响应
- **接收 entity**：请求参数可使用 entity 类接收

---

## 二、接口说明

### 2.1 DeviceController — 设备管理控制器

**完整类名**: `com.example.platform.controller.DeviceController`

> **当前状态**：控制器类已创建，但尚未实现具体接口。以下为推荐实现的接口设计。

#### 推荐接口设计

| HTTP 方法 | 路径 | 说明 | 返回类型 |
|-----------|------|------|----------|
| `GET` | `/devices` | 查询设备列表 | `Result<List<Device>>` |
| `GET` | `/devices/{id}` | 根据 ID 查询设备 | `Result<Device>` |
| `POST` | `/devices` | 新增设备 | `Result<Void>` |
| `PUT` | `/devices/{id}` | 更新设备信息 | `Result<Void>` |
| `DELETE` | `/devices/{id}` | 删除设备 | `Result<Void>` |
| `GET` | `/devices/online` | 查询在线设备 | `Result<List<Device>>` |
| `PUT` | `/devices/{id}/status` | 更新设备状态 | `Result<Void>` |

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

---

## 三、使用示例

### 示例 1：实现设备 CRUD 控制器

```java
@RestController
@RequestMapping("/devices")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping
    public Result<List<Device>> list() {
        return Result.success(deviceService.list());
    }

    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable Integer id) {
        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.error("设备不存在");
        }
        return Result.success(device);
    }

    @PostMapping
    public Result<Void> save(@RequestBody Device device) {
        boolean saved = deviceService.save(device);
        return saved ? Result.success() : Result.error("新增失败");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        boolean removed = deviceService.removeById(id);
        return removed ? Result.success() : Result.error("删除失败");
    }
}
```

### 示例 2：带参数校验的接口

```java
@PutMapping("/{id}/status")
public Result<Void> updateStatus(
        @PathVariable Integer id,
        @RequestParam Integer status) {
    if (status < 0 || status > 2) {
        return Result.error(400, "状态值无效，允许值：0-离线，1-在线，2-故障");
    }
    boolean updated = deviceService.updateStatus(id, status);
    return updated ? Result.success() : Result.error("更新失败");
}
```

### 示例 3：模糊查询接口

```java
@GetMapping("/search")
public Result<List<Device>> search(@RequestParam String name) {
    if (name == null || name.trim().isEmpty()) {
        return Result.error(400, "搜索关键词不能为空");
    }
    List<Device> devices = deviceService.findByNameLike(name);
    return Result.success(devices);
}
```

**请求示例**：

```
GET /api/devices/search?name=温度
```

**响应示例**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "deviceName": "1号楼温度传感器",
      "deviceCode": "TEMP-001",
      "status": 1,
      "location": "1号楼3层东侧"
    }
  ]
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 404 Not Found | 请求路径不匹配 | 注意项目配置了 `context-path: /api`，所有请求需加 `/api` 前缀 |
| 参数接收失败 | JSON 请求体未加 `@RequestBody` | POST/PUT 请求的 JSON 参数需使用 `@RequestBody` 注解 |
| 日期参数格式 | 前端传入日期字符串需匹配格式 | 默认格式为 `yyyy-MM-dd HH:mm:ss` |
| 跨域问题 | 前后端分离开发时可能遇到 | 在 config 包中添加 CORS 配置类 |
| Controller 为空 | 当前 `DeviceController` 尚未实现方法 | 参照上方示例实现具体接口 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
