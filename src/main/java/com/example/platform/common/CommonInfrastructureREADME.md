# common — 通用工具包

> **版本**: v3.0 | **更新日期**: 2026-05-24

---

## 一、功能用途

`common` 包提供项目级别的通用工具类、常量定义、异常体系和校验分组，确保全项目使用统一的响应格式、异常处理规范和参数校验策略。

### 核心作用

- **统一响应格式**：所有 API 接口返回相同结构的 JSON，方便前端统一处理
- **异常体系**：定义业务异常基类和具体异常，配合全局异常处理器自动拦截并返回标准错误格式
- **校验分组**：通过分组接口区分创建和更新场景的参数校验规则
- **状态码管理**：集中定义业务状态码，避免硬编码
- **JWT工具**：提供Token生成、解析与验证能力

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
├── ResultCode.java                    # 状态码常量
├── exception/                         # 异常体系
│   ├── BusinessException.java         # 业务异常基类
│   ├── DeviceCodeAlreadyExistsException.java  # 设备编码重复异常
│   ├── DeviceNotFoundException.java   # 设备未找到异常
│   ├── DeviceTypeNotFound.java        # 设备类型不存在异常
│   ├── DeviceTypeCodeAlreadyExistsException.java  # 设备类型编码重复异常
│   ├── UserNotFoundException.java     # 用户未找到异常
│   ├── UsernameAlreadyExistsException.java  # 用户名已存在异常
│   ├── AccountLockedException.java    # 账号锁定异常
│   └── GlobalExceptionHandler.java    # 全局异常处理器
├── util/                              # 工具类
│   └── JwtUtil.java                   # JWT工具类
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
| `code` | `private final int` | 业务状态码（通过 `@Getter` 提供 getter 方法） |

> **v2.0 修正**：`code` 字段已改为 `private final`，确保不可变性和线程安全。

### 2.3 具体业务异常类

| 异常类 | 状态码 | HTTP 状态码 | 触发场景 | 错误消息格式 |
|--------|--------|------------|----------|-------------|
| `DeviceCodeAlreadyExistsException` | 4002 | 400 | 新增设备时编码已存在 | `"设备编码已存在:{deviceCode}"` |
| `DeviceNotFoundException` | 4003 | 404 | 查询/操作不存在的设备 | `"设备不存在:{id}"` |
| `DeviceTypeNotFound` | 4001 | 404 | 关联的设备类型不存在 | `"设备类型不存在:{id}"` |
| `DeviceTypeCodeAlreadyExistsException` | 4051 | 400 | 新增/更新设备类型时编码已存在 | `"设备类型编码已存在:{typeCode}"` |
| `UserNotFoundException` | 4004 | 404 | 查询/操作不存在的用户 | `"用户不存在:{id/username}"` |
| `UsernameAlreadyExistsException` | 4005 | 400 | 创建用户时用户名已存在 | `"用户名已存在:{username}"` |
| `AccountLockedException` | 4006 | 400 | 登录时账号处于锁定状态 | `"账号已被锁定，请于 {lockTime} 后重试"` |

> **v2.0 修正**：`DeviceTypeNotFound` 异常返回 HTTP 状态码从 400 改为 404，语义更准确（资源不存在）。

#### 构造方法

| 异常类 | 构造参数 |
|--------|----------|
| `DeviceCodeAlreadyExistsException` | `String deviceCode` |
| `DeviceNotFoundException` | `Integer id` |
| `DeviceTypeNotFound` | `Integer id` |
| `DeviceTypeCodeAlreadyExistsException` | `String typeCode` |
| `UserNotFoundException` | `Integer id` 或 `String username` |
| `UsernameAlreadyExistsException` | `String username` |
| `AccountLockedException` | `LocalDateTime lockTime` |

### 2.4 GlobalExceptionHandler — 全局异常处理器

**完整类名**: `com.example.platform.common.exception.GlobalExceptionHandler`

通过 `@RestControllerAdvice` 注解，统一拦截 Controller 层抛出的异常，自动返回标准 JSON 格式的错误信息，无需在 Controller 中手动 try-catch。

#### 处理的异常类型

| 异常类型 | HTTP 状态码 | 返回 code | 说明 |
|----------|------------|-----------|------|
| `MethodArgumentNotValidException` | 400 | 400 | `@Valid` 校验不通过时自动抛出，拼接所有字段错误信息 |
| `ConstraintViolationException` | 400 | 400 | `@Validated` 在 `@RequestParam`/`@PathVariable` 上校验不通过时抛出 |
| `BusinessException` | 400 | 自定义 | 通用业务异常 |
| `DeviceCodeAlreadyExistsException` | 400 | 4002 | 设备编码重复 |
| `DeviceTypeNotFound` | 404 | 4001 | 设备类型不存在 |
| `DeviceNotFoundException` | 404 | 4003 | 设备未找到 |
| `DeviceTypeCodeAlreadyExistsException` | 400 | 4051 | 设备类型编码重复 |
| `UserNotFoundException` | 404 | 4004 | 用户未找到 |
| `UsernameAlreadyExistsException` | 400 | 4005 | 用户名已存在 |
| `AccountLockedException` | 400 | 4006 | 账号锁定 |
| `Exception`（兜底） | 500 | 500 | 所有未匹配的异常，返回模糊错误信息防止泄露系统细节 |

> **v2.0 新增**：`ConstraintViolationException` 处理器。当 `@Validated` 注解标注在 `@RequestParam` 或 `@PathVariable` 参数上时，校验失败会抛出 `ConstraintViolationException` 而非 `MethodArgumentNotValidException`，新增处理器确保此类场景也能返回统一格式。

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

> **v2.0 更新**：`ResultCode` 已定义以下常量，替代原先的空类预留：

| 常量名 | 值 | 含义 | 使用位置 |
|--------|-----|------|----------|
| `SUCCESS` | 200 | 操作成功 | `Result.success()` |
| `BAD_REQUEST` | 400 | 参数校验失败 / 通用业务异常 | `GlobalExceptionHandler` |
| `NOT_FOUND` | 404 | 资源未找到 | `DeviceNotFoundException`、`DeviceTypeNotFound`、`UserNotFoundException` |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 | 兜底异常处理 |
| `DEVICE_TYPE_NOT_FOUND` | 4001 | 设备类型不存在 | `DeviceTypeNotFound` |
| `DEVICE_CODE_EXISTS` | 4002 | 设备编码已存在 | `DeviceCodeAlreadyExistsException` |
| `DEVICE_NOT_FOUND` | 4003 | 设备未找到 | `DeviceNotFoundException` |
| `USER_NOT_FOUND` | 4004 | 用户未找到 | `UserNotFoundException` |
| `USERNAME_EXISTS` | 4005 | 用户名已存在 | `UsernameAlreadyExistsException` |
| `ACCOUNT_LOCKED` | 4006 | 账号锁定 | `AccountLockedException` |
| `DEVICE_TYPE_CODE_EXISTS` | 4051 | 设备类型编码已存在 | `DeviceTypeCodeAlreadyExistsException` |

### 2.7 JwtUtil — JWT工具类

**完整类名**: `com.example.platform.common.util.JwtUtil`

基于 `io.jsonwebtoken` 库封装的JWT工具类，提供Token的生成、解析与验证能力。通过 `@Component` 注册为Spring Bean，密钥和过期时间从配置文件读取。

#### 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `jwt.secret` | — | JWT签名密钥（Base64编码），必须在application.yaml中配置 |
| `jwt.expiration` | `86400000`（24小时） | Token过期时间（毫秒） |

#### 方法说明

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `generateToken(String username, String role)` | `String` | 根据用户名和角色生成JWT Token，payload包含username和role两个claim |
| `getUsernameFromToken(String token)` | `String` | 从Token中解析出username claim |
| `getRoleFromToken(String token)` | `String` | 从Token中解析出role claim |
| `validateToken(String token)` | `boolean` | 验证Token签名是否有效且未过期 |
| `isTokenExpired(String token)` | `boolean` | 判断Token是否已过期 |

#### Token结构

```
Header: { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "username", "username": "admin", "role": "ADMIN", "iat": ..., "exp": ... }
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

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

### 示例 4：@Validated 在 @RequestParam 上的校验

```java
@GetMapping("/search")
public Result<List<Device>> search(
        @Validated @RequestParam @Size(min = 1, max = 50, message = "名称长度1-50") String name) {
    return Result.success(deviceService.findByNameLike(name));
}
```

**校验失败时**：抛出 `ConstraintViolationException`，由新增的处理器返回统一格式：

```json
{
  "code": 400,
  "message": "名称长度1-50"
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
| ConstraintViolationException 未被处理 | `@Validated` 在方法参数上校验失败 | v2.0 已新增 `ConstraintViolationException` 处理器，返回统一格式 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，包含 Result 和 ResultCode 文档 |
| v1.1 | 2026-05-08 | `[f88934db]` | 新增 exception 子包（BusinessException、3个具体异常类、GlobalExceptionHandler）和 group 子包（CreateGroup、UpdateGroup）文档；更新状态码列表 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | DeviceNotFoundException 构造参数从 `String deviceName` 改为 `Integer id`；更新使用示例为 `getDeviceOrThrow` 调用方式 |
| v2.0 | 2026-05-17 | — | GlobalExceptionHandler 新增 `ConstraintViolationException` 处理器（@Validated 在 @RequestParam/@PathVariable 上时）；DeviceTypeNotFound 异常返回 HTTP 状态码从 400 改为 404；BusinessException.code 字段改为 `private final`；ResultCode 已定义常量（SUCCESS=200, BAD_REQUEST=400, NOT_FOUND=404, INTERNAL_ERROR=500, DEVICE_TYPE_NOT_FOUND=4001, DEVICE_CODE_EXISTS=4002, DEVICE_NOT_FOUND=4003） |
| v3.0 | 2026-05-24 | — | 新增异常类：`DeviceTypeCodeAlreadyExistsException`(4051)、`UserNotFoundException`(4004)、`UsernameAlreadyExistsException`(4005)、`AccountLockedException`(4006)；新增 `JwtUtil` 工具类（Token生成/解析/验证）；GlobalExceptionHandler 新增4个异常处理器；ResultCode 新增4个常量（USER_NOT_FOUND=4004, USERNAME_EXISTS=4005, ACCOUNT_LOCKED=4006, DEVICE_TYPE_CODE_EXISTS=4051）；修正异常消息格式 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
