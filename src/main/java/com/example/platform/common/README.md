# common — 通用工具包

> **版本**: v1.2 | **更新日期**: 2026-05-09

---

## 一、功能用途

`common` 包提供项目级别的通用工具类、常量定义、异常体系和校验分组，确保全项目使用统一的响应格式、异常处理规范和参数校验策略。

### 核心作用

- **统一响应格式**：所有 API 接口返回相同结构的 JSON，方便前端统一处理
- **异常体系**：定义业务异常基类和具体异常，配合全局异常处理器自动拦截并返回标准错误格式
- **校验分组**：通过分组接口区分创建和更新场景的参数校验规则
- **状态码管理**：集中定义业务状态码，避免硬编码

### 与其他模块的关系

```
Controller → 调用 Result.success() / Result.error() → 返回给前端
Controller → 抛出 BusinessException 子类 → GlobalExceptionHandler 拦截 → 返回 Result.error()
Entity     → 使用 @Validated(CreateGroup.class) / @Validated(UpdateGroup.class) → 触发分组校验
Service    → 抛出 BusinessException 子类 → 交由全局异常处理器统一处理
```

### 包结构

```
common/
├── Result.java                        # 统一响应结果封装
├── ResultCode.java                    # 状态码常量（预留）
├── exception/                         # 异常体系
│   ├── BusinessException.java         # 业务异常基类
│   ├── DeviceCodeAlreadyExistsException.java  # 设备编码重复异常
│   ├── DeviceNotFoundException.java   # 设备未找到异常
│   ├── DeviceTypeNotFound.java        # 设备类型不存在异常
│   └── GlobalExceptionHandler.java    # 全局异常处理器
└── group/                             # 校验分组
    ├── CreateGroup.java               # 创建操作校验分组
    └── UpdateGroup.java               # 更新操作校验分组
```

---

## 二、接口说明

### 2.1 Result\<T\> — 统一响应结果

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
  "code": 4001,
  "message": "设备类型id不存在:99"
}
```

### 2.2 BusinessException — 业务异常基类

**完整类名**: `com.example.platform.common.exception.BusinessException`

所有自定义业务异常的基类，继承 `RuntimeException`。

#### 构造方法

| 构造方法 | 参数 | 默认状态码 | 说明 |
|----------|------|-----------|------|
| `BusinessException(String message)` | `message`: 错误描述 | 400 | 通用业务异常 |
| `BusinessException(int code, String message)` | `code`: 自定义状态码, `message`: 错误描述 | 由参数指定 | 带自定义状态码的业务异常 |

#### 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 业务状态码（通过 `@Getter` 提供 getter 方法） |

### 2.3 具体业务异常类

| 异常类 | 状态码 | 触发场景 | 错误消息格式 |
|--------|--------|----------|-------------|
| `DeviceCodeAlreadyExistsException` | 4002 | 新增设备时编码已存在 | `"设备代码已存在:{deviceCode}"` |
| `DeviceNotFoundException` | 4003 | 查询/操作不存在的设备 | `"设备未找到:{id}"` |
| `DeviceTypeNotFound` | 4001 | 关联的设备类型不存在 | `"设备类型id不存在:{typeId}"` |

#### 构造方法

| 异常类 | 构造参数 |
|--------|----------|
| `DeviceCodeAlreadyExistsException` | `String deviceCode` |
| `DeviceNotFoundException` | `Integer id` |
| `DeviceTypeNotFound` | `Integer typeId` |

### 2.4 GlobalExceptionHandler — 全局异常处理器

**完整类名**: `com.example.platform.common.exception.GlobalExceptionHandler`

通过 `@RestControllerAdvice` 注解，统一拦截 Controller 层抛出的异常，自动返回标准 JSON 格式的错误信息，无需在 Controller 中手动 try-catch。

#### 处理的异常类型

| 异常类型 | HTTP 状态码 | 返回 code | 说明 |
|----------|------------|-----------|------|
| `MethodArgumentNotValidException` | 400 | 400 | `@Valid` 校验不通过时自动抛出，拼接所有字段错误信息 |
| `BusinessException` | 400 | 自定义 | 通用业务异常 |
| `DeviceCodeAlreadyExistsException` | 400 | 4002 | 设备编码重复 |
| `DeviceTypeNotFound` | 400 | 4001 | 设备类型不存在 |
| `DeviceNotFoundException` | 404 | 4003 | 设备未找到 |
| `Exception`（兜底） | 500 | 500 | 所有未匹配的异常，返回模糊错误信息防止泄露系统细节 |

#### 处理流程

```
Controller 抛出异常
    ↓
GlobalExceptionHandler 匹配对应 @ExceptionHandler 方法
    ↓
记录日志（warn/error 级别）
    ↓
返回 Result.error(code, message) 统一格式
```

### 2.5 校验分组接口

**完整类名**: `com.example.platform.common.group.CreateGroup` / `UpdateGroup`

空接口，用作 `@Validated` 的分组校验参数，区分创建和更新场景的不同校验规则。

| 分组接口 | 用途 | 典型使用 |
|----------|------|----------|
| `CreateGroup` | 创建操作时的校验规则 | `@Validated(CreateGroup.class) @RequestBody Device device` |
| `UpdateGroup` | 更新操作时的校验规则 | `@Validated(UpdateGroup.class) @RequestBody Device device` |

在 Entity 类中，通过 `groups` 属性指定校验注解生效的分组：

```java
@NotNull(message = "设备ID不能为空", groups = UpdateGroup.class)
private Integer id;  // 更新时必须传 ID，创建时不需要

@NotBlank(message = "设备名称不能为空", groups = {CreateGroup.class, UpdateGroup.class})
private String deviceName;  // 创建和更新时都必填
```

### 2.6 ResultCode — 状态码常量

**完整类名**: `com.example.platform.common.ResultCode`

> 当前为空类，预留用于集中定义业务状态码常量。项目当前使用的状态码如下：

| 状态码 | 含义 | 使用位置 |
|--------|------|----------|
| 200 | 操作成功 | `Result.success()` |
| 400 | 参数校验失败 / 通用业务异常 | `GlobalExceptionHandler` |
| 404 | 资源未找到 | `DeviceNotFoundException` |
| 500 | 服务器内部错误 | 兜底异常处理 |
| 4001 | 设备类型不存在 | `DeviceTypeNotFound` |
| 4002 | 设备编码已存在 | `DeviceCodeAlreadyExistsException` |
| 4003 | 设备未找到 | `DeviceNotFoundException` |

---

## 三、使用示例

### 示例 1：在 Service 中抛出业务异常

```java
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    public void deviceCodeExists(String deviceCode) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);
        if (count(wrapper) > 0) {
            throw new DeviceCodeAlreadyExistsException(deviceCode);
        }
    }
}
```

**效果**：无需在 Controller 中 try-catch，`GlobalExceptionHandler` 自动拦截并返回：

```json
{
  "code": 4002,
  "message": "设备代码已存在:SN-001"
}
```

### 示例 2：在 Controller 中使用分组校验

```java
@PostMapping
public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
    boolean saved = deviceService.save(device);
    return saved ? Result.success() : Result.error("新增设备失败");
}

@PutMapping("/{id}")
public Result<Void> update(@PathVariable Integer id,
                           @Validated(UpdateGroup.class) @RequestBody Device device) {
    // ...
}
```

**校验失败时自动返回**：

```json
{
  "code": 400,
  "message": "设备名称不能为空; 设备编码不能为空"
}
```

### 示例 3：在 Controller 中直接抛出异常

```java
@GetMapping("/{id}")
public Result<Device> getById(@PathVariable Integer id) {
    return Result.success(deviceService.getDeviceOrThrow(id));
}
```

**效果**：`GlobalExceptionHandler` 拦截后返回 HTTP 404：

```json
{
  "code": 4003,
  "message": "设备未找到:9999"
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| JSON 中 null 字段不显示 | Jackson 配置了 `default-property-inclusion: non_null` | 这是正常行为，减少数据传输量；如需显示 null 字段，修改 `application.yaml` 中的 Jackson 配置 |
| 校验不生效 | `@Valid` / `@Validated` 未触发校验 | 确保添加了 `spring-boot-starter-validation` 依赖，且 Controller 参数上有 `@Validated` 注解 |
| 分组校验不生效 | 未指定 `groups` 或 `@Validated` 中未指定分组 | `@Validated` 必须指定分组接口类，Entity 字段的校验注解也必须通过 `groups` 声明所属分组 |
| 异常处理器未拦截 | 异常不在 Controller 层抛出 | `@RestControllerAdvice` 仅拦截 Controller 层的异常；Service 中的异常需传播到 Controller 才能被拦截 |
| 兜底异常信息模糊 | `Exception` 兜底处理器返回"服务器内部错误" | 这是安全设计，防止泄露堆栈信息；详细错误信息记录在服务器日志中 |
| ResultCode 类为空 | 当前状态码分散在各异常类中 | 建议后续将状态码统一定义为 `ResultCode` 常量，避免硬编码 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，包含 Result 和 ResultCode 文档 |
| v1.1 | 2026-05-08 | `[f88934db]` | 新增 exception 子包（BusinessException、3个具体异常类、GlobalExceptionHandler）和 group 子包（CreateGroup、UpdateGroup）文档；更新状态码列表 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | DeviceNotFoundException 构造参数从 `String deviceName` 改为 `Integer id`；更新使用示例为 `getDeviceOrThrow` 调用方式 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-09。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
