# service — 业务逻辑层包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`service` 包是项目的业务逻辑层，封装核心业务规则和处理流程。采用接口 + 实现类的标准设计模式，接口定义业务能力，实现类提供具体逻辑。

### 核心作用

- **业务封装**：将复杂的业务逻辑从 Controller 中剥离，保持 Controller 简洁
- **事务管理**：通过 `@Transactional` 注解控制数据库事务
- **通用能力继承**：继承 `IService` 接口，自动获得基础 CRUD 方法
- **接口隔离**：面向接口编程，便于替换实现和单元测试

### 与其他模块的关系

```
Controller → 调用 Service 接口方法
Service    → 调用 Mapper 方法操作数据库
Service    → 使用 Entity 对象传递数据
```

### 目录结构

```
service/
├── AlertService.java           # 告警服务接口
├── DeviceDataService.java      # 设备数据服务接口
├── DeviceService.java          # 设备服务接口
├── DeviceTypeService.java      # 设备类型服务接口
└── impl/                       # 实现类目录
    ├── AlertServiceImpl.java
    ├── DeviceDataServiceImpl.java
    ├── DeviceServiceImpl.java
    └── DeviceTypeServiceImpl.java
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
| `findByNameLike(String name)` | `List<Device>` | `name`: 设备名称关键词 | 根据名称模糊查询设备 |
| `findOnlineDevices()` | `List<Device>` | 无 | 查询所有在线设备（status = 1） |
| `updateStatus(Integer id, Integer status)` | `boolean` | `id`: 设备ID, `status`: 新状态值 | 更新设备状态 |

#### 实现类

**完整类名**: `com.example.platform.service.impl.DeviceServiceImpl`
**继承**: `ServiceImpl<DeviceMapper, Device>`

| 方法 | 实现方式 | 说明 |
|------|----------|------|
| `findByNameLike` | 调用 `baseMapper.findByNameLike()` | 委托给 Mapper 层的自定义 SQL |
| `findOnlineDevices` | 使用 `LambdaQueryWrapper` | 构建 `status = 1` 条件查询 |
| `updateStatus` | 使用 `LambdaUpdateWrapper` | 构建按 ID 更新 status 的条件 |

### 2.3 DeviceDataService — 设备数据服务接口

**完整类名**: `com.example.platform.service.DeviceDataService`
**继承**: `IService<DeviceData>`

> 当前仅继承通用方法，无自定义方法。

**实现类**: `DeviceDataServiceImpl`

> ⚠️ **注意**：当前实现类缺少 `@Service` 注解，无法被 Spring 自动注入。使用前需添加 `@Service` 注解。

### 2.4 DeviceTypeService — 设备类型服务接口

**完整类名**: `com.example.platform.service.DeviceTypeService`
**继承**: `IService<DeviceType>`

> 当前仅继承通用方法，无自定义方法。

**实现类**: `DeviceTypeServiceImpl`

> ⚠️ **注意**：当前实现类缺少 `@Service` 注解，无法被 Spring 自动注入。使用前需添加 `@Service` 注解。

### 2.5 AlertService — 告警服务接口

**完整类名**: `com.example.platform.service.AlertService`
**继承**: `IService<Alert>`

> 当前仅继承通用方法，无自定义方法。

**实现类**: `AlertServiceImpl`

> ⚠️ **注意**：当前实现类缺少 `@Service` 注解，无法被 Spring 自动注入。使用前需添加 `@Service` 注解。

---

## 三、使用示例

### 示例 1：在 Controller 中注入并使用 Service

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

    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        return Result.success(deviceService.findOnlineDevices());
    }
}
```

### 示例 2：使用条件构造器进行复杂查询

```java
// 查询指定类型的在线设备
public List<Device> findOnlineByType(Integer typeId) {
    LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Device::getTypeId, typeId)
           .eq(Device::getStatus, 1);
    return deviceService.list(wrapper);
}
```

### 示例 3：批量操作与事务控制

```java
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Autowired
    private DeviceTypeService deviceTypeService;

    @Transactional(rollbackFor = Exception.class)
    public boolean addDeviceWithType(Device device, DeviceType type) {
        deviceTypeService.save(type);
        device.setTypeId(type.getId());
        return save(device);
    }
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| Service 注入失败 | 接口没有对应的实现类 | 确保实现类有 `@Service` 注解，且实现了对应接口 |
| 事务不生效 | 方法非 `public` 或同类内部调用 | 事务方法必须为 `public`，且不能在同类内部直接调用 |
| `baseMapper` 为 null | 实现类未正确继承 `ServiceImpl` | 确保继承 `ServiceImpl<XXXMapper, XXXEntity>` |
| 批量操作性能 | `saveBatch` 默认每批 1000 条 | 数据量大时可分批调用，避免内存溢出 |
| 接口与实现不一致 | 接口方法在实现类中未覆写 | 实现类必须使用 `@Override` 注解覆写接口方法 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
