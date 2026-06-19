# 📊 DAT数据采集模块总结性汇报

---

## 一、模块新增功能详细说明

### 1.1 REQ-DAT-FR-001：设备数据上报与告警触发

| 功能项             | 需求编号       | 实现说明                                                                                      |
| :----------------- | :------------- | :-------------------------------------------------------------------------------------------- |
| 单条数据上报       | REQ-DAT-FR-001 | `POST /device-data`，校验设备存在性后保存数据，更新设备在线时间，触发告警规则评估              |
| 批量数据上报       | REQ-DAT-FR-001 | `POST /device-data/batch`，通过`DeviceDataBatchDTO`接收列表，单次上限100条，逐条保存并触发告警 |
| 告警评估异常隔离   | REQ-DAT-FR-001 | `saveWithAlertCheck`中用try-catch包裹`alertRuleEngine.evaluate`，告警失败不影响数据保存        |
| 设备在线状态更新   | REQ-DAT-FR-001 | 数据上报成功后自动更新`last_online_time`和`status=1`                                           |
| 数据值范围校验     | REQ-DAT-FR-001 | `@DecimalMin(-999.99)` + `@DecimalMax(999.99)`注解校验，超出范围返回400错误                    |

### 1.2 REQ-DAT-FR-002：设备数据查询与统计

| 功能项             | 需求编号       | 实现说明                                                                                      |
| :----------------- | :------------- | :-------------------------------------------------------------------------------------------- |
| 分页查询           | REQ-DAT-FR-002 | `GET /device-data/page`，支持按设备ID可选筛选，按采集时间倒序分页                              |
| 设备历史数据查询   | REQ-DAT-FR-002 | `GET /device-data/device/{deviceId}`，按设备ID分页查询历史数据                                 |
| 最新数据查询       | REQ-DAT-FR-002 | `GET /device-data/device/{deviceId}/latest`，查询设备最新一条采集数据                          |
| 时间范围查询       | REQ-DAT-FR-002 | `GET /device-data/device/{deviceId}/range`，支持起止时间范围查询，最大跨度30天                 |
| 统计聚合           | REQ-DAT-FR-002 | `GET /device-data/device/{deviceId}/statistics`，返回count/min/max/avg统计值                   |
| 按小时/天/周聚合   | REQ-DAT-FR-002 | `GET /device-data/device/{deviceId}/aggregate`，支持hour/day/week三种粒度聚合                  |
| 时间范围校验       | REQ-DAT-FR-002 | `validateTimeRange`方法校验时间非空、起始不晚于结束、跨度不超过30天                            |

### 1.3 REQ-DAT-FR-003：数据导出（待实现）

| 功能项     | 需求编号       | 实现说明                                           |
| :--------- | :------------- | :------------------------------------------------- |
| Excel导出  | REQ-DAT-FR-003 | 需求已定义，当前未实现，优先级P2-一般               |

---

## 二、核心算法与实现思路

### 2.1 数据上报与告警触发流程

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Client      │     │  Controller      │     │  Service         │
│  POST /data  │────▶│  reportData()    │────▶│  saveWithAlert   │
│              │     │  @Validated校验   │     │  Check()         │
└──────────────┘     └──────────────────┘     └──────┬───────────┘
                                                      │
                                              ┌───────▼───────────┐
                                              │ ① getDeviceOrThrow │ ← 校验设备存在
                                              │ ② save(data)       │ ← 持久化数据
                                              │ ③ updateLastOnline │ ← 更新在线状态
                                              │ ④ evaluate()       │ ← 触发告警评估
                                              └───────┬───────────┘
                                                      │
                                     ┌────────────────┤
                                     │ 告警评估成功    │ 告警评估异常
                                     ▼                ▼
                              ┌────────────┐   ┌──────────────┐
                              │ 正常生成告警 │   │ log.error()  │
                              │ 或无告警    │   │ 不影响数据保存 │
                              └────────────┘   └──────────────┘
```

**关键设计决策**：
- 告警评估使用try-catch隔离，确保`evaluate()`抛出的任何异常都不会导致数据保存回滚
- `@Transactional`仅包裹数据保存操作，告警评估在事务提交后逻辑上已解耦（当前为同步调用，未来可改为异步）
- 批量上报采用逐条调用`saveWithAlertCheck`，保证每条数据独立处理，单条失败不影响其他

### 2.2 时间范围校验算法

```
输入: startTime, endTime

if startTime == null || endTime == null
  → 抛出 IllegalArgumentException("起始时间和结束时间不能为空")

if startTime > endTime
  → 抛出 IllegalArgumentException("起始时间不能晚于结束时间")

if Duration.between(startTime, endTime).toDays() > 30
  → 抛出 IllegalArgumentException("查询时间范围不能超过30天")

通过校验 → 执行查询
```

### 2.3 聚合统计SQL策略

```
按小时聚合: DATE_FORMAT(collect_time, '%Y-%m-%d %H:00:00') AS time_bucket
按天聚合:   DATE_FORMAT(collect_time, '%Y-%m-%d') AS time_bucket
按周聚合:   DATE_FORMAT(DATE_SUB(collect_time, INTERVAL WEEKDAY(collect_time) DAY), '%Y-%m-%d') AS time_bucket

每个粒度均计算: COUNT(*) AS count, MIN(data_value) AS min_val,
              MAX(data_value) AS max_val, AVG(data_value) AS avg_val
```

**周聚合说明**：`WEEKDAY(collect_time)`返回当前日期是本周第几天（0=周一），`DATE_SUB(..., INTERVAL WEEKDAY DAY)`将日期回退到本周一，实现按周分组。

---

## 三、代码结构与设计模式分析

### 3.1 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                       │
│  DeviceDataController                                     │
│  POST /device-data          → 单条上报                    │
│  POST /device-data/batch    → 批量上报                    │
│  GET  /device-data/page     → 分页查询                    │
│  GET  /device/{id}          → 设备历史数据                │
│  GET  /device/{id}/latest   → 最新数据                    │
│  GET  /device/{id}/range    → 时间范围查询                │
│  GET  /device/{id}/statistics → 统计信息                  │
│  GET  /device/{id}/aggregate  → 聚合统计                  │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Service Layer                          │
│  DeviceDataServiceImpl                                    │
│  saveWithAlertCheck()     → 保存+告警触发                 │
│  saveBatchWithAlertCheck() → 批量保存+告警触发            │
│  findByDeviceIdAndTimeRange() → 时间范围查询              │
│  getStatistics()          → 统计信息                      │
│  aggregate()              → 聚合统计                      │
│  validateTimeRange()      → 时间范围校验                  │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Mapper Layer                           │
│  DeviceDataMapper                                         │
│  getStatisticsByDeviceIdAndTimeRange() → XML映射SQL      │
│  aggregateByHour()  → @Select注解SQL                     │
│  aggregateByDay()   → @Select注解SQL                     │
│  aggregateByWeek()  → @Select注解SQL                     │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Entity Layer                           │
│  DeviceData (id, deviceId, metric, dataValue, collectTime)│
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    依赖组件                               │
│  AlertRuleEngine  → 告警规则评估引擎                      │
│  DeviceService    → 设备存在性校验与在线时间更新           │
└─────────────────────────────────────────────────────────┘
```

### 3.2 使用的设计模式

| 设计模式         | 应用位置                        | 说明                                                     |
| :--------------- | :------------------------------ | :------------------------------------------------------- |
| **模板方法**     | `ServiceImpl<Mapper, Entity>`   | 继承MyBatis-Plus的ServiceImpl，复用save/page/list等通用方法 |
| **策略模式**     | `aggregate()`的switch分支       | 根据granularity参数选择不同的聚合策略（hour/day/week）    |
| **依赖注入**     | 构造器注入AlertRuleEngine等     | 通过构造器注入依赖，保证不可变性和可测试性                |
| **异常隔离**     | `saveWithAlertCheck`的try-catch | 告警评估异常被捕获记录，不向上层传播，保证数据保存不受影响 |
| **防御性编程**   | `validateTimeRange`             | 对时间参数进行前置校验，快速失败避免无效数据库查询        |
| **DTO传输**     | `DeviceDataBatchDTO`            | 批量上报使用独立DTO封装，与实体类解耦                     |

### 3.3 新增/修改文件清单

| 文件                                                         | 行数 | 类型 | 说明                                         |
| :----------------------------------------------------------- | ---: | :--- | :------------------------------------------- |
| [DeviceDataServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/DeviceDataServiceImpl.java) |  190 | 修改 | 新增saveWithAlertCheck/saveBatchWithAlertCheck/aggregate/validateTimeRange |
| [DeviceDataController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/DeviceDataController.java) |  139 | 修改 | 新增aggregate接口                             |
| [DeviceDataMapper.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/mapper/DeviceDataMapper.java) |   70 | 修改 | 新增aggregateByHour/Day/Week三个@Select注解SQL |
| [DeviceDataService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/DeviceDataService.java) |   76 | 修改 | 新增aggregate方法声明                         |
| [DeviceServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/DeviceServiceImpl.java) |  184 | 修改 | 新增updateLastOnlineTime方法                  |
| [DeviceService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/DeviceService.java) |   50 | 修改 | 新增updateLastOnlineTime方法声明              |

---

## 四、接口清单

| HTTP方法 | 路径                                      | 功能             | 权限要求       |
| :------- | :---------------------------------------- | :--------------- | :------------- |
| POST     | `/device-data`                            | 单条数据上报     | OPERATOR+      |
| POST     | `/device-data/batch`                      | 批量数据上报     | OPERATOR+      |
| GET      | `/device-data/page`                       | 分页查询数据     | VIEWER+        |
| GET      | `/device-data/device/{deviceId}`          | 设备历史数据     | VIEWER+        |
| GET      | `/device-data/device/{deviceId}/latest`   | 最新数据查询     | VIEWER+        |
| GET      | `/device-data/device/{deviceId}/range`    | 时间范围查询     | VIEWER+        |
| GET      | `/device-data/device/{deviceId}/statistics` | 统计信息       | VIEWER+        |
| GET      | `/device-data/device/{deviceId}/aggregate`  | 聚合统计       | VIEWER+        |

---

## 五、待优化项

| 优先级 | 优化项                                       | 说明                                           |
| :----- | :------------------------------------------- | :--------------------------------------------- |
| P1     | 告警评估异步化                               | 当前为同步调用，高并发场景下影响接口响应速度     |
| P2     | REQ-DAT-FR-003数据导出功能                   | Excel导出功能待实现                             |
| P2     | 批量上报性能优化                             | 逐条save可优化为批量insert减少数据库交互次数     |
| P3     | 聚合统计结果缓存                             | 高频查询场景下可缓存聚合结果减少数据库压力       |
