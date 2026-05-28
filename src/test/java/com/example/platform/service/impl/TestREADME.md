# service/impl — Service 实现类测试包

> **版本**: v3.0 | **更新日期**: 2026-05-24

---

## 一、功能用途

本目录包含 Service 层实现类的测试，涵盖集成测试与纯单元测试，验证业务逻辑的正确性。

### 核心作用

- **集成测试**：在真实 Spring 上下文中验证 Service 和 Mapper 的协作
- **纯单元测试**：通过 Mockito 模拟依赖，隔离测试单个 Service 方法的业务逻辑
- **数据验证**：验证 CRUD 操作、条件查询、业务校验等核心逻辑
- **回归保障**：确保代码变更不会破坏已有功能

### 与其他模块的关系

```
集成测试类 → 注入 Service / Mapper → 操作数据库
集成测试类 → 验证 Entity 字段值 / 异常抛出
单元测试类 → @Mock 模拟依赖 → @InjectMocks 注入被测对象
单元测试类 → 验证方法调用 / 返回值 / 异常
```

### 目录结构

```
service/impl/
├── DeviceServiceImplTest.java    # Mapper 层基础 CRUD 集成测试
├── DeviceServiceTest.java        # Service 层业务逻辑集成测试
├── AlertServiceImplTest.java     # AlertServiceImpl 纯单元测试（Mockito）
└── AlertServiceTest.java         # AlertService 集成测试（空类，待补充）
```

---

## 二、接口说明

### 2.1 DeviceServiceImplTest — Mapper 基础集成测试

**完整类名**: `com.example.platform.service.impl.DeviceServiceImplTest`
**注解**: `@SpringBootTest`

该测试类主要验证 `DeviceMapper` 的基础 CRUD 能力和数据库连通性。

#### 测试方法列表

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testConnection()` | Mapper 注入 | `DeviceMapper` 成功注入，不为 null |
| `testSelectById()` | `selectById` | 查询 ID=1 的设备，结果不为 null |
| `testSelectAll()` | `selectList` | 查询所有设备，列表不为空 |
| `testInsert()` | `insert` | 插入测试设备，返回值为 1，ID 自动填充；测试后清理数据 |
| `testUpdate()` | `updateById` | 更新设备位置字段，返回值为 1；测试后恢复原数据 |
| `testFindByNameLike()` | `findByNameLike` | 自定义 SQL 模糊查询，结果不为 null |
| `testFindOnlineDevices()` | `findOnlineDevices` | Service 层查询在线设备，结果不为 null |

#### 特点

- 直接注入 `DeviceMapper` 和 `DeviceService`
- 插入和更新测试会清理/恢复测试数据，避免污染数据库
- 使用 `System.out.println` 输出调试信息

### 2.2 DeviceServiceTest — Service 业务逻辑测试

**完整类名**: `com.example.platform.service.impl.DeviceServiceTest`
**注解**: `@SpringBootTest`, `@Transactional`

该测试类验证 `DeviceService` 的完整业务逻辑，使用 `@Transactional` 确保每个测试方法执行后自动回滚。

#### 测试方法列表

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testListAllDevices()` | `list()` | 查询所有设备，列表不为空 |
| `testGetById()` | `getById()` | 查询 ID=1 的设备，编码为 `TEMP-001` |
| `testGetByIdNotFound()` | `getById()` | 查询不存在的 ID=9999，返回 null |
| `testGetDeviceOrThrow()` | `getDeviceOrThrow()` | 传入不存在的 ID，抛出 `DeviceNotFoundException` |
| `testSaveDevice()` | `save()` | 保存设备，返回 true，ID 和 createTime 自动填充 |
| `testUpdateDevice()` | `updateById()` | 更新设备位置，验证更新后值一致 |
| `testDeleteDevice()` | `removeById()` | 先插入再删除，验证删除后查询为 null |
| `testFindOnlineDevices()` | `findOnlineDevices()` | 查询在线设备，所有设备 status=1 |
| `testFindByNameLike()` | `findByNameLike()` | 模糊查询，所有结果名称包含关键词 |
| `testUpdateStatus()` | `updateStatus()` | 更新状态为 2，验证更新后值一致；测试后恢复原状态 |
| `testPagination()` | `page()` | 分页查询，total >= 0，records 不为 null |

#### 特点

- 使用 `@Transactional` 自动回滚，测试数据不污染数据库
- 仅注入 `DeviceService`，面向接口测试
- 使用 JUnit 5 的 `assertThrows` 验证异常场景
- `testUpdateStatus()` 在验证后恢复原始状态

### 2.3 AlertServiceImplTest — AlertServiceImpl 纯单元测试

**完整类名**: `com.example.platform.service.impl.AlertServiceImplTest`
**注解**: `@ExtendWith(MockitoExtension.class)`

该测试类使用 Mockito 纯单元测试方式验证 `AlertServiceImpl` 的全部 6 个业务方法，无需启动 Spring 上下文。

#### 初始化机制

- `@BeforeEach` 中通过反射设置 `baseMapper` 字段为 Mock 对象
- 通过 `MybatisConfiguration` + `MapperBuilderAssistant` + `TableInfoHelper` 初始化 Lambda 缓存，使 `LambdaQueryWrapper` 在纯 Mockito 环境下正常工作

#### 测试方法列表（按 Nested 分组）

**FindUnhandledTests（2个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_returnOnlyUnhandledAlerts_when_mixedAlertsExist()` | `findUnhandled()` | 返回所有未处理告警，排除已处理告警 |
| `should_returnEmptyList_when_noUnhandledAlerts()` | `findUnhandled()` | 无未处理告警时返回空列表 |

**FindByDeviceIdTests（3个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_returnAlertsForDevice_when_deviceIdIsValid()` | `findByDeviceId()` | 返回指定设备的告警列表 |
| `should_returnEmptyList_when_deviceIdIsNull()` | `findByDeviceId()` | deviceId 为 null 时返回空集合且不调用 Mapper |
| `should_returnEmptyList_when_deviceHasNoAlerts()` | `findByDeviceId()` | 设备无告警时返回空列表 |

**BatchHandleTests（5个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_handleAlertsAndRecordUser_when_idsAreValid()` | `batchHandle()` | 成功批量处理未处理告警并记录处理人 |
| `should_returnZero_when_idsIsNull()` | `batchHandle()` | ids 为 null 时返回 0 且不执行 SQL |
| `should_returnZero_when_idsIsEmpty()` | `batchHandle()` | ids 为空集合时返回 0 且不执行 SQL |
| `should_onlyHandleUnhandledAlerts_when_mixedStatusExists()` | `batchHandle()` | 只处理未处理的告警，跳过已处理的 |
| `should_returnZero_when_idsContainNonExistentId()` | `batchHandle()` | ids 中包含不存在的 ID 时返回 0 |

**GetStatsByDeviceIdTests（3个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_returnCorrectStats_when_deviceExistsWithAlerts()` | `getStatsByDeviceId()` | 设备存在且有告警时返回正确的统计数据 |
| `should_throwDeviceNotFoundException_when_deviceDoesNotExist()` | `getStatsByDeviceId()` | 设备不存在时抛出 DeviceNotFoundException |
| `should_returnZeroCount_when_deviceHasNoAlerts()` | `getStatsByDeviceId()` | 设备存在但无告警时返回 alertCount 为 0 |

**GetStatsByTimeRangeTests（2个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_returnStats_when_timeRangeIsValid()` | `getStatsByTimeRange()` | 有效时间区间应返回统计数据 |
| `should_returnZeroCount_when_noDataInTimeRange()` | `getStatsByTimeRange()` | 无数据的时间区间应返回 alertCount 为 0 |

**ListDeviceAlertCountByTimeRangeTests（2个）**

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `should_returnDeviceAlertCountsInDescOrder()` | `listDeviceAlertCountByTimeRange()` | 多设备告警应按 alertCount 降序排列 |
| `should_returnEmptyList_when_noDataInTimeRange()` | `listDeviceAlertCountByTimeRange()` | 无数据的时间区间应返回空列表 |

> **合计**：17 个测试方法，覆盖 AlertServiceImpl 全部 6 个业务方法。

#### 特点

- 使用 `@ExtendWith(MockitoExtension.class)` 纯 Mockito 测试，无需 Spring 上下文
- 通过 `@Mock` 模拟 `AlertMapper` 和 `DeviceService`，`@InjectMocks` 注入被测对象
- 使用 `@Nested` + `@DisplayName` 按方法分组，测试结构清晰
- 通过反射初始化 `baseMapper` 和 Lambda 缓存，解决 ServiceImpl 在纯 Mockito 环境下的兼容性问题
- 测试覆盖正常路径、空值/边界场景和交互验证
- 运行速度快，不依赖数据库

### 2.4 AlertServiceTest — AlertService 集成测试

**完整类名**: `com.example.platform.service.impl.AlertServiceTest`
**注解**: `@SpringBootTest` + `@Transactional`

> **当前状态**：空类，待补充测试方法。已注入 `AlertService` 依赖，框架搭建完成。

### 2.5 DeviceMapperTest — DeviceMapper 集成测试

**完整类名**: `com.example.platform.mapper.DeviceMapperTest`
**注解**: `@SpringBootTest` + `@Transactional`

该测试类验证 `DeviceMapper` 的自定义 SQL 方法，使用 `@Transactional` 确保测试数据自动回滚。

#### 测试方法列表

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testFindByNameLike()` | `findByNameLike()` | 模糊查询，结果中所有设备名称包含关键词 |
| `testFindByNameLikeNoMatch()` | `findByNameLike()` | 无匹配时返回空列表 |
| `testCountByStatus()` | `countByStatus()` | 按状态分组统计，每条记录包含 status 和 count 字段，count >= 0 |
| `testGetOnlineDetection()` | `getOnlineDetection()` | 离线检测，结果设备的 lastOnlineTime 早于阈值时间，status != 0 |
| `testGetOnlineDetectionWithFutureTime()` | `getOnlineDetection()` | 未来时间阈值查询，结果不为 null |
| `testInsertAndFindByNameLike()` | `insert()` + `findByNameLike()` | 插入设备后通过模糊查询能找到，ID 自动填充 |

#### 特点

- 直接注入 `DeviceMapper`，测试 Mapper 层自定义 SQL
- 使用 `@Transactional` 自动回滚，测试数据不污染数据库
- `testInsertAndFindByNameLike()` 验证插入 + 查询的完整流程

---

### 2.6 AlertConfigServiceTest — AlertConfigService 单元测试（v4.0 新增）

**完整类名**: `com.example.platform.service.impl.AlertConfigServiceTest`
**测试框架**: Mockito + JUnit 5

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testGetDeviceRuleConfig_CacheHit` | 缓存命中 | 缓存有数据时直接返回，不查 DB |
| `testGetDeviceRuleConfig_CacheMiss` | 缓存未命中 | 查 DB 后回填缓存 |
| `testGetDeviceRuleConfig_NullValue` | NullValue 防穿透 | 缓存中查到 NullValue → 返回 null |
| `testGetTypeRuleConfig_CacheHit` | 类型级规则缓存命中 | 同设备级逻辑 |
| `testEvictDeviceRuleConfig` | 清除设备级缓存 | delete 被调用 |
| `testEvictTypeRuleConfig` | 清除类型级缓存 | delete 被调用 |

---

### 2.7 DashboardStatsServiceTest — DashboardStatsService 单元测试（v4.0 新增）

**完整类名**: `com.example.platform.service.impl.DashboardStatsServiceTest`
**测试框架**: Mockito + JUnit 5

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testGetDashboardStats_CacheHit` | 缓存命中 | 直接返回缓存中的 DashboardStatsDTO |
| `testGetDashboardStats_CacheMiss_LockAcquired` | 缓存未命中 + 获取锁成功 | 查 DB 构建数据 → 写入缓存 → 返回 |
| `testGetDashboardStats_CacheMiss_LockFailed_RetrySuccess` | 锁竞争 → 重试命中 | 锁获取失败 → 重试期间缓存被填充 → 返回 |
| `testGetDashboardStats_CacheMiss_LockFailed_AllRetryFailed` | 锁竞争 → 全部重试失败 | 降级：直接查 DB（不缓存） |
| `testGetDashboardStats_NullCount` | DB 返回 null 统计 | 使用默认值 0L 防御 |

---

### 2.8 DictMetricServiceTest — DictMetricService 单元测试（v4.0 新增）

**完整类名**: `com.example.platform.service.impl.DictMetricServiceTest`
**测试框架**: Mockito + JUnit 5

| 方法名 | 测试目标 | 说明 |
|--------|----------|------|
| `testGetFromCache_CacheHit` | Redis 缓存命中 | CacheService.get() 返回值 → 直接返回 |
| `testGetFromCache_CacheMiss_DBHit` | 缓存未命中，DB 有数据 | 查 DB → 回填缓存 → 返回 |
| `testSaveUpdateCache` | 新增指标 → 同步缓存 | 调用 save() 后验证 CacheService.set() 被调用 |
| `testUpdateUpdateCache` | 更新指标 → 同步缓存 | 调用 updateById() 后验证 CacheService.set() 被调用 |
| `testRemoveUpdateCache` | 删除指标 → 同步缓存 | 调用 removeById() 后验证 CacheService.delete() 被调用 |

---

### 2.9 NotificationDedupServiceTest — 通知去重单元测试（v4.0 新增）

**完整类名**: `com.example.platform.service.impl.NotificationDedupServiceTest`
**测试框架**: Mockito + JUnit 5

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testShouldSendNotification_FirstTime` | 首次调用 | CacheService.setIfAbsent 返回 true → shouldSendNotification 返回 true |
| `testShouldSendNotification_Duplicate` | 重复调用 | CacheService.setIfAbsent 返回 false → shouldSendNotification 返回 false |
| `testShouldSendNotification_RedisException` | Redis 异常降级 | 抛异常 → 返回 true（放行通知） |
| `testReleaseDedupMark` | 释放去重标记 | 调用 delete 删除对应 Key |
| `testReleaseDedupMark_RedisException` | 释放失败降级 | 抛异常 → 记录 WARN 日志后静默返回 |

---

### 2.10 AuthServiceImplTest — AuthServiceImpl 单元测试（v4.0 新增）

**完整类名**: `com.example.platform.service.impl.AuthServiceImplTest`
**测试框架**: Mockito + JUnit 5

| 方法名 | 测试目标 | 验证内容 |
|--------|----------|----------|
| `testLogin_Success` | 登录成功 | Token 黑名单检查通过 → 密码匹配 → 返回 JWT Token + UserInfoVO |
| `testLogin_InvalidPassword` | 密码错误 | 抛出 BusinessException(4003) |
| `testLogin_UserNotFound` | 用户不存在 | 抛出 BusinessException(4003) |
| `testLogin_AccountLocked` | 账号已锁定 | 抛出 AccountLockedException |
| `testLogin_TokenBlacklisted` | Token 已失效 | 黑名单包含当前 Token → 返回空或提示重新登录 |
| `testLogin_IPFreqLimit` | IP 频率限制 | 超出频率限制 → 抛出 BusinessException(4003) |

#### service/ 子包测试

| 测试类 | 位置 | 说明 |
|--------|------|------|
| `CacheServiceTest` | `service/CacheServiceTest.java` | CacheService 单元测试（set/get/delete/setIfAbsent/deleteByPattern/executeLua/降级） |
| `TokenBlacklistServiceTest` | `service/TokenBlacklistServiceTest.java` | TokenBlacklistService 单元测试（blacklist/isBlacklisted/过期清理） |

---

## 三、使用方法

### 运行全部测试

```bash
mvn test -Dtest="com.example.platform.service.impl.*Test"
```

### 运行单个测试类

```bash
mvn test -Dtest="DeviceServiceTest"
```

### 运行单个测试方法

```bash
mvn test -Dtest="DeviceServiceTest#testGetDeviceOrThrow"
```

### 前置条件

1. 集成测试（DeviceServiceImplTest、DeviceServiceTest）需数据库中存在初始测试数据（如 ID=1 的设备，编码为 `TEMP-001`）
2. `application.yaml` 中数据库连接配置正确
3. `DeviceServiceTest` 依赖 `@Transactional` 回滚，确保数据库支持事务
4. 纯单元测试（AlertServiceImplTest）无需数据库，仅需 Mockito 依赖

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 测试数据依赖 | `testGetById` 等依赖 ID=1 的初始数据 | 确保数据库初始化脚本已执行 |
| 事务回滚不生效 | 测试类缺少 `@Transactional` 注解 | `DeviceServiceTest` 已添加，`DeviceServiceImplTest` 未添加（手动清理） |
| 测试顺序影响 | JUnit 5 默认不保证执行顺序 | 各测试方法应独立，不依赖其他测试的执行结果 |
| 数据库连接失败 | 测试环境数据库不可用 | 检查 `application.yaml` 或 `application-test.yaml` 配置 |
| Mockito stubbing 错误 | Mock 对象未正确配置返回值 | 检查 `when(...).thenReturn(...)` 配置是否匹配实际调用参数 |
| 集成测试与单元测试混合运行 | 集成测试需要数据库，单元测试不需要 | 可通过 Maven Profile 或 `-Dtest` 参数分别运行 |

---

## 五、变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-05-09 | 初始版本，包含 DeviceServiceImplTest（7 个测试方法）和 DeviceServiceTest（11 个测试方法） |
| v2.0 | 2026-05-17 | 新增 AlertServiceImplTest（17 个 Mockito 纯单元测试），覆盖 AlertServiceImpl 全部 6 个方法 |
| v3.0 | 2026-05-24 | AlertServiceImplTest 方法名更新为 BDD 风格命名（should_xxx_when_xxx），使用 @Nested + @DisplayName 按方法分组，新增反射初始化 baseMapper 和 Lambda 缓存机制说明；新增 AlertServiceTest 集成测试（空类，待补充）；新增 DeviceMapperTest 集成测试（6个测试方法，覆盖 findByNameLike/countByStatus/getOnlineDetection/insert+findByNameLike） |
| v4.0 | 2026-05-28 | `3d96056` | 新增 AlertConfigServiceTest（6个测试方法：Cache-Aside 缓存命中/未命中/NullValue）；新增 DashboardStatsServiceTest（5个测试方法：缓存命中/互斥锁竞争/重试/降级/null 统计防御）；新增 DictMetricServiceTest（5个测试方法：Redis 缓存命中/未命中/CRUD 缓存同步）；新增 NotificationDedupServiceTest（5个测试方法：首次/重复/Redis 异常降级/释放去重标记）；新增 AuthServiceImplTest（6个测试方法：登录成功/密码错误/用户不存在/账号锁定/Token 黑名单/IP 频率限制）；新增 service/ 子包 CacheServiceTest 和 TokenBlacklistServiceTest |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
