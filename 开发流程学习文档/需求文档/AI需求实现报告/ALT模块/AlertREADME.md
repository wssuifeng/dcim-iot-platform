# 📊 ALT告警模块总结性汇报

---

## 一、模块新增功能详细说明

### 1.1 REQ-ALT-FR-001：告警规则引擎评估

| 功能项             | 需求编号       | 实现说明                                                                                      |
| :----------------- | :------------- | :-------------------------------------------------------------------------------------------- |
| 规则配置优先级     | REQ-ALT-FR-001 | 设备级配置优先，类型级配置降级，通过`getEffectiveRuleConfig`实现两级降级策略                    |
| 指标规则匹配       | REQ-ALT-FR-001 | 遍历规则列表，按`metricKey`精确匹配当前数据指标                                                |
| 阈值比较运算       | REQ-ALT-FR-001 | 支持5种运算符：GT(大于)/LT(小于)/GTE(大于等于)/LTE(小于等于)/EQ(等于)，switch表达式实现         |
| 告警生成           | REQ-ALT-FR-001 | 无已有同类型未处理告警时，创建新告警记录                                                        |
| 告警升级           | REQ-ALT-FR-001 | 已有同类型未处理告警且新规则级别更紧急时，升级告警级别和消息                                    |
| 指标字典集成       | REQ-ALT-FR-001 | `buildAlertMessage`从DictMetric缓存获取指标中文名和单位，丰富告警消息内容                       |
| 消息模板替换       | REQ-ALT-FR-001 | 支持`{metricName}/{unit}/{value}/{threshold}`四种占位符，无模板时使用默认格式                   |

### 1.2 REQ-ALT-FR-002：告警管理与处理

| 功能项             | 需求编号       | 实现说明                                                                                      |
| :----------------- | :------------- | :-------------------------------------------------------------------------------------------- |
| 告警列表分页查询   | REQ-ALT-FR-002 | `GET /alerts`，支持按设备ID/处理状态/告警级别/时间范围多条件筛选分页                            |
| 告警详情查询       | REQ-ALT-FR-002 | `GET /alerts/{id}`，根据ID查询单条告警详情                                                     |
| 未处理告警查询     | REQ-ALT-FR-002 | `GET /alerts/unhandled`，查询所有is_handled=0的告警                                            |
| 批量处理告警       | REQ-ALT-FR-002 | `PUT /alerts/batch-handle`，批量标记告警为已处理，单次上限50条                                 |
| 设备告警统计       | REQ-ALT-FR-002 | `GET /alerts/stats/device/{deviceId}`，按设备统计各级别告警数量                                |
| 时间范围告警统计   | REQ-ALT-FR-002 | `GET /alerts/stats/time-range`，按时间段统计全局告警数量                                       |
| 设备告警排行       | REQ-ALT-FR-002 | `GET /alerts/stats/device-ranking`，按时间段统计各设备告警数量排行                             |
| 离线告警生成       | REQ-ALT-FR-002 | `createOfflineAlertsForDevices`，为离线设备批量创建离线告警，自动去重                           |

### 1.3 REQ-ALT-FR-003：告警通知推送（待实现）

| 功能项     | 需求编号       | 实现说明                                           |
| :--------- | :------------- | :------------------------------------------------- |
| 邮件通知   | REQ-ALT-FR-003 | 需求已定义，当前未实现，优先级P1-重要               |
| WebSocket  | REQ-ALT-FR-003 | 需求已定义，当前未实现，优先级P1-重要               |

---

## 二、核心算法与实现思路

### 2.1 告警规则引擎评估流程

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  DAT模块     │     │  AlertRuleEngine │     │  AlertService    │
│  evaluate()  │────▶│  评估引擎        │────▶│  持久化告警      │
└──────────────┘     └──────────────────┘     └──────────────────┘
                            │
                   ┌────────▼────────┐
                   │ Step 1: 获取生效 │
                   │ 规则配置         │
                   │ (设备级>类型级)   │
                   └────────┬────────┘
                            │ 有配置
                   ┌────────▼────────┐
                   │ Step 2: 匹配指标 │
                   │ 规则             │
                   │ metricKey==metric│
                   └────────┬────────┘
                            │ 匹配到
                   ┌────────▼────────┐
                   │ Step 3: 阈值比较 │
                   │ GT/LT/GTE/LTE/EQ│
                   └────────┬────────┘
                            │ 超阈值
                   ┌────────▼────────┐
                   │ Step 4: 生成/升级│
                   │ 告警             │
                   └─────────────────┘
```

### 2.2 告警生成与升级算法

```
输入: deviceId, matchedRule, deviceData

existingAlerts = 查找该设备同metricKey且未处理的告警

if existingAlerts 为空:
    → 创建新告警
      Alert:
        deviceId = 设备ID
        alertType = rule.metricKey
        alertLevel = rule.level
        alertMessage = buildAlertMessage(rule, deviceData)
        isHandled = 0

else:
    existingAlert = existingAlerts[0]
    if rule.level < existingAlert.alertLevel:   // 级别数字越小越紧急
        → 升级告警
          oldLevel = existingAlert.alertLevel    // 先保存旧级别
          existingAlert.alertLevel = rule.level
          existingAlert.alertMessage = buildAlertMessage(rule, deviceData)
          updateById(existingAlert)
          log("告警升级: 旧级别={}, 新级别={}", oldLevel, rule.level)
```

**关键设计决策**：
- 告警级别数字越小越紧急（1=紧急，5=提示），所以升级条件是`rule.level < existingAlert.alertLevel`
- 同一设备同一指标只保留一条未处理告警，避免告警风暴
- 升级时先保存旧级别再set新级别，确保日志记录正确

### 2.3 告警消息构建算法

```
输入: rule, deviceData

metricName = rule.metricKey          // 默认使用原始指标标识
metricUnit = ""                      // 默认无单位

try:
    metricInfo = dictMetricService.getFromCache(rule.metricKey)
    if metricInfo != null:
        if metricInfo.metricName 非空:
            metricName = metricInfo.metricName    // 替换为中文名
        if metricInfo.metricUnit 非空:
            metricUnit = metricInfo.metricUnit    // 替换为单位
catch:
    log.warn("获取指标字典失败，使用原始metricKey")

if rule.messageTemplate 非空:
    return template
        .replace("{value}", dataValue)
        .replace("{threshold}", threshold)
        .replace("{metricName}", metricName)
        .replace("{unit}", metricUnit)
else:
    return metricName + "当前值" + dataValue + metricUnit + "，超过阈值" + threshold + metricUnit
```

### 2.4 批量处理告警算法

```
输入: ids列表, handleUser

if ids 为空 → return 0
if ids.size > 50 → 抛出异常

UPDATE alerts
SET is_handled = 1,
    handle_time = NOW(),
    handle_user = handleUser
WHERE id IN (ids)
  AND is_handled = 0        ← 只处理未处理的告警

return 受影响行数
```

**关键设计**：`eq(Alert::getIsHandled, 0)`确保已处理告警不会被重复处理，即使前端传入了已处理的ID。

### 2.5 离线告警去重算法

```
输入: offlineDevices列表, offlineThresholdMinutes

1. 提取所有离线设备的ID列表 deviceIds
2. 查询这些设备中已有未处理离线告警的设备ID → alreadyAlertedDeviceIds
3. 过滤掉已报过的设备:
   newAlerts = offlineDevices.filter(d -> d.id NOT IN alreadyAlertedDeviceIds)
4. 为每个未报过的设备创建离线告警
5. saveBatch(newAlerts)
```

---

## 三、代码结构与设计模式分析

### 3.1 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Controller Layer                              │
│  AlertController                                                │
│  GET    /alerts                        → 多条件分页查询          │
│  GET    /alerts/{id}                   → 告警详情                │
│  GET    /alerts/unhandled              → 未处理告警列表          │
│  PUT    /alerts/batch-handle           → 批量处理                │
│  GET    /alerts/stats/device/{id}      → 设备告警统计            │
│  GET    /alerts/stats/time-range       → 时间范围统计            │
│  GET    /alerts/stats/device-ranking   → 设备告警排行            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    Service Layer                                 │
│  AlertServiceImpl          │  AlertRuleEngine                   │
│  findUnhandled()           │  evaluate()                        │
│  batchHandle()             │  getEffectiveRuleConfig()          │
│  getStatsByDeviceId()      │  isThresholdExceeded()             │
│  getStatsByTimeRange()     │  createOrUpgradeAlert()            │
│  createOfflineAlerts()     │  buildAlertMessage()               │
│  pageByCondition()         │                                    │
└───────────┬───────────────┴────────────┬───────────────────────┘
            │                            │
┌───────────▼──────────┐  ┌──────────────▼──────────────────────┐
│    Mapper Layer       │  │    依赖组件                          │
│  AlertMapper          │  │  DeviceService → 获取设备信息        │
│  getStatsByDeviceId() │  │  DeviceTypeService → 获取类型配置    │
│  getStatsByTimeRange()│  │  DictMetricService → 获取指标字典    │
│  listDeviceAlert...() │  │                                     │
└───────────┬──────────┘  └─────────────────────────────────────┘
            │
┌───────────▼──────────────────────────────────────────────────┐
│    Entity Layer                                               │
│  Alert (id, deviceId, alertType, alertLevel, alertMessage,   │
│         isHandled, handleTime, handleUser, createTime)        │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 使用的设计模式

| 设计模式         | 应用位置                        | 说明                                                     |
| :--------------- | :------------------------------ | :------------------------------------------------------- |
| **策略模式**     | `isThresholdExceeded`的switch   | 根据运算符类型选择不同的比较策略（GT/LT/GTE/LTE/EQ）     |
| **降级模式**     | `getEffectiveRuleConfig`        | 设备级配置优先，不存在时降级到类型级配置                  |
| **模板方法**     | `buildAlertMessage`             | 有模板时使用模板替换，无模板时使用默认格式                |
| **去重模式**     | `createOfflineAlertsForDevices` | 查询已有未处理告警的设备ID，过滤后只对新设备生成告警      |
| **幂等处理**     | `batchHandle`                   | WHERE条件包含`is_handled=0`，重复调用不会重复处理         |
| **依赖注入**     | 构造器注入                      | AlertRuleEngine通过`@RequiredArgsConstructor`注入4个依赖  |

### 3.3 新增/修改文件清单

| 文件                                                         | 行数 | 类型 | 说明                                         |
| :----------------------------------------------------------- | ---: | :--- | :------------------------------------------- |
| [AlertController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/AlertController.java) |   76 | 新增 | 告警管理7个REST接口                          |
| [AlertServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AlertServiceImpl.java) |  184 | 修改 | 新增batchHandle/getStats*/createOfflineAlerts/pageByCondition |
| [AlertService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/AlertService.java) |   34 | 修改 | 新增pageByCondition方法声明                  |
| [AlertRuleEngine.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AlertRuleEngine.java) |  180 | 修改 | 集成DictMetric缓存，增强buildAlertMessage    |
| [BatchHandleAlertDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/BatchHandleAlertDTO.java) |   25 | 新增 | 批量处理告警请求DTO                          |

---

## 四、接口清单

| HTTP方法 | 路径                                    | 功能             | 权限要求       |
| :------- | :-------------------------------------- | :--------------- | :------------- |
| GET      | `/alerts`                               | 多条件分页查询   | VIEWER+        |
| GET      | `/alerts/{id}`                          | 告警详情         | VIEWER+        |
| GET      | `/alerts/unhandled`                     | 未处理告警列表   | VIEWER+        |
| PUT      | `/alerts/batch-handle`                  | 批量处理告警     | OPERATOR+      |
| GET      | `/alerts/stats/device/{deviceId}`       | 设备告警统计     | VIEWER+        |
| GET      | `/alerts/stats/time-range`              | 时间范围统计     | VIEWER+        |
| GET      | `/alerts/stats/device-ranking`          | 设备告警排行     | VIEWER+        |

---

## 五、待优化项

| 优先级 | 优化项                                       | 说明                                           |
| :----- | :------------------------------------------- | :--------------------------------------------- |
| P1     | REQ-ALT-FR-003告警通知推送                   | 邮件通知和WebSocket实时推送待实现               |
| P1     | 告警评估异步化                               | 当前为同步调用，高并发场景下影响接口响应速度     |
| P2     | 告警通知去重                                 | 同一设备同一类型5分钟内不重复通知               |
| P2     | 告警自动恢复                                 | 数据恢复正常后自动关闭对应告警                   |
| P3     | 告警规则热更新                               | 修改规则后无需重启即可生效                       |
