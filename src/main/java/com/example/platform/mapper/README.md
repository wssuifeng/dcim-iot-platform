# mapper — 数据访问层包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`mapper` 包是项目的数据访问层（DAO 层），负责与数据库进行交互。每个 Mapper 接口继承 MyBatis-Plus 的 `BaseMapper`，自动获得基础 CRUD 能力，无需手写 SQL。

### 核心作用

- **基础 CRUD**：通过继承 `BaseMapper` 自动获得插入、删除、更新、查询等方法
- **自定义 SQL**：对于复杂查询，可通过注解或 XML 编写自定义 SQL
- **数据隔离**：将数据库操作与业务逻辑分离，保持代码层次清晰

### 与其他模块的关系

```
Service 层 → 调用 Mapper 方法 → 执行 SQL → 操作数据库
```

- **被 Service 调用**：Service 层通过注入 Mapper 接口调用数据访问方法
- **操作 Entity**：Mapper 的泛型参数为对应的 Entity 类
- **被 Spring 扫描**：通过 `@Mapper` 注解或 `@MapperScan` 注册为 Spring Bean

---

## 二、接口说明

### 2.1 BaseMapper 通用方法（所有 Mapper 自动继承）

以下方法无需声明，继承 `BaseMapper<T>` 即可直接使用：

#### 查询方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `selectById(Serializable id)` | `T` | 根据主键查询一条记录 |
| `selectBatchIds(Collection ids)` | `List<T>` | 根据主键批量查询 |
| `selectByMap(Map<String, Object> columnMap)` | `List<T>` | 根据字段条件查询 |
| `selectOne(Wrapper<T> wrapper)` | `T` | 根据条件查询单条记录 |
| `selectList(Wrapper<T> wrapper)` | `List<T>` | 根据条件查询列表 |
| `selectCount(Wrapper<T> wrapper)` | `Long` | 根据条件统计记录数 |
| `selectPage(Page<T> page, Wrapper<T> wrapper)` | `IPage<T>` | 分页查询 |

#### 插入方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `insert(T entity)` | `int` | 插入一条记录 |

#### 更新方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `updateById(T entity)` | `int` | 根据主键更新 |
| `update(T entity, Wrapper<T> wrapper)` | `int` | 根据条件更新 |

#### 删除方法

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `deleteById(Serializable id)` | `int` | 根据主键删除 |
| `deleteBatchIds(Collection ids)` | `int` | 根据主键批量删除 |
| `deleteByMap(Map<String, Object> columnMap)` | `int` | 根据字段条件删除 |
| `delete(Wrapper<T> wrapper)` | `int` | 根据条件构造器删除 |

### 2.2 DeviceMapper — 设备数据访问

**完整类名**: `com.example.platform.mapper.DeviceMapper`
**继承**: `BaseMapper<Device>`

#### 自定义方法

| 方法签名 | 返回类型 | 参数 | 说明 |
|----------|----------|------|------|
| `findByNameLike(String name)` | `List<Device>` | `name`: 设备名称关键词 | 根据名称模糊查询 |
| `countByStatus()` | `List<Map<String, Object>>` | 无 | 统计各状态的设备数量 |

**自定义 SQL**：

```sql
-- findByNameLike
SELECT * FROM devices WHERE device_name LIKE CONCAT('%', #{name}, '%')

-- countByStatus
SELECT status, COUNT(*) as count FROM devices GROUP BY status
```

### 2.3 DeviceDataMapper — 设备数据访问

**完整类名**: `com.example.platform.mapper.DeviceDataMapper`
**继承**: `BaseMapper<DeviceData>`

> 当前仅继承通用方法，无自定义方法。

### 2.4 DeviceTypeMapper — 设备类型数据访问

**完整类名**: `com.example.platform.mapper.DeviceTypeMapper`
**继承**: `BaseMapper<DeviceType>`

> 当前仅继承通用方法，无自定义方法。

### 2.5 AlertMapper — 告警数据访问

**完整类名**: `com.example.platform.mapper.AlertMapper`
**继承**: `BaseMapper<Alert>`

> 当前仅继承通用方法，无自定义方法。

---

## 三、使用示例

### 示例 1：基础 CRUD 操作

```java
@Autowired
private DeviceMapper deviceMapper;

// 查询
Device device = deviceMapper.selectById(1);
List<Device> allDevices = deviceMapper.selectList(null);

// 插入
Device newDevice = new Device();
newDevice.setDeviceName("新设备");
newDevice.setDeviceCode("NEW-001");
deviceMapper.insert(newDevice);

// 更新
device.setLocation("新位置");
deviceMapper.updateById(device);

// 删除
deviceMapper.deleteById(1);
```

### 示例 2：条件构造器查询

```java
// 查询状态为在线的设备
LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Device::getStatus, 1)
       .like(Device::getDeviceName, "温度")
       .orderByDesc(Device::getCreateTime);
List<Device> devices = deviceMapper.selectList(wrapper);
```

### 示例 3：自定义方法调用

```java
// 模糊查询
List<Device> devices = deviceMapper.findByNameLike("传感器");

// 统计各状态数量
List<Map<String, Object>> stats = deviceMapper.countByStatus();
// 返回示例: [{status=0, count=5}, {status=1, count=3}, {status=2, count=1}]
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| Mapper 注入失败 | 未被 Spring 扫描到 | 确保启动类有 `@MapperScan("com.example.platform.mapper")` 注解 |
| SQL 日志不打印 | 无法看到执行的 SQL | `application.yaml` 已配置 `log-impl: StdOutImpl`，开发环境会打印 SQL |
| 自定义 SQL 报错 | SQL 语法错误或参数不匹配 | 检查 `@Select` 注解中的 SQL 语句，确保 `@Param` 参数名一致 |
| 分页查询不生效 | 未配置分页插件 | 启用 `MybatisPlusConfig` 中的分页拦截器 |
| XML Mapper 未加载 | 自定义 XML 文件未被扫描 | 确保 XML 文件位于 `resources/mapper/` 目录下 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
