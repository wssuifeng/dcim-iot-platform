# DAT数据采集模块学习指南

> **版本**: V1.0.0 | **创建**: 2026-05-24
>
> **适用对象**: 掌握Java基础与Spring Boot常用注解，但不熟悉数据采集与告警联动的开发者
>
> **关联需求**: REQ-DAT-FR-001（设备数据上报与告警触发）、REQ-DAT-FR-002（设备数据查询与统计）

---

## 1. 模块全景定位（宏观视角）

### 一句话概述

DAT模块就像一个**智能仓库的收货与盘点系统**——卡车运来货物（设备上报数据），仓库管理员先验货（校验设备存在性和数据合法性），然后入库（保存数据），同时通知质检部门抽检（触发告警评估）；之后你可以随时查库存（查询数据）、做盘点报表（统计聚合）。

### 核心职责清单

| # | 职责 | 对应生活类比 |
|---|:-----|:-------------|
| 1 | **数据上报接收**：接收单条或批量设备数据，校验后持久化 | 仓库收货验货入库 |
| 2 | **告警联动触发**：数据保存后自动触发告警规则评估 | 入库后通知质检抽检 |
| 3 | **设备在线更新**：数据上报成功后更新设备在线时间和状态 | 收到货物确认供应商在线 |
| 4 | **历史数据查询**：分页、时间范围、最新数据等多维度查询 | 查库存台账 |
| 5 | **统计聚合**：按小时/天/周聚合统计，计算min/max/avg | 做周报月报盘点 |

> **这5项职责的协作关系**：职责1是"入口"，职责2是"联动"，职责3是"状态同步"，职责4是"回溯"，职责5是"分析"。数据从入口进来，触发联动和状态同步，之后可被回溯和分析。

---

## 2. 技术盲区速查字典（扫清障碍）

### 2.1 核心概念

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **@Transactional** | 声明事务边界，方法内所有数据库操作要么全部成功，要么全部回滚 | 像银行转账：扣款和入账必须同时成功，任何一步失败就全部撤销 | DeviceDataServiceImpl.java `saveWithAlertCheck()` |
| **try-catch异常隔离** | 告警评估用try-catch包裹，评估失败只记日志不抛异常，保证数据保存不受影响 | 像快递签收后质检发现问题只记录不退货，包裹已经到手了 | DeviceDataServiceImpl.java `saveWithAlertCheck()` |
| **BigDecimal** | Java中用于精确表示和计算小数的类，避免double的精度丢失问题 | 像用算盘算账而不是用计算器，算盘不会出现0.1+0.2≠0.3的问题 | DeviceData.java `dataValue`字段 |
| **@DateTimeFormat** | Spring提供的注解，告诉框架怎么把字符串转成LocalDateTime对象 | 像翻译官，把"2026-05-24 10:00:00"这种文字翻译成Java能理解的日期对象 | DeviceDataController.java 查询参数 |
| **DATE_FORMAT** | MySQL函数，把日期按指定格式转成字符串，用于分组聚合 | 像把"2026-05-24 10:30:00"归类到"2026-05-24 10:00:00"这个小时桶里 | DeviceDataMapper.java `aggregateByHour()` |
| **WEEKDAY()** | MySQL函数，返回日期是本周第几天（0=周一，6=周日） | 像日历上标注"今天是周三"，用于按周分组 | DeviceDataMapper.java `aggregateByWeek()` |
| **LambdaQueryWrapper** | MyBatis-Plus提供的工具类，用Java代码构建WHERE条件，编译期能检查字段名 | 像填表模板，你只需在指定位置填值，不用手写SQL，也不会拼错字段名 | DeviceDataServiceImpl.java |

### 2.2 校验注解

| 注解 | 作用 | 生活类比 | 对应代码位置 |
|:-----|:-----|:---------|:-------------|
| **@DecimalMin** | 校验数值不小于指定值 | 最低消费限制 | DeviceData.java `dataValue` |
| **@DecimalMax** | 校验数值不大于指定值 | 最高限额 | DeviceData.java `dataValue` |
| **@NotEmpty** | 校验集合不为空（不能为null且不能为空列表） | 快递箱里至少有一件物品 | DeviceDataBatchDTO.java `dataList` |
| **@Valid** | 级联校验，触发集合内每个元素的校验 | 每件快递都要验货，不是只验箱子 | DeviceDataBatchDTO.java `dataList` |
| **CreateGroup** | 自定义校验分组，只在创建时触发校验 | 入库时检查，出库时不检查 | DeviceData.java `deviceId`/`dataValue` |

---

## 3. 架构与数据流转（见森林）

### 3.1 模块架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    【第1层】HTTP 请求入口                       │
│                    (设备/前端发送 HTTP 请求)                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                【第2层】Controller 层                                      │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │ DeviceDataController                                               │   │
│  │ POST /device-data          → reportData()   单条上报              │   │
│  │ POST /device-data/batch    → reportBatch()  批量上报              │   │
│  │ GET  /device-data/page     → listByPage()   分页查询              │   │
│  │ GET  /device/{id}          → listByDevice() 历史数据              │   │
│  │ GET  /device/{id}/latest   → getLatest()    最新数据              │   │
│  │ GET  /device/{id}/range    → listByTimeRange() 时间范围查询       │   │
│  │ GET  /device/{id}/statistics → getStatistics() 统计信息           │   │
│  │ GET  /device/{id}/aggregate  → aggregate()    聚合统计            │   │
│  └──────────────────────────┬─────────────────────────────────────────┘   │
└─────────────────────────────┼─────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────────────────┐
│                   【第3层】Service 层                                      │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │ DeviceDataServiceImpl                                               │   │
│  │ saveWithAlertCheck()        → 保存+触发告警+更新在线状态            │   │
│  │ saveBatchWithAlertCheck()   → 批量保存（逐条调用上面方法）          │   │
│  │ findByDeviceIdAndTimeRange() → 时间范围查询（含校验）               │   │
│  │ getStatistics()             → 统计信息（含设备校验）                │   │
│  │ aggregate()                 → 聚合统计（switch选策略）             │   │
│  │ validateTimeRange()         → 时间范围校验（私有方法）              │   │
│  └──────────┬────────────┬──────────────────────────────────────────┘   │
│             │            │                                                │
│             ▼            ▼                                                │
│  ┌──────────────┐  ┌──────────────────┐                                  │
│  │AlertRuleEngine│  │ DeviceService    │                                  │
│  │evaluate()    │  │getDeviceOrThrow()│                                  │
│  │(告警评估)     │  │updateLastOnline  │                                  │
│  └──────────────┘  └──────────────────┘                                  │
└───────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────┐
│                   【第4层】Mapper / 数据库层                                │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │ DeviceDataMapper                                                    │   │
│  │ getStatisticsByDeviceIdAndTimeRange() → XML映射SQL                  │   │
│  │ aggregateByHour()  → @Select注解SQL（按小时GROUP BY）               │   │
│  │ aggregateByDay()   → @Select注解SQL（按天GROUP BY）                 │   │
│  │ aggregateByWeek()  → @Select注解SQL（按周GROUP BY）                 │   │
│  └──────────────────────────┬─────────────────────────────────────────┘   │
│                             │                                              │
│                             ▼                                              │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │                     device_data 表                                  │   │
│  │ id | device_id | metric | data_value | collect_time                │   │
│  └────────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心数据流转图：一次完整的数据上报请求

```
【阶段A：前端发请求】

① 前端发送请求
   POST /api/device-data
   Body: { "deviceId": 1, "metric": "temperature", "dataValue": 36.5 }
          │
          ▼
【阶段B：Controller接收请求】

② DeviceDataController.reportData()                  [DeviceDataController.java 第67行]
   - @Validated(CreateGroup.class) 触发参数校验
   - 调用 deviceDataService.saveWithAlertCheck(deviceData)
          │
          ▼
【阶段C：Service执行核心流程】

③ DeviceDataServiceImpl.saveWithAlertCheck()          [DeviceDataServiceImpl.java 第120行]
   ┌─────────────────────────────────────────────────────────────┐
   │ Step 1: deviceService.getDeviceOrThrow(deviceId)            │
   │         → 校验设备存在，不存在抛DeviceNotFoundException      │
   │                                                              │
   │ Step 2: save(deviceData)                                     │
   │         → MyBatis-Plus执行INSERT，collect_time自动填充       │
   │                                                              │
   │ Step 3: deviceService.updateLastOnlineTime(deviceId)         │
   │         → UPDATE devices SET last_online_time=NOW(), status=1│
   │                                                              │
   │ Step 4: try {                                                │
   │           alertRuleEngine.evaluate(deviceId, deviceData)     │
   │         } catch (Exception e) {                              │
   │           log.error("告警评估失败...")                        │
   │         }                                                    │
   │         → 告警评估异常被吞掉，不影响事务提交                  │
   └─────────────────────────────────────────────────────────────┘
          │
          ▼
【阶段D：返回结果】

④ 返回 Result.success() → 前端收到成功响应
```

### 3.3 聚合统计流转图

```
前端请求: GET /device-data/device/1/aggregate?startTime=...&endTime=...&granularity=day
          │
          ▼
Controller.aggregate()  → deviceService.getDeviceOrThrow(1)  校验设备存在
          │
          ▼
Service.aggregate()     → validateTimeRange(startTime, endTime)  校验时间范围
          │
          ▼
switch(granularity) {
   "hour" → mapper.aggregateByHour(1, start, end)
            SQL: GROUP BY DATE_FORMAT(collect_time, '%Y-%m-%d %H:00:00')
   "day"  → mapper.aggregateByDay(1, start, end)
            SQL: GROUP BY DATE_FORMAT(collect_time, '%Y-%m-%d')
   "week" → mapper.aggregateByWeek(1, start, end)
            SQL: GROUP BY DATE_FORMAT(DATE_SUB(collect_time, INTERVAL WEEKDAY(collect_time) DAY), '%Y-%m-%d')
}
          │
          ▼
返回 List<Map<String, Object>>
每个Map: { time_bucket: "2026-05-24", count: 120, min_val: 18.5, max_val: 36.8, avg_val: 25.3 }
```

---

## 4. 关键代码逐行解读（见树木）

### 4.1 saveWithAlertCheck —— 数据上报核心方法

```java
@Transactional(rollbackFor = Exception.class)   // ① 声明事务，任何Exception都回滚
@Override
public void saveWithAlertCheck(DeviceData deviceData) {
    deviceService.getDeviceOrThrow(deviceData.getDeviceId());  // ② 校验设备存在

    save(deviceData);  // ③ 调用ServiceImpl.save()执行INSERT

    deviceService.updateLastOnlineTime(deviceData.getDeviceId());  // ④ 更新在线状态

    try {
        alertRuleEngine.evaluate(deviceData.getDeviceId(), deviceData);  // ⑤ 触发告警评估
    } catch (Exception e) {
        log.error("告警评估失败，设备ID={}，数据ID={}：{}",
            deviceData.getDeviceId(), deviceData.getId(), e.getMessage(), e);  // ⑥ 仅记录日志
    }
}
```

**逐行要点**：
- ① `rollbackFor = Exception.class`：默认Spring只回滚RuntimeException，这里显式指定所有异常都回滚
- ② 先校验后操作，避免脏数据进入数据库
- ③ `save()`来自父类`ServiceImpl`，执行`INSERT INTO device_data ...`
- ④ 更新设备在线时间和状态为在线（status=1），用`LambdaUpdateWrapper`避免查出整个设备对象
- ⑤ ⑥ **这是最关键的设计**：try-catch隔离了告警评估，即使`evaluate()`内部抛出任何异常，事务仍然正常提交，数据不会丢失

### 4.2 validateTimeRange —— 时间范围校验

```java
private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    if (startTime == null || endTime == null) {          // ① 空值防御
        throw new IllegalArgumentException("起始时间和结束时间不能为空");
    }
    if (startTime.isAfter(endTime)) {                     // ② 逻辑顺序校验
        throw new IllegalArgumentException("起始时间不能晚于结束时间");
    }
    if (java.time.Duration.between(startTime, endTime)    // ③ 跨度限制
            .toDays() > MAX_TIME_RANGE_DAYS) {
        throw new IllegalArgumentException("查询时间范围不能超过" + MAX_TIME_RANGE_DAYS + "天");
    }
}
```

**设计思路**：三层校验——空值→逻辑顺序→业务限制，从最基础的校验到最复杂的校验，快速失败。

### 4.3 aggregate —— 聚合策略选择

```java
@Override
public List<Map<String, Object>> aggregate(Integer deviceId, LocalDateTime startTime,
                                            LocalDateTime endTime, String granularity) {
    validateTimeRange(startTime, endTime);          // ① 复用时间校验
    deviceService.getDeviceOrThrow(deviceId);       // ② 校验设备存在
    return switch (granularity.toLowerCase()) {      // ③ Java 17 switch表达式
        case "hour" -> baseMapper.aggregateByHour(deviceId, startTime, endTime);
        case "day"  -> baseMapper.aggregateByDay(deviceId, startTime, endTime);
        case "week" -> baseMapper.aggregateByWeek(deviceId, startTime, endTime);
        default -> throw new IllegalArgumentException(  // ④ 非法粒度快速失败
            "不支持的聚合粒度: " + granularity + "，可选值: hour/day/week");
    };
}
```

**设计思路**：策略模式简化版——用switch替代策略接口，因为聚合策略只有3种且不会频繁扩展，过度设计反而增加复杂度。

---

## 5. 常见问题与踩坑记录

### Q1: 为什么告警评估要用try-catch包裹，而不是让异常自然传播？

**答**：因为数据保存和告警评估是两个独立的关注点。数据保存是核心业务，必须保证成功；告警评估是附加功能，失败不应影响核心业务。如果让异常传播，`@Transactional`会捕获到异常并回滚事务，导致已保存的数据被撤销。

### Q2: 批量上报为什么是逐条调用saveWithAlertCheck，而不是一次性批量INSERT？

**答**：当前实现优先保证功能正确性和告警触发的完整性。逐条调用确保每条数据都经过完整的校验→保存→在线更新→告警评估流程。未来优化方向是：先批量INSERT，再批量更新在线时间，最后异步触发告警评估。

### Q3: 聚合统计为什么返回`List<Map<String, Object>>`而不是自定义DTO？

**答**：聚合统计的返回结构是动态的（time_bucket格式随粒度变化），使用Map更灵活。如果未来需要强类型约束，可以定义`AggregateResultDTO`包含timeBucket/count/minVal/maxVal/avgVal字段。

### Q4: 时间范围为什么限制30天？

**答**：防止用户发起超大范围查询导致数据库性能下降。30天是经验值，覆盖了绝大多数监控场景的查询需求。如果需要更长时间范围的分析，应使用专门的数据仓库或时序数据库。

---

## 6. 沙盒演练

### 演练1：添加一个新的聚合粒度"month"

**目标**：在现有hour/day/week基础上，新增按月聚合统计功能。

**步骤**：
1. 在`DeviceDataMapper.java`中新增方法：
```java
@Select("SELECT DATE_FORMAT(collect_time, '%Y-%m') AS time_bucket, " +
        "COUNT(*) AS count, MIN(data_value) AS min_val, MAX(data_value) AS max_val, AVG(data_value) AS avg_val " +
        "FROM device_data WHERE device_id = #{deviceId} " +
        "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
        "GROUP BY time_bucket ORDER BY time_bucket ASC")
List<Map<String, Object>> aggregateByMonth(@Param("deviceId") Integer deviceId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
```
2. 在`DeviceDataServiceImpl.aggregate()`的switch中添加`case "month"`分支
3. 验证：启动项目，调用`GET /device-data/device/1/aggregate?granularity=month&startTime=...&endTime=...`

### 演练2：修改批量上报上限从100改为200

**目标**：将`MAX_BATCH_SIZE`从100调整为200。

**步骤**：
1. 修改`DeviceDataServiceImpl.java`中的常量：`private static final int MAX_BATCH_SIZE = 200;`
2. 同步修改`DeviceDataBatchDTO.java`中的`@Size`注解（如果有）
3. 验证：发送201条数据的批量请求，应返回400错误

### 演练3：为统计接口增加缓存

**目标**：对`getStatistics()`的结果进行5分钟缓存，减少数据库查询。

**步骤**：
1. 在`DeviceDataServiceImpl`中注入`ConcurrentHashMap<String, CacheEntry>`作为本地缓存
2. 定义`CacheEntry`包含统计数据和时间戳
3. 在`getStatistics()`中先查缓存，5分钟内直接返回，否则查数据库并更新缓存
4. 验证：连续两次调用统计接口，第二次应命中缓存（可通过日志确认）
