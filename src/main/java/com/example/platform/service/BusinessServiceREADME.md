# service — 业务逻辑层包

> **版本**: v3.0 | **更新日期**: 2026-05-24

---

## 一、功能用途

`service` 包是项目的业务逻辑层，封装核心业务规则和处理流程。采用接口 + 实现类的标准设计模式，接口定义业务能力，实现类提供具体逻辑。

### 核心作用

- **业务封装**：将复杂的业务逻辑从 Controller 中剥离，保持 Controller 简洁
- **事务管理**：通过 `@Transactional` 注解控制数据库事务
- **通用能力继承**：继承 `IService` 接口，自动获得基础 CRUD 方法
- **接口隔离**：面向接口编程，便于替换实现和单元测试
- **业务校验**：在 Service 层执行业务规则校验（如唯一性检查、关联性验证）

### 与其他模块的关系

```
Controller → 调用 Service 接口方法
Service    → 调用 Mapper 方法操作数据库
Service    → 使用 Entity 对象传递数据
Service    → 抛出 BusinessException 子类（由 GlobalExceptionHandler 统一处理）
```

### 目录结构

```
service/
├── AlertService.java           # 告警服务接口
├── AlertConfigService.java     # 告警规则配置缓存服务接口
├── AuthService.java            # 认证服务接口
├── CacheService.java           # 缓存服务统一接口
├── DashboardStatsService.java  # 仪表盘统计服务接口
├── DeviceDataService.java      # 设备数据服务接口
├── DeviceService.java          # 设备服务接口
├── DeviceTypeService.java      # 设备类型服务接口
├── DictMetricService.java      # 指标字典服务接口
├── NotificationDedupService.java # 通知去重服务接口
├── SysAuditLogService.java     # 审计日志服务接口
├── SysUserService.java         # 用户服务接口
├── TokenBlacklistService.java  # Token 黑名单服务接口
└── impl/                       # 实现类目录
    ├── AlertRuleEngine.java    # 告警规则引擎
    ├── AlertServiceImpl.java
    ├── AlertConfigServiceImpl.java
    ├── AuthServiceImpl.java
    ├── CacheServiceImpl.java
    ├── DashboardStatsServiceImpl.java
    ├── DeviceDataServiceImpl.java
    ├── DeviceServiceImpl.java
    ├── DeviceTypeServiceImpl.java
    ├── DictMetricServiceImpl.java
    ├── NotificationDedupServiceImpl.java
    ├── SysAuditLogServiceImpl.java
    └── SysUserServiceImpl.java
```

---

## 二、接口说明

### 2.1 IService 通用方法（所有 Service 自动继承）

所有 Service 接口继承 `IService<T>`，自动获得以下方法（无需重写声明）：

#### 查询方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getById(Serializable id)` | `T` | 根据主键查询 |
| `listByIds(Collection ids)` | `List<T>` | 根据 ID 集合批量查询 |
| `listByMap(Map columnMap)` | `List<T>` | 根据字段条件查询 |
| `getOne(Wrapper wrapper)` | `T` | 查询单条记录 |
| `list(Wrapper wrapper)` | `List<T>` | 根据条件查询列表 |
| `count(Wrapper wrapper)` | `long` | 统计记录数 |
| `page(Page page, Wrapper wrapper)` | `IPage<T>` | 分页查询 |

#### 新增/更新方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `save(T entity)` | `boolean` | 插入一条记录 |
| `saveBatch(Collection list)` | `boolean` | 批量插入 |
| `saveOrUpdate(T entity)` | `boolean` | 有主键则更新，无则插入 |
| `updateById(T entity)` | `boolean` | 根据主键更新 |
| `update(T entity, Wrapper wrapper)` | `boolean` | 根据条件更新 |

#### 删除方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `removeById(Serializable id)` | `boolean` | 根据主键删除 |
| `removeByMap(Map columnMap)` | `boolean` | 根据条件删除 |
| `remove(Wrapper wrapper)` | `boolean` | 根据条件构造器删除 |
| `removeBatchByIds(Collection ids)` | `boolean` | 根据 ID 批量删除 |

### 2.2 DeviceService — 设备服务接口

**完整类名**: `com.example.platform.service.DeviceService`
**继承**: `IService<Device>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `getDeviceOrThrow(Integer id)` | `Device` | `id`: 设备ID | 设备存在性校验，不存在时抛出 `DeviceNotFoundException` |
| `findByNameLike(String name)` | `List<Device>` | `name`: 设备名称关键词 | 根据名称模糊查询设备 |
| `findOnlineDevices()` | `List<Device>` | 无 | 查询所有在线设备（status = 1），按 updateTime 倒序 |
| `updateStatus(Integer id, Integer status)` | `boolean` | `id`: 设备ID, `status`: 新状态值 | 更新设备状态 |
| `updateAlertConfig(Integer id, AlertRuleConfig config)` | `void` | `id`: 设备ID, `config`: 告警规则配置 | 更新设备级告警规则配置 |
| `getOnlineDetection(LocalDateTime checkTime)` | `List<Device>` | `checkTime`: 检查时间阈值 | 查询指定时间之前未更新在线时间的设备（含从未上线） |
| `pageByCondition(Integer current, Integer size, Integer status, String name)` | `IPage<Device>` | `current`: 页码, `size`: 每页大小, `status`: 设备状态（可选）, `name`: 设备名称（可选） | 按条件分页查询设备 |

> **v2.0 修正**：`updateAlertConfig` 参数名从 `typeId` 改为 `id`；新增 `pageByCondition` 方法。

#### 实现类

**完整类名**: `com.example.platform.service.impl.DeviceServiceImpl`
**继承**: `ServiceImpl<DeviceMapper, Device>`
**注解**: `@Service` ✅

| 方法 | 实现方式 | 说明 |
|------|----------|------|
| `getDeviceOrThrow` | 调用 `getById(id)` + null 检查 | 不存在时抛出 `DeviceNotFoundException(id)` |
| `findByNameLike` | 调用 `baseMapper.findByNameLike()` | 委托给 Mapper 层的自定义 SQL |
| `findOnlineDevices` | 使用 `LambdaQueryWrapper` | 构建 `status = 1` 条件，按 `updateTime` 倒序 |
| `updateStatus` | 使用 `LambdaUpdateWrapper` | 构建按 ID 更新 status 的条件 |

#### 业务校验逻辑

`DeviceServiceImpl` 的 `save()` 方法覆写了 `IService.save()`，新增以下业务校验：

| 校验项 | 校验方式 | 失败时抛出异常 |
|--------|----------|---------------|
| 设备编码唯一性 | `lambdaQuery().eq(Device::getDeviceCode, code).count() > 0` | `DeviceCodeAlreadyExistsException` |
| 设备类型存在性 | `deviceTypeService.getTypeOrThrow(typeId)` | `DeviceTypeNotFound` |

#### DeviceServiceImpl 特有方法（未声明在接口中）

以下方法定义在 `DeviceServiceImpl` 中，未通过 `DeviceService` 接口暴露，仅供内部或同包调用：

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `findByTypeId(Integer typeId)` | `List<Device>` | 根据设备类型 ID 查询设备列表 |
| `countByStatus()` | `List<Map<String, Object>>` | 按状态分组统计设备数量，委托 `baseMapper.countByStatus()` |
| `updateAlertConfig(Integer id, AlertRuleConfig config)` | `void` | 更新设备级告警规则，通过 `JacksonTypeHandler` 存为 JSON |

### 2.3 DeviceTypeService — 设备类型服务接口

**完整类名**: `com.example.platform.service.DeviceTypeService`
**继承**: `IService<DeviceType>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `updateAlertConfig(Integer typeId, AlertRuleConfig config)` | `void` | `typeId`: 类型ID, `config`: 告警规则配置 | 更新设备类型级告警规则配置 |
| `getTypeOrThrow(Integer typeId)` | `DeviceType` | `typeId`: 类型ID | 类型存在性校验，不存在时抛出 `DeviceTypeNotFound` |
| `findByTypeCode(String typeCode)` | `DeviceType` | `typeCode`: 类型编码 | 根据类型编码查询设备类型 |

> **v2.0 修正**：`getTypeOrThrow` 参数类型从 `int` 改为 `Integer`。

#### 实现类

**完整类名**: `com.example.platform.service.impl.DeviceTypeServiceImpl`
**继承**: `ServiceImpl<DeviceTypeMapper, DeviceType>`
**注解**: `@Service` ✅

| 方法 | 实现方式 | 说明 |
|------|----------|------|
| `updateAlertConfig` | `getTypeOrThrow` + `updateById` | 先校验存在性，再 set 配置对象并更新 |
| `getTypeOrThrow` | `getById(typeId)` + null 检查 | 不存在时抛出 `DeviceTypeNotFound`，替代原 `typeIdExists` |
| `findByTypeCode` | 使用 `LambdaQueryWrapper` | 构建 `type_code = typeCode` 条件查询单条记录 |

### 2.4 DeviceDataService — 设备数据服务接口

**完整类名**: `com.example.platform.service.DeviceDataService`
**继承**: `IService<DeviceData>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `findByDeviceId(Integer deviceId)` | `List<DeviceData>` | `deviceId`: 设备ID | 查询指定设备的所有数据，按采集时间倒序 |
| `findLatestByDeviceId(Integer deviceId)` | `DeviceData` | `deviceId`: 设备ID | 查询指定设备的最新一条数据（LIMIT 1） |
| `findByDeviceIdAndTimeRange(Integer deviceId, LocalDateTime startTime, LocalDateTime endTime)` | `List<DeviceData>` | `deviceId`: 设备ID, `startTime`/`endTime`: 时间范围 | 按时间段查询采集数据，按采集时间升序 |
| `getStatistics(Integer deviceId, LocalDateTime startTime, LocalDateTime endTime)` | `Map<String, Object>` | 同上 | 获取时间段内统计数据（minVal/maxVal/avgVal/count） |
| `saveWithAlertCheck(DeviceData deviceData)` | `void` | `deviceData`: 设备数据 | 保存数据并触发告警检查（`@Transactional`） |
| `saveBatchWithAlertCheck(List<DeviceData> dataList)` | `void` | `dataList`: 设备数据列表 | 批量保存数据并逐条触发告警检查（`@Transactional`） |
| `pageByDeviceId(Integer current, Integer size, Integer deviceId)` | `IPage<DeviceData>` | `current`: 页码, `size`: 每页大小, `deviceId`: 设备ID | 按设备ID分页查询数据 |
| `pageByCondition(Integer current, Integer size, Integer deviceId, LocalDateTime startTime, LocalDateTime endTime)` | `IPage<DeviceData>` | `current`: 页码, `size`: 每页大小, `deviceId`: 设备ID（可选）, `startTime`/`endTime`: 时间范围（可选） | 按条件分页查询设备数据 |

> **v2.0 新增**：`saveBatchWithAlertCheck`、`pageByDeviceId`、`pageByCondition` 方法。

**实现类**: `DeviceDataServiceImpl`
**注解**: `@Service` ✅

#### DeviceDataServiceImpl 实现说明

- `saveWithAlertCheck()`：校验设备存在 → 保存数据 → 调用 `AlertRuleEngine.evaluate()` 触发告警评估
- `saveBatchWithAlertCheck()`：批量保存数据，逐条触发告警检查
- `pageByDeviceId()`：按设备ID分页查询，按采集时间倒序
- `pageByCondition()`：支持设备ID和时间范围条件组合的分页查询
- `getStatistics()`：委托 `baseMapper.getStatisticsByDeviceIdAndTimeRange()`，无数据时返回 null 占位
- 依赖 `AlertRuleEngine` 和 `DeviceService`（构造器注入）

### 2.5 AlertService — 告警服务接口

**完整类名**: `com.example.platform.service.AlertService`
**继承**: `IService<Alert>`

> 接口层当前仅继承通用方法。以下自定义方法定义在实现类中。

**实现类**: `AlertServiceImpl`
**注解**: `@Service` ✅

#### AlertServiceImpl 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `findByDeviceId(Integer deviceId)` | `List<Alert>` | `deviceId`: 设备ID | 按设备ID查询告警；`deviceId` 为 null 时返回 `Collections.emptyList()` |
| `findUnhandled()` | `List<Alert>` | 无 | 查询所有未处理告警（`isHandled = 0`） |
| `batchHandle(List<Integer> ids, String handleUser)` | `int` | `ids`: 告警ID列表, `handleUser`: 处理人 | `@Transactional`：批量处理未处理告警，空集合防御返回 0 |
| `getStatsByDeviceId(Integer deviceId)` | `Map<String, Object>` | `deviceId`: 设备ID | 按设备统计告警数量/最高级别/平均级别/最低级别，先校验设备存在 |
| `getStatsByTimeRange(LocalDateTime, LocalDateTime)` | `Map<String, Object>` | `startTime`/`endTime`: 时间范围 | 按时间区间统计告警数量/最高级别/平均级别/最低级别 |
| `listDeviceAlertCountByTimeRange(LocalDateTime, LocalDateTime)` | `List<Map<String, Object>>` | `startTime`/`endTime`: 时间范围 | 按时间区间统计各设备告警数量，按告警数量降序排列 |
| `createOfflineAlertsForDevices(List<Device> devices, int alertLevel)` | `void` | `devices`: 设备列表, `alertLevel`: 告警级别 | 为离线设备批量创建离线告警（`@Transactional`，含去重逻辑） |

> **v2.0 新增**：`createOfflineAlertsForDevices` 方法。

#### AlertServiceImpl 依赖注入

```java
private final DeviceService deviceService;

public AlertServiceImpl(DeviceService deviceService) {
    this.deviceService = deviceService;
}
```

### 2.6 DictMetricService — 指标字典服务接口

**完整类名**: `com.example.platform.service.DictMetricService`
**继承**: `IService<DictMetric>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `initCache()` | `void` | 无 | 初始化内存缓存（`@PostConstruct` 启动时自动执行） |
| `refreshCache()` | `void` | 无 | 刷新缓存（管理页面手动触发） |
| `getFromCache(String metricKey)` | `DictMetric` | `metricKey`: 指标标识 | 从内存缓存读取指标元数据（高频读取） |
| `removeById(Integer id)` | `boolean` | `id`: 主键 | 删除指标并同步移除缓存 |

**实现类**: `DictMetricServiceImpl`
**注解**: `@Service` ✅ + `@Slf4j`

#### DictMetricServiceImpl 实现说明

- 使用 `ConcurrentHashMap` 作为本地内存缓存，key 为 `metricKey`
- 启动时通过 `@PostConstruct` 自动加载全量数据到缓存
- 覆写 `save()`/`updateById()`/`removeById()` 方法，自动维护缓存一致性
- `getFromCache()` 为高频读取方法，直接走内存缓存，不查数据库

### 2.7 AuthService — 认证服务接口

**完整类名**: `com.example.platform.service.AuthService`

> 不继承 `IService`，为独立业务接口。

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `login(LoginDTO loginDTO, String ip)` | `LoginResponseDTO` | `loginDTO`: 登录请求体（用户名+密码）, `ip`: 客户端IP | 用户登录认证，验证凭据并签发 JWT Token |

**实现类**: `AuthServiceImpl`
**注解**: `@Service` ✅

#### AuthServiceImpl 实现说明

- 依赖 `SysUserService`、`SysAuditLogService`、`JwtUtil`、`PasswordEncoder`（构造器注入）
- 登录流程：查找用户 → 校验账号状态 → 校验密码 → 签发 Token → 记录审计日志
- 账号锁定检查：`lockTime` 未过期时抛出 `AccountLockedException`
- 密码校验：使用 `BCrypt.matches()` 比对明文与密文
- 登录成功：重置失败计数 → 签发 Token → 记录 `LOGIN_SUCCESS` 审计日志
- 登录失败：递增失败计数（达5次锁定30分钟）→ 记录 `LOGIN_FAIL` 审计日志 → 抛出异常

### 2.8 SysUserService — 用户服务接口

**完整类名**: `com.example.platform.service.SysUserService`
**继承**: `IService<SysUser>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `findByUsername(String username)` | `SysUser` | `username`: 用户名 | 根据用户名查询用户完整信息（含密码、角色、状态），用于登录认证 |
| `getUserOrThrow(Long id)` | `SysUser` | `id`: 用户ID | 用户存在性检查，不存在则抛出 `UserNotFoundException` |
| `createUser(SysUser user)` | `boolean` | `user`: 待创建用户对象（含明文密码） | 创建新用户，校验用户名唯一性并对密码 BCrypt 加密 |
| `updateUser(SysUser user)` | `boolean` | `user`: 待更新用户对象 | 更新用户基本信息（昵称、角色），不涉及密码变更 |
| `resetPassword(Long id, String newPassword)` | `void` | `id`: 用户ID, `newPassword`: 新密码明文 | 重置密码，新密码经 BCrypt 加密后存储 |
| `updateStatus(Long id, Integer status)` | `void` | `id`: 用户ID, `status`: 目标状态 | 启用（0）或停用（1）用户账号 |
| `pageUsers(Integer current, Integer size, String keyword)` | `Page<SysUser>` | `current`: 页码, `size`: 每页大小, `keyword`: 用户名关键字（可选） | 按用户名关键字分页查询用户列表 |
| `resetLoginFailCount(Long id)` | `void` | `id`: 用户ID | 重置登录失败计数为0（登录成功时调用） |
| `incrementLoginFailCount(Long id)` | `int` | `id`: 用户ID | 递增登录失败计数，达到阈值（5次）时锁定账号30分钟 |

**实现类**: `SysUserServiceImpl`
**注解**: `@Service` ✅

#### SysUserServiceImpl 实现说明

- 依赖 `SysUserMapper`、`PasswordEncoder`（构造器注入）
- `createUser()`：校验用户名唯一性（重复抛出 `UsernameAlreadyExistsException`）→ BCrypt 加密密码 → 保存
- `resetPassword()`：先 `getUserOrThrow(id)` 确保用户存在 → BCrypt 加密新密码 → 更新
- `incrementLoginFailCount()`：递增失败次数，达到5次时设置 `lockTime = now + 30分钟`

### 2.9 SysAuditLogService — 审计日志服务接口

**完整类名**: `com.example.platform.service.SysAuditLogService`
**继承**: `IService<SysAuditLog>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `pageLogs(Integer current, Integer size, Long userId, String operation, LocalDateTime startTime, LocalDateTime endTime)` | `Page<SysAuditLog>` | `current`: 页码, `size`: 每页大小, `userId`: 操作人ID（可选）, `operation`: 操作类型（可选）, `startTime`/`endTime`: 时间范围（可选） | 按条件分页查询审计日志 |
| `cleanExpiredLogs(LocalDateTime beforeTime)` | `int` | `beforeTime`: 保留截止时间 | 清理指定时间之前的过期审计日志，返回删除条数 |
| `log(Long userId, String username, String operation, String ip, String detail)` | `void` | `userId`: 操作人ID, `username`: 操作人账号, `operation`: 操作类型, `ip`: 操作IP, `detail`: 操作详情 | 记录一条审计日志（登录成功/失败等安全事件） |

**实现类**: `SysAuditLogServiceImpl`
**注解**: `@Service` ✅

#### SysAuditLogServiceImpl 实现说明

- `pageLogs()`：使用 `LambdaQueryWrapper` 构建多条件查询，按 `createTime` 倒序
- `cleanExpiredLogs()`：委托 `baseMapper.deleteByCreateTimeBefore()` 执行批量删除
- `log()`：创建 `SysAuditLog` 对象并保存，`createTime` 由 `MyMetaObjectHandler` 自动填充

---

### 2.10 CacheService — 统一缓存服务接口

**完整类名**: `com.example.platform.service.CacheService`

> ⚠️ **重要**：所有 Redis 缓存操作必须通过此接口，禁止直接使用 `RedisTemplate`。

#### 方法列表

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `set(String key, Object value, long timeout, TimeUnit unit)` | `void` | 写入缓存（设置 TTL） |
| `get(String key, Class<T> clazz)` | `T` | 读取缓存，自动反序列化为指定类型 |
| `delete(String key)` | `boolean` | 删除缓存 |
| `exists(String key)` | `boolean` | 判断 Key 是否存在 |
| `setIfAbsent(String key, Object value, long timeout, TimeUnit unit)` | `boolean` | SETNX 原子写入（返回是否设置成功） |
| `getAndSet(String key, Object value, long timeout, TimeUnit unit)` | `<T> T` | 获取旧值并设置新值 |
| `deleteByPattern(String pattern)` | `long` | SCAN 批量删除，返回删除数 |
| `executeLua(String script, Class<T> resultType, List<String> keys, Object... args)` | `<T> T` | 执行 Lua 脚本（原子操作） |
| `multiSet(Map<String, Object> map, long timeout, TimeUnit unit)` | `void` | 批量写入 |

#### 防御策略

| 策略 | 实现方式 | 目标场景 |
|------|----------|----------|
| 缓存穿透防御 | `NullValue` 占位对象 + 短 TTL（5min） | 查询不存在的数据 |
| 缓存雪崩防御 | 随机 TTL（±20% 浮动） | 大量 Key 同时过期 |
| 缓存击穿防御 | 互斥锁（SETNX）+ 重试机制 | 热点 Key 过期瞬间高并发 |
| 降级策略 | 非计数器操作异常时返回 null/静默降级 | Redis 不可用保障业务可用 |
| 计数器安全 | 计数器操作异常直接抛出，不做降级 | 避免拿到错误计数值 |

**实现类**: `CacheServiceImpl`
**注解**: `@Service` ✅

---

### 2.11 AlertConfigService — 告警规则配置缓存服务接口

**完整类名**: `com.example.platform.service.AlertConfigService`

#### 方法列表

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getAlertRuleConfig(Integer deviceId)` | `AlertRuleConfig` | 获取设备级告警规则（缓存→DB→回填） |
| `getTypeAlertRuleConfig(Integer typeId)` | `AlertRuleConfig` | 获取类型级告警规则（缓存→DB→回填） |
| `evictAlertRuleConfig(Integer deviceId)` | `void` | 清除设备级规则缓存 |
| `evictTypeAlertRuleConfig(Integer typeId)` | `void` | 清除类型级规则缓存 |

**实现类**: `AlertConfigServiceImpl`
**注解**: `@Service` ✅

#### 缓存策略

- Key 格式：`alert:config:device:{deviceId}` / `alert:config:type:{typeId}`
- Cache-Aside 模式：读（缓存命中直接返回 → 未命中查 DB → 回填缓存），写（更新 DB → 删除缓存）
- NullValue 占位防穿透 + 随机 TTL 防雪崩

---

### 2.12 DashboardStatsService — 仪表盘统计服务接口

**完整类名**: `com.example.platform.service.DashboardStatsService`

#### 方法列表

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getDashboardStats()` | `DashboardStatsDTO` | 获取仪表盘聚合统计（设备状态分布 + 告警统计） |

**实现类**: `DashboardStatsServiceImpl`
**注解**: `@Service` ✅

**缓存设计**:
- 缓存 Key：`stats:dashboard` ｜ TTL：30 秒
- 防击穿：互斥锁 `stats:dashboard:lock`（TTL 3s），失败重试 3 次（间隔 100ms）
- 降级：锁竞争失败后直接查 DB（不缓存）

---

### 2.13 NotificationDedupService — 通知去重服务接口

**完整类名**: `com.example.platform.service.NotificationDedupService`

#### 方法列表

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `shouldSendNotification(Integer alertId)` | `boolean` | 检查是否应发送通知（首次返回 true） |
| `releaseDedupMark(Integer alertId)` | `void` | 手动释放去重标记（告警处理时调用） |

**实现类**: `NotificationDedupServiceImpl`
**注解**: `@Service` ✅

**去重策略**:
- **粒度**: AlertId 级别（每条告警独立去重）
- **Key 格式**: `notify:alert:{alertId}`
- **TTL**: 48 小时（兜底防护，正常由 `releaseDedupMark` 提前清除）
- **删除机制**: 事件驱动手动删除（告警处理时调用 `releaseDedupMark`）
- **降级策略**: Redis 异常时降级放行（返回 true），避免阻塞告警通知

---

### 2.14 TokenBlacklistService — Token 黑名单服务接口

**完整类名**: `com.example.platform.service.TokenBlacklistService`

#### 方法列表

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `blacklist(String token, long ttlMillis)` | `void` | 将 Token 加入黑名单（用户登出/密码重置时调用） |
| `isBlacklisted(String token)` | `boolean` | 检查 Token 是否在黑名单中 |

**Key 格式**: `token:blacklist:{token前8位}`
**TTL**: Token 剩余有效期（黑名单条目在 Token 过期后自动清理）

---

## 三、使用示例

### 示例 1：在 Controller 中注入并使用 Service

```java
@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<List<Device>> list() {
        return Result.success(deviceService.list());
    }

    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        return Result.success(deviceService.findOnlineDevices());
    }
}
```

### 示例 2：新增设备（含业务校验）

```java
// Controller 调用
@PostMapping
public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
    deviceService.save(device);
    return Result.success();
}

// DeviceServiceImpl.save() 内部逻辑：
// 1. 检查 deviceCode 是否已存在 → 存在则抛出 DeviceCodeAlreadyExistsException
// 2. 检查 typeId 对应的设备类型是否存在 → 不存在则抛出 DeviceTypeNotFound
// 3. 校验通过 → 调用 super.save() 执行插入
// 4. createTime / updateTime 由 MyMetaObjectHandler 自动填充
```

### 示例 3：使用条件构造器进行复杂查询

```java
// 查询指定类型的在线设备
public List<Device> findOnlineByType(Integer typeId) {
    LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Device::getTypeId, typeId)
           .eq(Device::getStatus, 1);
    return deviceService.list(wrapper);
}
```

### 示例 4：根据类型编码查询设备类型

```java
DeviceType type = deviceTypeService.findByTypeCode("TEMP_SENSOR");
if (type == null) {
    throw new DeviceTypeNotFound("TEMP_SENSOR");
}
```

### 示例 5：设备存在性校验

```java
// 在 Controller 中使用 getDeviceOrThrow 确保设备存在
@GetMapping("/{id}")
public Result<Device> getById(@PathVariable Integer id) {
    return Result.success(deviceService.getDeviceOrThrow(id));
}

// 设备不存在时自动抛出 DeviceNotFoundException → GlobalExceptionHandler 返回 404
```

### 示例 6：查询设备最新数据和未处理告警

```java
// 查询设备最新一条采集数据
DeviceData latest = deviceDataServiceImpl.findLatestByDeviceId(1);

// 查询所有未处理告警
List<Alert> unhandled = alertServiceImpl.findUnhandled();
```

### 示例 7：告警聚合统计

```java
// 按设备统计告警
Map<String, Object> stats = alertServiceImpl.getStatsByDeviceId(1);
// 返回: {alertCount=5, maxLevel=1, avgLevel=2.4, minLevel=3}

// 按时间区间统计各设备告警数量
List<Map<String, Object>> deviceCounts = alertServiceImpl.listDeviceAlertCountByTimeRange(
    LocalDateTime.of(2026, 5, 1, 0, 0),
    LocalDateTime.of(2026, 5, 16, 0, 0));
// 返回: [{deviceId=1, alertCount=8}, {deviceId=3, alertCount=5}, ...]
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| Service 注入失败 | 接口没有对应的实现类 | 确保实现类有 `@Service` 注解，且实现了对应接口 |
| 业务校验异常 | `save()` 抛出 `BusinessException` 子类 | 由 `GlobalExceptionHandler` 统一捕获并返回错误响应，Controller 无需 try-catch |
| 事务不生效 | 方法非 `public` 或同类内部调用 | 事务方法必须为 `public`，且不能在同类内部直接调用 |
| `baseMapper` 为 null | 实现类未正确继承 `ServiceImpl` | 确保继承 `ServiceImpl<XXXMapper, XXXEntity>` |
| 批量操作性能 | `saveBatch` 默认每批 1000 条 | 数据量大时可分批调用，避免内存溢出 |
| 接口与实现不一致 | 接口方法在实现类中未覆写 | 实现类必须使用 `@Override` 注解覆写接口方法 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，部分实现类缺少 `@Service` 注解 |
| v1.1 | 2026-05-08 | `[f88934db]` | 所有实现类已补全 `@Service` 注解；DeviceServiceImpl.save() 新增业务校验逻辑（编码唯一性+类型存在性）；DeviceTypeService 新增 `findByTypeCode()` 方法；Controller 注入方式改为构造器注入 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | DeviceService 新增 `getDeviceOrThrow()` 接口方法；DeviceServiceImpl 新增 `findByTypeId()`、`countByStatus()` 方法；AlertServiceImpl 新增 `findByDeviceId()`、`findUnhandled()` 方法；DeviceDataServiceImpl 新增 `findByDeviceId()`、`findLatestByDeviceId()` 方法；DeviceTypeServiceImpl 新增 `findByTypeCode()` 方法 |
| v1.3 | 2026-05-15 | `[05b8ff81]` | DeviceService 新增 `updateAlertConfig()` 方法；DeviceTypeService 重构 `typeIdExists` → `getTypeOrThrow`，新增 `updateAlertConfig()` 方法；DeviceDataService 新增 `findByDeviceIdAndTimeRange()`、`getStatistics()`、`saveWithAlertCheck()` 接口方法；新增 DictMetricService 指标字典服务（含内存缓存）；新增 AlertRuleEngine 告警规则引擎 |
| v1.4 | 2026-05-16 | `[240739a]` | AlertServiceImpl 重大更新：新增3个告警统计方法（getStatsByDeviceId/getStatsByTimeRange/listDeviceAlertCountByTimeRange）、新增 batchHandle 批量处理、新增 DeviceService 注入、findUnhandled/findByDeviceId 简化排序；DeviceService 新增 getOnlineDetection |
| v2.0 | 2026-05-17 | — | DeviceService 新增 `pageByCondition` 方法；`updateAlertConfig` 参数名从 `typeId` 改为 `id`；DeviceDataService 新增 `saveBatchWithAlertCheck`、`pageByDeviceId`、`pageByCondition` 方法；AlertService 新增 `createOfflineAlertsForDevices(List<Device>, int)` 方法；DeviceTypeService.getTypeOrThrow 参数类型从 `int` 改为 `Integer` |
| v3.0 | 2026-05-24 | — | 新增 AuthService 认证服务接口（login 登录认证，不继承 IService）；新增 SysUserService 用户服务接口（9个自定义方法：findByUsername/getUserOrThrow/createUser/updateUser/resetPassword/updateStatus/pageUsers/resetLoginFailCount/incrementLoginFailCount）；新增 SysAuditLogService 审计日志服务接口（3个自定义方法：pageLogs/cleanExpiredLogs/log） |
| v4.0 | 2026-05-28 | `3d96056` | 新增 CacheService 统一缓存服务接口（9 个方法，含穿透/击穿/雪崩防御策略、NullValue 占位/随机 TTL/互斥锁/SETNX/SCAN/Lua）；新增 AlertConfigService 告警规则配置缓存服务接口（4 个方法，Cache-Aside 模式）；新增 DashboardStatsService 仪表盘统计服务接口（互斥锁防缓存击穿）；新增 NotificationDedupService 通知去重服务接口（AlertId 粒度 + 事件驱动删除 + 48h TTL）；新增 TokenBlacklistService Token 黑名单服务接口（登出/密码重置场景） |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
