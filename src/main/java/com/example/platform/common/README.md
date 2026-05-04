# common — 通用工具包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`common` 包提供项目级别的通用工具类和常量定义，确保全项目使用统一的响应格式和状态码规范。

### 核心作用

- **统一响应格式**：所有 API 接口返回相同结构的 JSON，方便前端统一处理
- **状态码管理**：集中定义业务状态码，避免硬编码

### 与其他模块的关系

```
Controller → 调用 Result.success() / Result.error() → 返回给前端
Service    → 返回业务数据 → Controller 封装为 Result
```

所有 Controller 层的接口都应使用 `Result` 类封装返回值，保证响应格式一致。

---

## 二、接口说明

### 2.1 Result<T> — 统一响应结果

**完整类名**: `com.example.platform.common.Result<T>`

统一响应结果封装类，泛型 `T` 为业务数据类型。

#### 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `code` | `Integer` | 是 | - | 状态码，200 表示成功，500 表示失败 |
| `message` | `String` | 是 | - | 提示信息 |
| `data` | `T` | 否 | `null` | 业务数据，失败时通常为 null |

#### 静态方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `Result.success()` | `Result<T>` | 返回成功结果（无数据） |
| `Result.success(T data)` | `Result<T>` | 返回成功结果（携带数据） |
| `Result.error(String message)` | `Result<T>` | 返回错误结果（状态码 500） |
| `Result.error(Integer code, String message)` | `Result<T>` | 返回错误结果（自定义状态码） |

#### 返回值格式

成功响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

失败响应：

```json
{
  "code": 500,
  "message": "设备不存在"
}
```

#### 错误处理机制

- 当业务逻辑出现异常时，调用 `Result.error()` 封装错误信息
- 建议在 Controller 层使用 `try-catch` 捕获异常并返回 `Result.error()`
- 后续可结合 `@ControllerAdvice` 全局异常处理器统一处理

### 2.2 ResultCode — 状态码常量

**完整类名**: `com.example.platform.common.ResultCode`

> 当前为空类，预留用于集中定义业务状态码常量。后续可扩展为：

```java
public class ResultCode {
    public static final int SUCCESS = 200;
    public static final int ERROR = 500;
    public static final int NOT_FOUND = 404;
    public static final int PARAM_ERROR = 400;
}
```

---

## 三、使用示例

### 示例 1：查询成功返回数据

```java
@GetMapping("/{id}")
public Result<Device> getDevice(@PathVariable Integer id) {
    Device device = deviceService.getById(id);
    if (device == null) {
        return Result.error("设备不存在");
    }
    return Result.success(device);
}
```

**输出示例**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "deviceName": "1号楼温度传感器",
    "deviceCode": "TEMP-001",
    "status": 1,
    "location": "1号楼3层东侧"
  }
}
```

### 示例 2：操作成功无数据返回

```java
@DeleteMapping("/{id}")
public Result<Void> deleteDevice(@PathVariable Integer id) {
    boolean removed = deviceService.removeById(id);
    if (!removed) {
        return Result.error("删除失败，设备不存在");
    }
    return Result.success();
}
```

**输出示例**：

```json
{
  "code": 200,
  "message": "操作成功"
}
```

### 示例 3：带自定义状态码的错误返回

```java
@GetMapping("/{id}")
public Result<Device> getDevice(@PathVariable Integer id) {
    if (id <= 0) {
        return Result.error(400, "参数错误：ID 必须为正整数");
    }
    Device device = deviceService.getById(id);
    if (device == null) {
        return Result.error(404, "设备不存在");
    }
    return Result.success(device);
}
```

**输出示例**：

```json
{
  "code": 404,
  "message": "设备不存在"
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| JSON 中 null 字段不显示 | Jackson 配置了 `default-property-inclusion: non_null` | 这是正常行为，减少数据传输量；如需显示 null 字段，修改 `application.yaml` 中的 Jackson 配置 |
| ResultCode 类为空 | 当前状态码直接使用数字 | 建议后续将状态码定义为常量，避免硬编码 |
| 日期格式 | 默认 `yyyy-MM-dd HH:mm:ss` | 由 `application.yaml` 中 `spring.jackson.date-format` 控制 |
| 序列化异常 | 实体类需实现 `Serializable` 接口 | 所有 entity 类已实现 `Serializable`，正常使用即可 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
