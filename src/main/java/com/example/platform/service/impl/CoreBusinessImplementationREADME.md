# service/impl — 业务逻辑实现包

> **版本**: v3.0 | **更新日期**: 2026-05-24

---

## 一、功能用途

`service/impl` 包包含 Service 层接口的具体实现类以及告警规则引擎。每个实现类继承 `ServiceImpl` 并实现对应的 Service 接口，提供完整的业务逻辑。

### 核心作用

- **业务逻辑实现**：实现 Service 接口定义的业务方法
- **告警规则引擎**：`AlertRuleEngine` 负责评估设备数据是否触发告警
- **告警聚合统计**：`AlertServiceImpl` 提供按设备、按时间维度的告警统计分析
- **缓存管理**：`DictMetricServiceImpl` 提供指标字典的内存缓存
- **事务控制**：通过 `@Transactional` 保证数据一致性

### 目录结构

```
service/impl/
├── AlertRuleEngine.java               # 告警规则引擎（@Component）
├── AlertServiceImpl.java              # 告警服务实现
├── AlertConfigServiceImpl.java        # 告警配置缓存服务实现
├── AuthServiceImpl.java               # 认证服务实现
├── CacheServiceImpl.java              # 统一缓存服务实现
├── DashboardStatsServiceImpl.java     # 仪表盘统计服务实现
├── DeviceDataServiceImpl.java         # 设备数据服务实现
├── DeviceServiceImpl.java             # 设备服务实现
├── DeviceTypeServiceImpl.java         # 设备类型服务实现
├── DictMetricServiceImpl.java         # 指标字典服务实现（Redis 分布式缓存）
├── NotificationDedupServiceImpl.java  # 通知去重服务实现
├── SysAuditLogServiceImpl.java        # 审计日志服务实现
└── SysUserServiceImpl.java            # 用户服务实现
```

---

## 二、接口说明

### 2.1 AlertRuleEngine — 告警规则引擎

**完整类名**: `com.example.platform.service.impl.AlertRuleEngine`
**注解**: `@Component` + `@Slf4j`

告警规则引擎是数据采集模块的核心组件，负责评估设备数据是否触发告警规则，并自动生成或升级告警记录。

#### 核心方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `evaluate(Integer deviceId, DeviceData data)` | `void` | 评估设备数据是否触发告警 |

#### 运行流程

```
1. 基础校验 → dataValue 为 null 则跳过；设备不存在则由 getDeviceOrThrow 抛出 DeviceNotFoundException
2. 获取生效规则 → 设备级优先，无则降级到类型级
3. 过滤匹配规则 → 按指标匹配，按告警级别降序排序
4. 获取活跃告警快照 → 一次性查出所有未处理告警（避免 N+1 问题）
5. 按告警类型分组遍历评估 → 命中即止（break）
6. 批量保存新告警 → saveBatch
```

#### 规则优先级

- **设备级规则优先**：如果设备自身配置了 `alertRuleConfig`，优先使用
- **类型级规则降级**：设备未配置时，使用所属设备类型的 `alertRuleConfig`
- **无规则则跳过**：两者均无配置时不触发告警

#### getEffectiveRuleConfig() NPE 防御

> **v2.0 修正**：`getEffectiveRuleConfig()` 方法已添加 NPE 防御逻辑：

```java
// 设备级规则：deviceConfig.getRules() != null 时才返回
if (deviceConfig != null && deviceConfig.getRules() != null) {
    return deviceConfig;
}
// 类型级规则：typeConfig.getRules() != null 时才返回
if (typeConfig != null && typeConfig.getRules() != null) {
    return typeConfig;
}
return null;
```

避免当 `AlertRuleConfig` 对象存在但 `rules` 字段为 null 时误判为有规则配置。

#### 防重与升级机制

| 场景 | 处理方式 |
|------|----------|
| 同类型无活跃告警 | 新增告警记录 + 调用 `notificationDedupService.shouldSendNotification(alert.getId())`（AlertId 粒度去重） |
| 同类型已有活跃告警，新规则级别更高 | 升级现有告警（更新级别和消息），不发送通知 |
| 同类型已有活跃告警，新规则级别更低或相同 | 不做处理（避免重复告警） |

> **v2.0 修正**：告警升级方向已修正为 `rule.getLevel() < existingAlert.getAlertLevel()`。级别数字越小越紧急（如 1=紧急, 2=警告, 3=提示），因此只有新规则级别数字更小时才升级。

#### 指标匹配逻辑

| 规则 metric | 数据 metric | 结果 |
|-------------|-------------|------|
| 空/未设置 | 任意 | ✅ 匹配（规则不限指标） |
| 有值 | 空/未设置 | ❌ 不匹配（防止误触发） |
| 有值 | 有值 | 精确相等才匹配 |

#### 消息生成

- 优先使用规则中的 `messageTemplate`，支持占位符：`{metricName}`、`{unit}`、`{value}`、`{threshold}`
- 未配置模板时使用默认格式：`"温度 触发规则，当前值 85.5℃，阈值 > 80℃"`
- 指标中文名和单位从 `DictMetricService` 缓存获取，查不到则降级使用英文标识

#### 并发冲突说明

内存判重可挡住 99% 的常规请求，剩余 1% 的并发冲突需依赖数据库唯一索引兜底。生产环境建议在 `saveBatch` 处 try-catch 忽略 `DuplicateKeyException`。

#### 通知去重变更（v4.0）

- **旧方案**：`(deviceId, alertType)` 粒度 + TTL 5min — 告警处理后再触发会被误去重，造成漏通知
- **新方案**：`alertId` 粒度 + 事件驱动删除（`NotificationDedupService.releaseDedupMark`）+ 48h TTL 兜底
- **升级告警**：不再重复发送通知（仅新建告警时调用 `shouldSendNotification`）
- **降级策略**：Redis 异常时 `shouldSendNotification` 返回 true（放行通知），宁可重复不错过

### 2.2 DeviceDataServiceImpl — 设备数据服务实现

**完整类名**: `com.example.platform.service.impl.DeviceDataServiceImpl`
**继承**: `ServiceImpl<DeviceDataMapper, DeviceData>`
**注解**: `@Service`

#### 自定义方法

| 方法 | 说明 |
|------|------|
| `findByDeviceId(Integer deviceId)` | 按设备ID查询历史数据，按采集时间倒序 |
| `findLatestByDeviceId(Integer deviceId)` | 查询最新一条数据（LIMIT 1） |
| `findByDeviceIdAndTimeRange(Integer, LocalDateTime, LocalDateTime)` | 按时间段查询，按采集时间升序 |
| `getStatistics(Integer, LocalDateTime, LocalDateTime)` | 统计数据（min/max/avg/count），先校验设备存在再委托 Mapper 层 SQL |
| `saveWithAlertCheck(DeviceData)` | `@Transactional`：校验设备存在 → 保存数据 → 触发告警评估 |
| `saveBatchWithAlertCheck(List<DeviceData>)` | `@Transactional`：批量保存数据，逐条触发告警检查 |
| `pageByDeviceId(Integer current, Integer size, Integer deviceId)` | 按设备ID分页查询，按采集时间倒序 |
| `pageByCondition(Integer current, Integer size, Integer deviceId, LocalDateTime startTime, LocalDateTime endTime)` | 按条件分页查询，支持设备ID和时间范围组合筛选 |

> **v2.0 新增**：`saveBatchWithAlertCheck`、`pageByDeviceId`、`pageByCondition` 方法。

#### 依赖注入

```java
private final AlertRuleEngine alertRuleEngine;
private final DeviceService deviceService;
```

### 2.3 AlertServiceImpl — 告警服务实现

**完整类名**: `com.example.platform.service.impl.AlertServiceImpl`
**继承**: `ServiceImpl<AlertMapper, Alert>`
**注解**: `@Service`

#### 依赖注入

```java
private final DeviceService deviceService;
private final NotificationDedupService notificationDedupService;  // v4.0 新增

public AlertServiceImpl(DeviceService deviceService, NotificationDedupService notificationDedupService) {
    this.deviceService = deviceService;
    this.notificationDedupService = notificationDedupService;
}
```

> **v2.0 修正**：`deviceService` 字段已改为 `private final`，确保不可变性和线程安全。

#### 自定义方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `findUnhandled()` | `List<Alert>` | 查询所有未处理告警（`isHandled = 0`） |
| `findByDeviceId(Integer deviceId)` | `List<Alert>` | 按设备ID查询告警；`deviceId` 为 null 时返回 `Collections.emptyList()` |
| `batchHandle(List<Integer> ids, String handleUser)` | `int` | `@Transactional`：批量处理未处理告警，空集合防御返回 0；处理成功后同步调用 `notificationDedupService.releaseDedupMark(id)` 释放去重标记 |
| `getStatsByDeviceId(Integer deviceId)` | `Map<String, Object>` | 按设备统计告警数量/最高级别/平均级别/最低级别，先校验设备存在 |
| `getStatsByTimeRange(LocalDateTime, LocalDateTime)` | `Map<String, Object>` | 按时间区间统计告警数量/最高级别/平均级别/最低级别 |
| `listDeviceAlertCountByTimeRange(LocalDateTime, LocalDateTime)` | `List<Map<String, Object>>` | 按时间区间统计各设备告警数量，按告警数量降序排列 |
| `createOfflineAlertsForDevices(List<Device> devices, int alertLevel)` | `void` | `@Transactional`：为离线设备批量创建离线告警（含去重逻辑） |

#### createOfflineAlertsForDevices 实现说明

> **v2.0 新增**

- 标注 `@Transactional`，保证批量操作的原子性
- 去重逻辑：先查询每个设备当前是否已有未处理的离线告警，已有则跳过，避免重复创建
- 遍历设备列表，为每个离线设备创建一条离线告警记录
- 告警级别由调用方传入 `alertLevel` 参数

#### 关键变更（v1.1）

- `findUnhandled()` 移除了 `orderByDesc(alertLevel).orderByDesc(createTime)` 排序，简化为仅按 `isHandled = 0` 过滤
- `findByDeviceId()` 移除了 `orderByDesc(createTime)` 排序，新增 `deviceId == null` 防御返回 `Collections.emptyList()`
- `batchHandle()` 新增 `ids == null || ids.isEmpty()` 防御性校验
- 新增 `DeviceService` 构造器注入（供 `getStatsByDeviceId` 校验设备存在性）
- 新增3个告警统计方法，委托 `AlertMapper` 自定义 SQL

### 2.4 DeviceServiceImpl — 设备服务实现

**完整类名**: `com.example.platform.service.impl.DeviceServiceImpl`
**继承**: `ServiceImpl<DeviceMapper, Device>`
**注解**: `@Service`

#### 关键变更

- `save()` 中的设备类型校验改为调用 `deviceTypeService.getTypeOrThrow(typeId)`（替代原 `typeIdExists`）
- `save()` 已添加 `@Transactional(rollbackFor = Exception.class)`，确保业务校验和插入操作的原子性
- `getDeviceOrThrow()` 添加 `@Override` 注解
- 新增 `updateAlertConfig(Integer id, AlertRuleConfig config)`：更新设备级告警规则
- 新增 `getOnlineDetection(LocalDateTime checkTime)`：查询指定时间之前未更新在线时间的设备（含从未上线），委托 `DeviceMapper.getOnlineDetection`
- 新增 `pageByCondition(Integer current, Integer size, Integer status, String name)`：按条件分页查询设备

> **v2.0 修正**：`save()` 已添加 `@Transactional(rollbackFor = Exception.class)`；新增 `pageByCondition` 方法。

### 2.5 DeviceTypeServiceImpl — 设备类型服务实现

**完整类名**: `com.example.platform.service.impl.DeviceTypeServiceImpl`
**继承**: `ServiceImpl<DeviceTypeMapper, DeviceType>`
**注解**: `@Service`

#### 关键变更

- 原 `typeIdExists(int typeId)` 重构为 `getTypeOrThrow(Integer typeId)`：返回 `DeviceType` 对象而非 void，参数类型从 `int` 改为 `Integer`
- 新增 `updateAlertConfig(Integer typeId, AlertRuleConfig config)`：更新类型级告警规则

### 2.6 DictMetricServiceImpl — 指标字典服务实现

**完整类名**: `com.example.platform.service.impl.DictMetricServiceImpl`
**继承**: `ServiceImpl<DictMetricMapper, DictMetric>`
**注解**: `@Service` + `@Slf4j`

#### 缓存机制

- **v4.0 迁移**：从 `ConcurrentHashMap` 本地内存缓存 → Redis 分布式缓存（`CacheService`）
- 使用 `@PostConstruct` 启动时从 DB 全量加载并预热到 Redis
- `getFromCache()` 改为 Redis 优先 → DB 回填（Cache-Aside 模式）
- 缓存一致性：`save()`/`updateById()`/`removeById()` 自动维护 Redis 缓存

#### 缓存一致性

覆写以下方法，自动维护缓存：

| 方法 | 缓存操作 |
|------|----------|
| `save()` | 新增成功后 `put(metricKey, entity)` |
| `updateById()` | 更新成功后 `put(metricKey, entity)` 覆盖 |
| `removeById()` | 删除成功后 `remove(metricKey)` |

### 2.7 AuthServiceImpl — 认证服务实现

**完整类名**: `com.example.platform.service.impl.AuthServiceImpl`
**实现接口**: `AuthService`
**注解**: `@Service` + `@Slf4j`

> 不继承 `ServiceImpl`，为独立业务实现类。

#### 核心方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `login(LoginDTO loginDTO, String ip)` | `LoginResponseDTO` | `@Transactional`：用户登录认证，验证凭据并签发 JWT Token |

#### 依赖注入

```java
private final SysUserService sysUserService;
private final SysAuditLogService sysAuditLogService;
private final JwtUtil jwtUtil;
private final PasswordEncoder passwordEncoder;
```

#### 登录流程

```
1. IP频率检查 → ipFailCount >= 5 时拒绝请求（防止暴力破解）
2. 查找用户 → findByUsername，不存在时记录审计日志并抛出统一错误提示（防止用户名枚举攻击）
3. 检查账号锁定 → lockTime 未过期时抛出 AccountLockedException；锁定刚过期时自动重置计数
4. 检查账号停用 → status == 1 时抛出 BusinessException(ACCOUNT_DISABLED)
5. 验证密码 → BCrypt.matches() 比对明文与密文
6. 登录成功 → 重置失败计数 → 签发 JWT Token → 记录 LOGIN_SUCCESS 审计日志 → 清除IP失败计数
7. 登录失败 → 递增失败计数 → 记录 LOGIN_FAIL 审计日志 → 递增IP失败计数 → 抛出统一错误提示
```

#### IP频率限制

- 使用 `ConcurrentHashMap<String, Integer>` 记录每个IP的登录失败次数
- 同一IP连续失败5次后拒绝登录请求，提示"登录尝试过于频繁"
- 登录成功时清除对应IP的失败计数

#### 私有辅助方法

| 方法 | 说明 |
|------|------|
| `checkAccountLocked(SysUser user)` | 检查账号锁定状态，锁定中抛出 `AccountLockedException`；锁定刚过期返回 `true`（已自动重置计数） |
| `checkAccountDisabled(SysUser user)` | 检查账号是否停用，停用时抛出 `BusinessException(ACCOUNT_DISABLED)` |
| `handleLoginFailure(SysUser user, String ip)` | 处理登录失败：递增失败计数、记录审计日志、递增IP失败计数、抛出统一错误 |
| `buildLoginResponse(String token, SysUser user)` | 构建 `LoginResponseDTO` 响应对象 |

#### 常量配置

| 常量 | 值 | 说明 |
|------|----|------|
| `MAX_LOGIN_FAIL_COUNT` | 3 | 最大连续登录失败次数（达阈值后锁定账号） |
| `LOCK_DURATION_MINUTES` | 10 | 账号锁定时长（分钟） |

> **注意**：`MAX_LOGIN_FAIL_COUNT` 为 3 次，与 `SysUserServiceImpl.incrementLoginFailCount()` 中的阈值（3次）一致。锁定时长在 `SysUserServiceImpl` 中设置为 30 分钟，此处常量仅用于错误提示消息。

### 2.8 SysUserServiceImpl — 用户服务实现

**完整类名**: `com.example.platform.service.impl.SysUserServiceImpl`
**继承**: `ServiceImpl<SysUserMapper, SysUser>`
**实现接口**: `SysUserService`
**注解**: `@Service`

#### 依赖注入

```java
private final PasswordEncoder passwordEncoder;
```

#### 自定义方法

| 方法 | 返回类型 | 事务 | 说明 |
|------|----------|------|------|
| `findByUsername(String username)` | `SysUser` | — | 委托 `baseMapper.findByUsername()` 查询 |
| `getUserOrThrow(Long id)` | `SysUser` | — | `getById(id)` + null 检查，不存在抛出 `UserNotFoundException` |
| `createUser(SysUser user)` | `boolean` | `@Transactional` | 校验用户名唯一性 → BCrypt 加密密码 → 设置默认值（status=0, role=VIEWER）→ 保存 |
| `updateUser(SysUser user)` | `boolean` | `@Transactional` | 校验用户存在 → `LambdaUpdateWrapper` 仅更新 nickname/role（防止注入 password/status） |
| `resetPassword(Long id, String newPassword)` | `void` | `@Transactional` | 校验用户存在 → BCrypt 加密新密码 → 更新 |
| `updateStatus(Long id, Integer status)` | `void` | `@Transactional` | 校验用户存在 → 更新 status 字段 |
| `pageUsers(Integer current, Integer size, String keyword)` | `Page<SysUser>` | — | 按用户名关键字分页查询，按创建时间倒序 |
| `resetLoginFailCount(Long id)` | `void` | `@Transactional` | 重置 loginFailCount=0 并清除 lockTime |
| `incrementLoginFailCount(Long id)` | `int` | `@Transactional` | SQL原子递增 `login_fail_count`，达3次时设置 `lockTime = now + 30分钟` |

#### incrementLoginFailCount 并发安全

```java
// 使用 SQL 原子递增，避免并发竞态条件（read-then-write）
wrapper.setSql("login_fail_count = COALESCE(login_fail_count, 0) + 1");
```

- 第1步：SQL 原子递增失败计数
- 第2步：读取递增后的最新值
- 第3步：达到阈值（3次）且尚未锁定时，设置锁定截止时间

#### 安全设计

| 设计点 | 说明 |
|--------|------|
| 密码存储 | BCrypt 加密，永不存储明文 |
| 更新字段白名单 | `updateUser()` 仅允许更新 nickname/role，防止通过请求体注入 password/status |
| 用户名唯一性 | `createUser()` 前校验，重复抛出 `UsernameAlreadyExistsException` |
| 默认角色 | 新用户默认 role=VIEWER，status=0（正常） |

### 2.9 SysAuditLogServiceImpl — 审计日志服务实现

**完整类名**: `com.example.platform.service.impl.SysAuditLogServiceImpl`
**继承**: `ServiceImpl<SysAuditLogMapper, SysAuditLog>`
**实现接口**: `SysAuditLogService`
**注解**: `@Service`

#### 自定义方法

| 方法 | 返回类型 | 事务 | 说明 |
|------|----------|------|------|
| `pageLogs(Integer, Integer, Long, String, LocalDateTime, LocalDateTime)` | `Page<SysAuditLog>` | — | 多条件分页查询，支持 userId/operation/时间范围筛选，按 createTime 倒序 |
| `cleanExpiredLogs(LocalDateTime beforeTime)` | `int` | `@Transactional` | 委托 `baseMapper.deleteByCreateTimeBefore()` 清理过期日志（保留180天） |
| `log(Long userId, String username, String operation, String ip, String detail)` | `void` | `@Transactional` | 创建并保存一条审计日志记录 |

#### pageLogs 条件构建

```java
// 所有条件均为可选，动态拼接
if (userId != null) wrapper.eq(SysAuditLog::getUserId, userId);
if (operation != null && !operation.trim().isEmpty()) wrapper.eq(SysAuditLog::getOperation, operation);
if (startTime != null) wrapper.ge(SysAuditLog::getCreateTime, startTime);
if (endTime != null) wrapper.le(SysAuditLog::getCreateTime, endTime);
wrapper.orderByDesc(SysAuditLog::getCreateTime);
```

---

### 2.10 CacheServiceImpl — 统一缓存服务实现

**完整类名**: `com.example.platform.service.impl.CacheServiceImpl`
**实现接口**: `CacheService`
**注解**: `@Service` + `@Slf4j`

#### 依赖注入

```java
private final RedisTemplate<String, Object> redisTemplate;
```

#### 核心方法实现

| 方法 | 实现方式 | 防御机制 |
|------|----------|----------|
| `set` | `redisTemplate.opsForValue().set(key, jsonStr, timeout, unit)` | 随机 TTL（±20%） |
| `get` | `redisTemplate.opsForValue().get(key)` → NullValue 判断 → 类型反序列化 | 空值返回 null（穿透防御由调用方控制） |
| `setIfAbsent` | `redisTemplate.opsForValue().setIfAbsent(...)` | 原子 SETNX |
| `deleteByPattern` | `SCAN` 命令遍历 + 批量 `delete` | SCAN 替代 KEYS（避免 Redis 阻塞） |
| `executeLua` | `redisTemplate.execute(RedisScript, keys, args)` | Lua 原子执行 |
| `delete` | `redisTemplate.delete(key)` | 返回 boolean |
| `multiSet` | `redisTemplate.opsForValue().multiSet(map)` + 逐条设置 TTL | 管道批量操作 |

#### 降级策略

| 操作类型 | Redis 不可用时的行为 |
|----------|---------------------|
| `get` / `exists` | 返回 null/false（静默降级，不抛异常） |
| `set` / `delete` / `deleteByPattern` | 记录 WARN 日志后静默返回（非关键路径） |
| `setIfAbsent` | 返回 false（保守策略：认为 Key 已存在） |
| 计数器相关 | **直接抛出 CacheOperationException**（不降级，避免错误计数值） |

#### NullValue 防穿透

```java
static class NullValue implements Serializable {
    private String marker = "__NULL__";
}
// 写入：key → NullValue（TTL 5min）
// 读取：检测到 NullValue → 返回 null
```

---

### 2.11 AlertConfigServiceImpl — 告警配置缓存服务实现

**完整类名**: `com.example.platform.service.impl.AlertConfigServiceImpl`
**实现接口**: `AlertConfigService`
**注解**: `@Service` + `@Slf4j`

#### Cache-Aside 模式实现

**读取流程**:
```
getAlertRuleConfig(deviceId)
  → cacheService.get("alert:config:device:" + deviceId, AlertRuleConfig.class)
  → 命中 → 直接返回
  → 未命中 → mapper 查 DB → 查到了 → cacheService.set → 返回
  → 未命中 → mapper 查 DB → 没查到 → cacheService.set(NullValue, 5min) → 返回 null
```

**写入流程**（更新告警规则时）:
```
updateAlertRuleConfig(deviceId, config)
  → mapper.updateById(device)
  → cacheService.delete("alert:config:device:" + deviceId)
  → 下次读取时自动回填
```

#### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 缓存 Key 前缀 | `alert:config:device:` / `alert:config:type:` | 设备/类型级规则 |
| 缓存 TTL | 60 min | 规则变更频率低 |
| NullValue TTL | 5 min | 短暂缓存空值 |
| 随机 TTL 浮动 | ±20% | 防雪崩 |

---

### 2.12 DashboardStatsServiceImpl — 仪表盘统计服务实现

**完整类名**: `com.example.platform.service.impl.DashboardStatsServiceImpl`
**实现接口**: `DashboardStatsService`
**注解**: `@Service` + `@Slf4j`

#### 互斥锁防缓存击穿

```
getDashboardStats()
  → cacheService.get("stats:dashboard", DashboardStatsDTO.class)
  → 命中 → 直接返回
  → 未命中 → acquireLock("stats:dashboard:lock", 3s)
    → 获取锁成功 → 查 DB 构建 → cacheService.set("stats:dashboard", dto, 30s) → 释放锁 → 返回
    → 获取锁失败 → 重读缓存（最多 3 次，间隔 100ms）
      → 命中 → 返回
      → 均未命中 → 降级：直接查 DB（不缓存） → 返回
```

#### 统计数据构建

| 统计项 | 数据来源 | 说明 |
|--------|----------|------|
| `totalDevices` | `deviceMapper.selectCount(null)` | 设备总数 |
| `onlineDevices` | `deviceService.findOnlineDevices().size()` | 在线设备数（status=1） |
| `statusDistribution` | `deviceMapper.countByStatus()` | 设备状态分布 `{status: count}` |
| `totalAlerts` | `alertMapper.selectCount(null)` | 历史告警总数 |
| `unhandledAlerts` | `alertService.findUnhandled().size()` | 未处理告警数 |

#### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `CACHE_KEY` | `stats:dashboard` | 缓存 Key |
| `CACHE_TTL_SECONDS` | 30 | 业务容忍度内（30 秒延迟） |
| `LOCK_KEY` | `stats:dashboard:lock` | 互斥锁 Key |
| `LOCK_TTL_SECONDS` | 3 | 持锁超时（防止死锁） |
| `MAX_RETRY_COUNT` | 3 | 获取锁失败后重试次数 |
| `RETRY_INTERVAL_MS` | 100 | 重试间隔 |

---

### 2.13 NotificationDedupServiceImpl — 通知去重服务实现

**完整类名**: `com.example.platform.service.impl.NotificationDedupServiceImpl`
**实现接口**: `NotificationDedupService`
**注解**: `@Service` + `@Slf4j`

#### 依赖注入

```java
private final CacheService cacheService;
```

#### 核心方法实现

| 方法 | 实现方式 | 说明 |
|------|----------|------|
| `shouldSendNotification(alertId)` | `cacheService.setIfAbsent("notify:alert:" + alertId, "1", 48, HOURS)` | 首次返回 true，重复返回 false |
| `releaseDedupMark(alertId)` | `cacheService.delete("notify:alert:" + alertId)` | 告警处理时手动清除 |

#### 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `DEDUP_KEY_PREFIX` | `notify:alert:` | Key 前缀 |
| `DEDUP_TTL_HOURS` | 48 | 兜底 TTL（正常由 `releaseDedupMark` 提前清除） |

#### 降级策略

| 方法 | Redis 异常时行为 |
|------|-----------------|
| `shouldSendNotification` | 返回 `true`（放行通知，宁可重复不错过） |
| `releaseDedupMark` | 记录 WARN 日志后静默返回（不影响告警处理正确性） |

---

## 三、使用方法

### 示例 1：数据上报触发告警

```java
// Controller 调用
@PostMapping
public Result<Void> reportData(@Valid @RequestBody DeviceData deviceData) {
    deviceDataService.saveWithAlertCheck(deviceData);
    return Result.success();
}

// saveWithAlertCheck 内部流程：
// 1. deviceService.getDeviceOrThrow(deviceId) → 校验设备存在
// 2. save(deviceData) → 保存采集数据
// 3. alertRuleEngine.evaluate(deviceId, deviceData) → 评估告警
```

### 示例 2：配置告警规则

```java
// 设备级规则
deviceService.updateAlertConfig(1, alertRuleConfig);

// 类型级规则（设备未配规则时的降级方案）
deviceTypeService.updateAlertConfig(1, alertRuleConfig);
```

### 示例 3：手动刷新指标缓存

```java
dictMetricService.refreshCache();
// 或通过 API：POST /api/metric/refreshCache
```

### 示例 4：告警聚合统计

```java
// 按设备统计告警
Map<String, Object> stats = alertService.getStatsByDeviceId(1);
// 返回: {alertCount=5, maxLevel=1, avgLevel=2.4, minLevel=3}

// 按时间区间统计告警
Map<String, Object> timeStats = alertService.getStatsByTimeRange(
    LocalDateTime.of(2026, 5, 1, 0, 0),
    LocalDateTime.of(2026, 5, 16, 0, 0));

// 按时间区间统计各设备告警数量（降序）
List<Map<String, Object>> deviceCounts = alertService.listDeviceAlertCountByTimeRange(
    LocalDateTime.of(2026, 5, 1, 0, 0),
    LocalDateTime.of(2026, 5, 16, 0, 0));
// 返回: [{deviceId=1, alertCount=8}, {deviceId=3, alertCount=5}, ...]
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 告警重复生成 | 高并发下可能重复插入 | 依赖数据库唯一索引兜底，生产环境建议 try-catch `DuplicateKeyException` |
| 缓存不一致 | 多实例部署时本地缓存不共享 | **v4.0 已解决**：DictMetric 缓存已迁移至 Redis（`CacheService`），支持多实例共享 |
| 告警规则不生效 | 设备和类型均未配置 `alertRuleConfig` | 确保至少在设备类型级别配置了告警规则 |
| `getTypeOrThrow` 替代 `typeIdExists` | 旧方法只校验不返回对象 | 新方法返回 `DeviceType` 对象，避免二次查询 |
| AlertServiceImpl 排序变更 | `findUnhandled`/`findByDeviceId` 不再默认排序 | 如需排序，调用方在 `LambdaQueryWrapper` 中自行指定 |
| Redis 操作规范（v4.0） | 直接使用 `RedisTemplate` 难以统一监控/调试 | 所有 Redis 操作必须通过 `CacheService` 统一接口 |
| 告警统计SQL性能 | `getStatsByDeviceId` 等方法全表扫描 | 确保 `device_id`、`create_time` 字段有索引 |
| AlertRuleEngine NPE 风险 | rules 为 null 时误判为有规则 | v2.0 已添加 `getRules() != null` 防御 |
| 告警升级方向 | 级别数字越小越紧急 | v2.0 已修正为 `rule.getLevel() < existingAlert.getAlertLevel()` |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-15 | `[05b8ff81]` | 初始版本，包含 AlertRuleEngine 告警规则引擎、DeviceDataServiceImpl（5个自定义方法）、DeviceServiceImpl（新增 updateAlertConfig）、DeviceTypeServiceImpl（重构 typeIdExists → getTypeOrThrow，新增 updateAlertConfig）、DictMetricServiceImpl（内存缓存机制） |
| v1.1 | 2026-05-16 | `[240739a]` | AlertServiceImpl 重大更新：新增3个告警统计方法（getStatsByDeviceId/getStatsByTimeRange/listDeviceAlertCountByTimeRange）、新增 DeviceService 注入、findUnhandled/findByDeviceId 简化排序、batchHandle 空集合防御；AlertRuleEngine 改用 getDeviceOrThrow；DeviceDataServiceImpl.getStatistics 新增设备校验；DeviceServiceImpl 新增 getOnlineDetection |
| v2.0 | 2026-05-17 | — | AlertRuleEngine.getEffectiveRuleConfig() 添加 NPE 防御（deviceConfig.getRules()!=null, typeConfig.getRules()!=null）；告警升级方向修正为 rule.getLevel() < existingAlert.getAlertLevel()（级别数字越小越紧急）；AlertServiceImpl 新增 createOfflineAlertsForDevices 方法（@Transactional + 去重逻辑）、deviceService 字段改为 private final；DeviceServiceImpl.save() 添加 @Transactional(rollbackFor=Exception.class)、新增 pageByCondition 方法；DeviceDataServiceImpl 新增 saveBatchWithAlertCheck/pageByDeviceId/pageByCondition 方法 |
| v3.0 | 2026-05-24 | — | 新增 AuthServiceImpl 认证服务实现（login 登录认证含IP频率限制、账号锁定/停用检查、BCrypt密码校验、JWT签发、审计日志记录）；新增 SysUserServiceImpl 用户服务实现（9个自定义方法：findByUsername/getUserOrThrow/createUser/updateUser/resetPassword/updateStatus/pageUsers/resetLoginFailCount/incrementLoginFailCount，含SQL原子递增并发安全、BCrypt密码加密、更新字段白名单防注入）；新增 SysAuditLogServiceImpl 审计日志服务实现（3个自定义方法：pageLogs/cleanExpiredLogs/log，含多条件动态查询） |
| v4.0 | 2026-05-28 | `3d96056` | 新增 CacheServiceImpl（统一缓存服务实现，含穿透/击穿/雪崩防御、SCAN/Lua/NullValue/随机TTL）；新增 AlertConfigServiceImpl（告警规则 Cache-Aside 缓存）；新增 DashboardStatsServiceImpl（仪表盘统计，互斥锁防缓存击穿）；新增 NotificationDedupServiceImpl（AlertId 粒度 + 事件驱动删除 + 48h TTL）；AlertRuleEngine 修改（新建告警调用 shouldSendNotification、升级告警不发送通知）；AlertServiceImpl 修改（batchHandle 调用 releaseDedupMark、新增 NotificationDedupService 依赖注入）；DictMetricServiceImpl 修改（本地内存缓存迁移至 Redis 分布式缓存） |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
