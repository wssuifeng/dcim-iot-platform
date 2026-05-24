# CFG配置模块学习指南

> **版本**: V1.0.0 | **创建**: 2026-05-24
>
> **适用对象**: 掌握Java基础与Spring Boot常用注解，但不熟悉CRUD完整性设计与缓存一致性的开发者
>
> **关联需求**: REQ-CFG-FR-001（设备类型完整CRUD）、REQ-CFG-FR-002（指标字典管理与缓存）

---

## 1. 模块全景定位（宏观视角）

### 一句话概述

CFG模块就像一个**企业的人事与字典管理系统**——人事部门管理岗位类型（设备类型CRUD），新增岗位时检查名称不重复（编码唯一性校验），删除岗位时检查是否还有人在岗（关联检查）；字典管理部门维护公司术语表（指标字典），大家查术语时先查公告栏（缓存），公告栏没有再翻原始文件（数据库），术语更新后公告栏同步刷新（缓存一致性）。

### 核心职责清单

| # | 职责 | 对应生活类比 |
|---|:-----|:-------------|
| 1 | **设备类型CRUD**：完整的增删改查6个接口 | 人事管理岗位类型 |
| 2 | **编码唯一性校验**：新增/更新时校验typeCode不重复 | 岗位编号不能重复 |
| 3 | **删除关联检查**：删除类型前检查是否有设备引用 | 删岗位前检查是否有人在职 |
| 4 | **指标字典缓存**：ConcurrentHashMap内存缓存+数据库回填 | 公告栏+原始文件双查 |
| 5 | **缓存一致性**：CRUD操作后自动维护缓存 | 术语更新后公告栏同步刷新 |

> **这5项职责的协作关系**：职责1是"核心操作"，职责2和3是"安全保障"（保证数据合法性和完整性），职责4和5是"性能优化"（减少数据库查询）。

---

## 2. 技术盲区速查字典（扫清障碍）

### 2.1 核心概念

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **ConcurrentHashMap** | 线程安全的Map实现，用作指标字典的内存缓存 | 像一个多线程安全的公告栏，多人同时查看和更新不会出错 | DictMetricServiceImpl.java `metricCache` |
| **@PostConstruct** | Bean初始化完成后自动执行的方法，本模块用于启动时加载缓存 | 像公司开门前先把术语表贴到公告栏上 | DictMetricServiceImpl.java `initCache()` |
| **方法重写(Override)** | 子类重新定义父类的方法，在原逻辑前后添加额外操作 | 像在标准流程中加了自己的步骤：先走标准流程，再额外更新公告栏 | DictMetricServiceImpl.java `save()/updateById()/removeById()` |
| **super.save()** | 调用父类的save方法，执行MyBatis-Plus的标准INSERT操作 | 像说"按原来的方式做"，不改变原有逻辑 | DictMetricServiceImpl.java `save()` |
| **选择性更新** | 只更新DTO中非null的字段，避免覆盖未传入的字段 | 像修改简历只改了电话号码，其他信息保持不变 | DeviceTypeServiceImpl.java `updateById()` |
| **excludeId** | 唯一性校验时排除自身ID，避免更新自己时误判重复 | 像改自己名字时，系统不会拿新名字和自己的旧名字比 | DeviceTypeServiceImpl.java `validateTypeCodeUnique()` |
| **JacksonTypeHandler** | MyBatis-Plus的类型处理器，自动将Java对象与JSON字符串互转 | 像翻译官，数据库存JSON字符串，Java用对象，翻译官负责双向转换 | DeviceType.java `alertRuleConfig`字段 |
| **@TableField(fill)** | MyBatis-Plus注解，指定字段在INSERT或UPDATE时自动填充 | 像表单上的"日期"栏自动填今天日期，不用手写 | DeviceType.java `createTime`/`updateTime` |

### 2.2 缓存一致性策略对比

| 策略 | 适用场景 | 本模块使用位置 | 优缺点 |
|:-----|:---------|:---------------|:-------|
| **单条put** | 新增操作，只影响一条记录 | `save()`后 | 优点：性能高；缺点：如果新增的metricKey与已有记录冲突（理论上不可能），缓存不会更新 |
| **全量refreshCache** | 更新操作，可能影响多条记录 | `updateById()`后 | 优点：安全可靠；缺点：全量加载有性能开销 |
| **单条remove** | 删除操作，只影响一条记录 | `removeById()`后 | 优点：精确移除；缺点：需要先查出原记录的metricKey |

### 2.3 异常体系

| 异常类 | 触发场景 | HTTP状态码 | 错误码 |
|:-------|:---------|:-----------|:-------|
| DeviceTypeNotFound | 类型ID不存在 | 404 | 4041 |
| DeviceTypeCodeAlreadyExistsException | 类型编码重复 | 400 | 4051 |
| IllegalStateException | 删除时存在关联设备 | 400 | 无（使用默认） |

---

## 3. 架构与数据流转（见森林）

### 3.1 模块架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    【第1层】HTTP 请求入口                       │
│                    (管理员发送 HTTP 请求)                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                【第2层】Controller 层                                      │
│  ┌──────────────────────────────┐  ┌────────────────────────────────┐    │
│  │ DeviceTypeController         │  │ DictMetricController           │    │
│  │ (已有，此处不展开)            │  │ (已有，此处不展开)              │    │
│  │ GET/POST/PUT/DELETE          │  │ GET/POST/PUT/DELETE            │    │
│  │ /device-types/**            │  │ /metric/**                     │    │
│  └──────────────┬───────────────┘  └──────────────┬─────────────────┘    │
└─────────────────┼──────────────────────────────────┼──────────────────────┘
                  │                                  │
┌─────────────────▼──────────────────────────────────▼──────────────────────┐
│                   【第3层】Service 层                                      │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ DeviceTypeServiceImpl                                             │     │
│  │ create()              → 校验编码唯一 + save                       │     │
│  │ updateById(Integer,DT)→ 选择性更新 + 校验编码唯一                 │     │
│  │ deleteById()          → 关联检查 + removeById                    │     │
│  │ validateTypeCodeUnique() → 编码唯一性校验（私有）                │     │
│  └──────────┬───────────────────────────────────────────────────────┘     │
│             │                                                              │
│             ▼                                                              │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ DictMetricServiceImpl                                             │     │
│  │ getFromCache()    → 缓存优先，未命中查DB回填                      │     │
│  │ save()            → super.save() + 缓存put                       │     │
│  │ updateById()      → super.updateById() + 缓存refreshCache        │     │
│  │ removeById()      → 查原记录 + super.removeById() + 缓存remove   │     │
│  │ initCache()       → @PostConstruct全量加载                        │     │
│  │ refreshCache()    → 清空 + 全量加载                               │     │
│  └──────────────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────┐
│                   【第4层】缓存层                                          │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ ConcurrentHashMap<String, DictMetric> metricCache                 │     │
│  │ Key: metricKey (如 "temperature")                                 │     │
│  │ Value: DictMetric对象 (metricKey + metricName + metricUnit + ...) │     │
│  └──────────────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────┐
│                   【第5层】Mapper / 数据库层                                │
│  ┌──────────────────┐  ┌──────────────────┐                              │
│  │ DeviceTypeMapper │  │ DictMetricMapper │                              │
│  │ (BaseMapper)     │  │ (BaseMapper)     │                              │
│  └────────┬─────────┘  └────────┬─────────┘                              │
│           │                     │                                         │
│           ▼                     ▼                                         │
│  ┌──────────────────┐  ┌──────────────────┐                               │
│  │  device_types 表 │  │  dict_metric 表  │                               │
│  └──────────────────┘  └──────────────────┘                               │
└───────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心数据流转图：设备类型新增

```
【阶段A：管理员发请求】

① 前端发送请求
   POST /api/device-types
   Body: { "typeName": "温度传感器", "typeCode": "TEMP_SENSOR", "description": "用于温度监测" }
          │
          ▼
【阶段B：Controller接收请求】

② DeviceTypeController.create()                     [DeviceTypeController.java 第38行]
   - @Validated @RequestBody 触发参数校验
   - 调用 deviceTypeService.create(deviceType)
          │
          ▼
【阶段C：Service执行核心流程】

③ DeviceTypeServiceImpl.create()                    [DeviceTypeServiceImpl.java 第55行]
   ┌─────────────────────────────────────────────────────────────┐
   │ Step 1: validateTypeCodeUnique(typeCode, null)               │
   │         → 查询 device_types WHERE type_code = 'TEMP_SENSOR' │
   │         → count = 0 → 校验通过                               │
   │                                                              │
   │ Step 2: save(deviceType)                                     │
   │         → MyBatis-Plus执行INSERT                             │
   │         → create_time/update_time自动填充                     │
   │                                                              │
   │ Step 3: return deviceType (含自增ID)                         │
   └─────────────────────────────────────────────────────────────┘
          │
          ▼
【阶段D：返回结果】

④ 返回 Result.success(deviceType) → 前端收到新建的类型对象（含ID）
```

### 3.3 核心数据流转图：设备类型删除

```
前端请求: DELETE /api/device-types/3
          │
          ▼
DeviceTypeServiceImpl.deleteById(3)
   ┌─────────────────────────────────────────────────────────────┐
   │ Step 1: getTypeOrThrow(3)                                     │
   │         → getById(3) → 类型存在                               │
   │                                                              │
   │ Step 2: deviceService.findByTypeId(3)                        │
   │         → SELECT * FROM devices WHERE type_id = 3            │
   │         → 结果: [Device(id=5), Device(id=12)]                │
   │         → 关联设备数 = 2                                      │
   │                                                              │
   │ Step 3: 关联设备非空 → 抛出IllegalStateException              │
   │         "该类型下存在2个关联设备，无法删除"                     │
   └─────────────────────────────────────────────────────────────┘
          │
          ▼
GlobalExceptionHandler → 返回 Result.error(400, "该类型下存在2个关联设备，无法删除")
```

### 3.4 核心数据流转图：指标字典缓存读取

```
AlertRuleEngine.buildAlertMessage() 调用 dictMetricService.getFromCache("temperature")
          │
          ▼
DictMetricServiceImpl.getFromCache("temperature")
   ┌─────────────────────────────────────────────────────────────┐
   │ Step 1: metricCache.get("temperature")                       │
   │         → 缓存命中 → 直接返回DictMetric对象                   │
   │                                                              │
   │ Step 2 (缓存未命中时):                                        │
   │         → SELECT * FROM dict_metric WHERE metric_key = 'temperature' │
   │         → 查到 → metricCache.put("temperature", metric)      │
   │         → 返回DictMetric对象                                  │
   │                                                              │
   │ Step 3 (数据库也未查到时):                                     │
   │         → 返回null（不缓存null，避免缓存穿透）                 │
   └─────────────────────────────────────────────────────────────┘
```

---

## 4. 关键代码逐行解读（见树木）

### 4.1 validateTypeCodeUnique —— 编码唯一性校验

```java
private void validateTypeCodeUnique(String typeCode, Integer excludeId) {
    if (typeCode == null || typeCode.trim().isEmpty()) {  // ① 空值跳过校验
        return;
    }
    LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DeviceType::getTypeCode, typeCode);        // ② WHERE type_code = ?
    if (excludeId != null) {
        wrapper.ne(DeviceType::getId, excludeId);         // ③ AND id != excludeId
    }
    if (count(wrapper) > 0) {                             // ④ 查询计数
        throw new DeviceTypeCodeAlreadyExistsException(typeCode);  // ⑤ 重复则抛异常
    }
}
```

**逐行要点**：
- ① 空编码不校验——这是业务决策，允许编码为空（后续可增加必填校验）
- ③ `ne(DeviceType::getId, excludeId)`：更新时排除自身，这是最关键的逻辑
- ④ `count`而不是`getOne`：只关心是否存在，不关心具体记录，count性能更优

### 4.2 updateById —— 选择性更新

```java
@Override
public DeviceType updateById(Integer id, DeviceType deviceType) {
    DeviceType existing = getTypeOrThrow(id);             // ① 先查出已有记录
    if (deviceType.getTypeCode() != null) {               // ② 非null才更新
        validateTypeCodeUnique(deviceType.getTypeCode(), id);  // ③ 校验编码唯一
        existing.setTypeCode(deviceType.getTypeCode());   // ④ 设置新值
    }
    if (deviceType.getTypeName() != null) {
        existing.setTypeName(deviceType.getTypeName());
    }
    if (deviceType.getDescription() != null) {
        existing.setDescription(deviceType.getDescription());
    }
    if (deviceType.getAlertRuleConfig() != null) {
        existing.setAlertRuleConfig(deviceType.getAlertRuleConfig());
    }
    updateById(existing);                                 // ⑤ 调用父类方法
    return existing;
}
```

**设计思路**：选择性更新（Partial Update）——只更新前端传入的非null字段。这避免了"传入null就清空字段"的问题，前端只需传需要修改的字段即可。

**注意**：方法签名是`updateById(Integer id, DeviceType deviceType)`，有两个参数，与父类`IService.updateById(T entity)`不同，不会递归调用。

### 4.3 deleteById —— 关联检查删除

```java
@Override
public void deleteById(Integer id) {
    getTypeOrThrow(id);                                   // ① 校验类型存在
    List<Device> associatedDevices = deviceService.findByTypeId(id);  // ② 查关联设备
    if (!associatedDevices.isEmpty()) {                   // ③ 存在关联
        throw new IllegalStateException(                  // ④ 拒绝删除
            "该类型下存在" + associatedDevices.size() + "个关联设备，无法删除");
    }
    removeById(id);                                       // ⑤ 安全删除
}
```

**设计思路**：先检查后操作（Check-then-Act）模式。在并发场景下，理论上可能在检查和删除之间有新设备关联到该类型，但当前业务场景下这种竞态条件概率极低，不需要加锁。

### 4.4 DictMetricServiceImpl —— 缓存一致性维护

```java
@Override
public boolean save(DictMetric entity) {
    boolean result = super.save(entity);                  // ① 先执行标准INSERT
    if (result && entity.getMetricKey() != null) {
        metricCache.put(entity.getMetricKey(), entity);   // ② 成功后加入缓存
    }
    return result;
}

@Override
public boolean updateById(DictMetric entity) {
    boolean result = super.updateById(entity);            // ③ 先执行标准UPDATE
    if (result) {
        refreshCache();                                   // ④ 成功后全量刷新
    }
    return result;
}

@Override
public boolean removeById(Integer id) {
    DictMetric existing = getById(id);                    // ⑤ 先查出原记录
    boolean result = super.removeById(id);                // ⑥ 执行标准DELETE
    if (result && existing != null && existing.getMetricKey() != null) {
        metricCache.remove(existing.getMetricKey());      // ⑦ 从缓存移除
    }
    return result;
}
```

**关键要点**：
- 所有方法都是"先执行数据库操作，成功后再更新缓存"——如果数据库操作失败，缓存不会被错误更新
- `removeById`需要先查出原记录的metricKey，因为删除后无法再获取
- `updateById`使用全量刷新而非单条更新，因为更新可能改变metricKey

---

## 5. 常见问题与踩坑记录

### Q1: 为什么DeviceTypeServiceImpl.updateById方法签名是`(Integer, DeviceType)`而不是父类的`(DeviceType)`？

**答**：这是有意为之的设计。父类`IService.updateById(T entity)`接收一个实体对象（通过ID匹配更新），而我们的方法接收ID和实体两个参数，实现选择性更新。两个方法签名不同，不会产生递归调用。方法内部调用`updateById(existing)`时，因为`existing`是单个参数，实际调用的是父类的方法。

### Q2: 为什么删除关联检查不用数据库外键约束？

**答**：外键约束虽然能保证数据完整性，但有以下缺点：1）外键检查影响写入性能；2）外键导致的错误信息不够友好（数据库报错vs业务异常）；3）微服务架构下跨库无法使用外键。当前方案在Service层做检查，可以返回清晰的业务提示。

### Q3: ConcurrentHashMap缓存有什么局限性？

**答**：三个主要局限：1）单实例限制——每个JVM有独立的缓存副本，多实例部署时缓存不一致；2）无TTL过期——缓存永不过期，只能手动刷新；3）无淘汰策略——缓存大小无限增长。这些问题将在Redis缓存模块（REQ-CACHE-FR-002）中解决。

### Q4: 为什么getFromCache不缓存null值？

**答**：当前指标字典数据量小，null值（不存在的metricKey）被频繁查询的概率低，不需要缓存null值防止缓存穿透。如果未来出现缓存穿透问题，可以缓存一个特殊的空对象标记。

---

## 6. 沙盒演练

### 演练1：为typeCode添加正则校验

**目标**：限制typeCode只能包含大写字母、数字和下划线。

**步骤**：
1. 在`DeviceType.java`的typeCode字段上添加注解：
```java
@Pattern(regexp = "^[A-Z0-9_]+$", message = "类型编码只能包含大写字母、数字和下划线")
@TableField("type_code")
private String typeCode;
```
2. 在`DeviceTypeController.create()`的`@RequestBody`前添加`@Validated`
3. 验证：提交typeCode为"temp-sensor"的请求，应返回400错误

### 演练2：为删除操作添加逻辑删除

**目标**：将物理删除改为逻辑删除（设置deleted字段而非真正删除记录）。

**步骤**：
1. 在数据库执行：`ALTER TABLE device_types ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'`
2. 在`DeviceType.java`中添加字段：
```java
@TableLogic(value = "0", delval = "1")
private Integer deleted;
```
3. 在`application.yml`中配置：`mybatis-plus.global-config.db-config.logic-delete-field: deleted`
4. 验证：删除一个类型后，数据库中该记录的deleted字段变为1，查询接口不再返回该记录

### 演练3：为缓存添加TTL过期

**目标**：为ConcurrentHashMap缓存添加5分钟过期策略。

**步骤**：
1. 创建`CacheEntry<T>`类，包含value和expireTime字段：
```java
@Data
private static class CacheEntry<T> {
    private T value;
    private long expireTime;
    boolean isExpired() { return System.currentTimeMillis() > expireTime; }
}
```
2. 修改`metricCache`类型为`ConcurrentHashMap<String, CacheEntry<DictMetric>>`
3. 在`getFromCache`中检查过期：
```java
CacheEntry<DictMetric> entry = metricCache.get(metricKey);
if (entry != null && !entry.isExpired()) return entry.getValue();
// 过期或不存在，查数据库并回填
```
4. 验证：5分钟后再次查询，应从数据库重新加载
