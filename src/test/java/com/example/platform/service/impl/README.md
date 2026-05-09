# service/impl — Service 实现类测试包

> **版本**: v1.0 | **更新日期**: 2026-05-09

---

## 一、功能用途

本目录包含 Service 层实现类的集成测试，验证业务逻辑的正确性。测试基于 Spring Boot Test 框架，通过 `@SpringBootTest` 启动完整应用上下文，对 Service 和 Mapper 层进行端到端验证。

### 核心作用

- **集成测试**：在真实 Spring 上下文中验证 Service 和 Mapper 的协作
- **数据验证**：验证 CRUD 操作、条件查询、业务校验等核心逻辑
- **回归保障**：确保代码变更不会破坏已有功能

### 与其他模块的关系

```
测试类 → 注入 Service / Mapper → 操作数据库
测试类 → 验证 Entity 字段值 / 异常抛出
```

### 目录结构

```
service/impl/
├── DeviceServiceImplTest.java   # Mapper 层基础 CRUD 集成测试
└── DeviceServiceTest.java       # Service 层业务逻辑集成测试
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

1. 数据库中需存在初始测试数据（如 ID=1 的设备，编码为 `TEMP-001`）
2. `application.yaml` 中数据库连接配置正确
3. `DeviceServiceTest` 依赖 `@Transactional` 回滚，确保数据库支持事务

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 测试数据依赖 | `testGetById` 等依赖 ID=1 的初始数据 | 确保数据库初始化脚本已执行 |
| 事务回滚不生效 | 测试类缺少 `@Transactional` 注解 | `DeviceServiceTest` 已添加，`DeviceServiceImplTest` 未添加（手动清理） |
| 测试顺序影响 | JUnit 5 默认不保证执行顺序 | 各测试方法应独立，不依赖其他测试的执行结果 |
| 数据库连接失败 | 测试环境数据库不可用 | 检查 `application.yaml` 或 `application-test.yaml` 配置 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-09 | `[0e2c8e48]` | 初始版本，包含 DeviceServiceImplTest（7 个测试方法）和 DeviceServiceTest（11 个测试方法） |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-09。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
