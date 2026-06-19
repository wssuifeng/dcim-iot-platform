# ALT告警模块学习指南

> **版本**: V1.0.0 | **创建**: 2026-05-24
>
> **适用对象**: 掌握Java基础与Spring Boot常用注解，但不熟悉告警规则引擎设计的开发者
>
> **关联需求**: REQ-ALT-FR-001（告警规则引擎评估）、REQ-ALT-FR-002（告警管理与处理）

---

## 1. 模块全景定位（宏观视角）

### 一句话概述

ALT模块就像一个**智能安防监控系统**——摄像头拍到画面（设备上报数据）后，系统自动比对预设规则（告警规则配置），发现异常就触发警报（生成告警）；如果已有警报但情况更严重了，就升级警报级别（告警升级）；安保人员可以查看所有警报、批量处理已确认的警报（告警管理）。

### 核心职责清单

| # | 职责 | 对应生活类比 |
|---|:-----|:-------------|
| 1 | **规则评估**：对设备数据进行阈值比较，判断是否触发告警 | 安防系统比对画面与规则 |
| 2 | **告警生成**：超阈值时创建告警记录 | 触发警报 |
| 3 | **告警升级**：已有告警且情况更严重时升级级别 | 警报升级（从黄色变红色） |
| 4 | **告警管理**：查询、筛选、批量处理告警 | 安保人员查看和处理警报 |
| 5 | **告警统计**：按设备/时间/级别统计告警数据 | 安防月报/周报 |

> **这5项职责的协作关系**：职责1是"判断"，职责2和3是"行动"（判断后的两种行动），职责4是"响应"（人对告警的响应），职责5是"复盘"（事后分析）。

---

## 2. 技术盲区速查字典（扫清障碍）

### 2.1 核心概念

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **规则引擎** | 将"什么条件下触发告警"从业务代码中抽离为可配置的规则 | 像可编程的报警器，你设定"温度>40度就响"，不用改电路 |
| **降级策略** | 设备级配置优先，不存在时使用类型级配置 | 像员工有个人权限就用个人的，没有就用部门的默认权限 |
| **告警升级** | 同一指标已有告警但新规则更紧急时，提升告警级别 | 像火警从"注意"升级为"紧急"，不重复拉响两次警报 |
| **幂等处理** | 同一操作执行多次结果相同，批量处理已处理告警不会重复处理 | 像按了已读按钮，再按一次不会变成"已读两次" |
| **去重** | 离线告警生成前先查已有未处理告警，避免重复 | 像同一扇门只贴一张"维修中"告示，不重复贴 |
| **LambdaUpdateWrapper** | MyBatis-Plus提供的工具类，用Java代码构建UPDATE语句 | 像填表模板，你只需在指定位置填值，不用手写SQL |
| **@RequiredArgsConstructor** | Lombok注解，自动为所有final字段生成构造器 | 像自动帮你写好了"需要A和B才能创建这个对象"的构造方法 |

### 2.2 告警级别体系

| 级别数字 | 含义 | 紧急程度 | 生活类比 |
|:---------|:-----|:---------|:---------|
| 1 | 紧急 | 🔴 最高 | 火灾警报——立即行动 |
| 2 | 严重 | 🟠 高 | 设备故障——尽快处理 |
| 3 | 一般 | 🟡 中 | 温度偏高——关注即可 |
| 4 | 警告 | 🔵 低 | 轻微异常——记录备查 |
| 5 | 提示 | ⚪ 最低 | 信息提醒——了解一下 |

> **注意**：数字越小越紧急！这是告警升级判断的核心逻辑：`rule.level < existingAlert.alertLevel` 表示新规则更紧急。

### 2.3 运算符说明

| 运算符 | 含义     | 示例（threshold=40） | 判断逻辑         |
|:-------|:---------|:---------------------|:-----------------|
| GT     | 大于     | value > 40           | 温度超过40度告警 |
| LT     | 小于     | value < 0            | 温度低于0度告警  |
| GTE    | 大于等于 | value >= 40          | 温度达到40度告警 |
| LTE    | 小于等于 | value <= 0           | 温度降到0度告警  |
| EQ     | 等于     | value == 1           | 状态等于1时告警  |

---

## 3. 架构与数据流转（见森林）

### 3.1 模块架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    【第1层】HTTP 请求入口                       │
│                    (运维人员/前端发送 HTTP 请求)                 │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                【第2层】Controller 层                                      │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │ AlertController                                                    │   │
│  │ GET    /alerts                → list()          多条件分页查询     │   │
│  │ GET    /alerts/{id}           → getById()       告警详情           │   │
│  │ GET    /alerts/unhandled      → listUnhandled() 未处理告警         │   │
│  │ PUT    /alerts/batch-handle   → batchHandle()   批量处理           │   │
│  │ GET    /alerts/stats/device/{id} → getStatsByDeviceId()           │   │
│  │ GET    /alerts/stats/time-range  → getStatsByTimeRange()          │   │
│  │ GET    /alerts/stats/device-ranking → getDeviceAlertRanking()     │   │
│  └──────────────────────────┬─────────────────────────────────────────┘   │
└─────────────────────────────┼─────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────────────────┐
│                   【第3层】Service 层                                      │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ AlertServiceImpl                                                  │     │
│  │ findUnhandled()              → 查询未处理告警                     │     │
│  │ batchHandle()                → 批量处理（幂等）                   │     │
│  │ getStatsByDeviceId()         → 设备维度统计                       │     │
│  │ createOfflineAlertsForDevices() → 离线告警（去重）               │     │
│  │ pageByCondition()            → 多条件分页                         │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ AlertRuleEngine（核心引擎）                                        │     │
│  │ evaluate()                   → 入口方法                           │     │
│  │   ├─ getEffectiveRuleConfig() → 获取生效配置（降级策略）          │     │
│  │   ├─ isThresholdExceeded()   → 阈值比较（策略模式）              │     │
│  │   ├─ createOrUpgradeAlert()  → 生成/升级告警                     │     │
│  │   └─ buildAlertMessage()     → 构建告警消息（模板替换）          │     │
│  └──────────┬──────────┬──────────┬─────────────────────────────────┘     │
│             │          │          │                                        │
│             ▼          ▼          ▼                                        │
│  ┌──────────────┐ ┌──────────┐ ┌───────────────┐                         │
│  │DeviceService │ │DeviceType│ │DictMetric     │                         │
│  │获取设备信息   │ │Service   │ │Service        │                         │
│  │              │ │获取类型  │ │获取指标字典    │                         │
│  └──────────────┘ │配置      │ └───────────────┘                         │
│                   └──────────┘                                           │
└───────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────┐
│                   【第4层】Mapper / 数据库层                                │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │ AlertMapper                                                        │     │
│  │ getStatsByDeviceId()           → XML映射SQL                       │     │
│  │ getStatsByTimeRange()          → XML映射SQL                       │     │
│  │ listDeviceAlertCountByTimeRange() → XML映射SQL                    │     │
│  └──────────────────────────┬───────────────────────────────────────┘     │
│                             │                                              │
│                             ▼                                              │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │                     alerts 表                                     │     │
│  │ id | device_id | alert_type | alert_level | alert_message |      │     │
│  │ is_handled | handle_time | handle_user | create_time             │     │
│  └──────────────────────────────────────────────────────────────────┘     │
└───────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心数据流转图：一次完整的告警评估过程

```
【阶段A：DAT模块触发】

① DeviceDataServiceImpl.saveWithAlertCheck()
   → alertRuleEngine.evaluate(deviceId, deviceData)
          │
          ▼
【阶段B：获取生效规则】

② AlertRuleEngine.getEffectiveRuleConfig(deviceId)
   ┌─────────────────────────────────────────────────────────────┐
   │ Step 1: 获取设备对象                                         │
   │         device = deviceService.getDeviceOrThrow(deviceId)   │
   │                                                              │
   │ Step 2: 检查设备级配置                                       │
   │         deviceConfig = device.getAlertRuleConfig()           │
   │         if 设备级配置存在且规则非空 → return 设备级配置       │
   │                                                              │
   │ Step 3: 降级到类型级配置                                     │
   │         type = deviceTypeService.getTypeOrThrow(typeId)      │
   │         typeConfig = type.getAlertRuleConfig()               │
   │         if 类型级配置存在且规则非空 → return 类型级配置       │
   │                                                              │
   │ Step 4: 均无配置 → return null（不评估）                     │
   └─────────────────────────────────────────────────────────────┘
          │ 有配置
          ▼
【阶段C：匹配规则与阈值比较】

③ 遍历规则列表，匹配metricKey
   for rule in config.rules:
       if rule.metricKey == deviceData.metric → matchedRule = rule

④ isThresholdExceeded(dataValue, matchedRule)
   switch(rule.operator):
     "GT"  → value > threshold
     "LT"  → value < threshold
     "GTE" → value >= threshold
     "LTE" → value <= threshold
     "EQ"  → value == threshold
          │ 超阈值
          ▼
【阶段D：生成或升级告警】

⑤ createOrUpgradeAlert(deviceId, rule, deviceData)
   ┌─────────────────────────────────────────────────────────────┐
   │ existingAlerts = 查找该设备同类型未处理告警                    │
   │                                                              │
   │ if 无已有告警:                                                │
   │   → 创建新Alert，设置deviceId/alertType/alertLevel/           │
   │     alertMessage/isHandled=0                                 │
   │   → alertService.save(alert)                                 │
   │                                                              │
   │ if 有已有告警且新级别更紧急:                                   │
   │   → oldLevel = existing.alertLevel  // 先保存旧级别           │
   │   → existing.alertLevel = rule.level                         │
   │   → existing.alertMessage = buildAlertMessage(rule, data)    │
   │   → alertService.updateById(existing)                        │
   │   → log("告警升级: 旧={}, 新={}", oldLevel, rule.level)      │
   └─────────────────────────────────────────────────────────────┘
```

### 3.3 批量处理告警流转图

```
前端请求: PUT /alerts/batch-handle
Body: { "ids": [1,2,3], "handleUser": "张三" }
          │
          ▼
AlertController.batchHandle(dto)
   │
   ▼
AlertServiceImpl.batchHandle(ids, handleUser)
   ┌─────────────────────────────────────────────────────────────┐
   │ ① ids为空 → return 0                                        │
   │ ② ids.size > 50 → 抛出IllegalArgumentException               │
   │ ③ 构建UPDATE语句:                                            │
   │    UPDATE alerts                                             │
   │    SET is_handled = 1,                                       │
   │        handle_time = NOW(),                                  │
   │        handle_user = '张三'                                  │
   │    WHERE id IN (1,2,3)                                       │
   │      AND is_handled = 0    ← 关键：只处理未处理的             │
   │ ④ return 受影响行数                                          │
   └─────────────────────────────────────────────────────────────┘
```

---

## 4. 关键代码逐行解读（见树木）

### 4.1 getEffectiveRuleConfig —— 降级策略核心

```java
private AlertRuleConfig getEffectiveRuleConfig(Integer deviceId) {
    // 优先使用设备级配置
    Device device = deviceService.getDeviceOrThrow(deviceId);       // ① 获取设备
    AlertRuleConfig deviceConfig = device.getAlertRuleConfig();     // ② 取设备级配置
    if (deviceConfig != null && deviceConfig.getRules() != null
            && !deviceConfig.getRules().isEmpty()) {
        return deviceConfig;                                        // ③ 有则返回
    }

    // 降级使用类型级配置
    DeviceType type = deviceTypeService.getTypeOrThrow(device.getTypeId());  // ④ 取类型
    AlertRuleConfig typeConfig = type.getAlertRuleConfig();         // ⑤ 取类型级配置
    if (typeConfig != null && typeConfig.getRules() != null
            && !typeConfig.getRules().isEmpty()) {
        return typeConfig;                                          // ⑥ 有则返回
    }

    return null;                                                    // ⑦ 都没有
}
```

**设计思路**：这是一个典型的降级策略——先查高优先级配置（设备级），没有再查低优先级配置（类型级），都没有则不评估。三层null检查（config≠null, rules≠null, rules非空）确保不会因为配置不完整而报错。

### 4.2 createOrUpgradeAlert —— 告警生成与升级

```java
private void createOrUpgradeAlert(Integer deviceId, AlertRuleDefinition rule,
                                   DeviceData deviceData) {
    List<Alert> existingAlerts = alertService.findByDeviceId(deviceId).stream()
            .filter(a -> a.getAlertType().equals(rule.getMetricKey())  // ① 同类型
                    && a.getIsHandled() == 0)                          // ② 未处理
            .toList();

    if (existingAlerts.isEmpty()) {
        // 无已有告警，创建新告警
        Alert alert = new Alert();
        alert.setDeviceId(deviceId);
        alert.setAlertType(rule.getMetricKey());        // ③ 类型=指标标识
        alert.setAlertLevel(rule.getLevel());            // ④ 级别=规则级别
        alert.setAlertMessage(buildAlertMessage(rule, deviceData));
        alert.setIsHandled(0);
        alertService.save(alert);
    } else {
        Alert existingAlert = existingAlerts.get(0);
        if (rule.getLevel() < existingAlert.getAlertLevel()) {  // ⑤ 新级别更紧急
            int oldLevel = existingAlert.getAlertLevel();        // ⑥ 先保存旧级别
            existingAlert.setAlertLevel(rule.getLevel());
            existingAlert.setAlertMessage(buildAlertMessage(rule, deviceData));
            alertService.updateById(existingAlert);
            log.info("⬆️ 告警升级: 旧级别={}, 新级别={}", oldLevel, rule.getLevel());
        }
    }
}
```

**关键要点**：
- ①② 过滤条件：同一指标（metricKey）+ 未处理（isHandled=0），确保只对同类型未处理告警做升级判断
- ⑤ `<` 而不是 `<=`：只有更紧急才升级，同级别不重复操作
- ⑥ **先保存旧级别再set新级别**：这是代码审查修复的bug，如果先set再log，日志中旧级别和新级别会显示相同值

### 4.3 buildAlertMessage —— 消息模板替换

```java
private String buildAlertMessage(AlertRuleDefinition rule, DeviceData deviceData) {
    String metricName = rule.getMetricKey();     // ① 默认使用原始指标标识
    String metricUnit = "";                      // ② 默认无单位
    try {
        DictMetric metricInfo = dictMetricService.getFromCache(rule.getMetricKey());
        if (metricInfo != null) {
            if (StringUtils.hasText(metricInfo.getMetricName())) {
                metricName = metricInfo.getMetricName();    // ③ 替换为中文名
            }
            if (StringUtils.hasText(metricInfo.getMetricUnit())) {
                metricUnit = metricInfo.getMetricUnit();    // ④ 替换为单位
            }
        }
    } catch (Exception e) {
        log.warn("获取指标字典失败，使用原始metricKey: {}", rule.getMetricKey());
        // ⑤ 获取失败不影响消息生成，使用默认值
    }

    String template = rule.getMessageTemplate();
    if (template != null && !template.isEmpty()) {
        return template
                .replace("{value}", deviceData.getDataValue().toString())
                .replace("{threshold}", rule.getThreshold().toString())
                .replace("{metricName}", metricName)
                .replace("{unit}", metricUnit);            // ⑥ 模板替换
    }
    return metricName + "当前值" + deviceData.getDataValue() + metricUnit
            + "，超过阈值" + rule.getThreshold() + metricUnit;  // ⑦ 默认格式
}
```

**设计思路**：三层兜底——①字典获取失败用原始key；②字典中名称为空用原始key；③模板为空用默认格式。确保任何情况下都能生成有意义的告警消息。

---

## 5. 常见问题与踩坑记录

### Q1: 为什么告警升级条件是`rule.level < existingAlert.alertLevel`，而不是`>`？

**答**：因为告警级别数字越小越紧急（1=紧急，5=提示）。当新规则的级别数字更小时，表示情况更紧急，需要升级。比如已有告警级别3（一般），新规则级别1（紧急），1 < 3，所以需要升级。

### Q2: 为什么同一设备同一指标只保留一条未处理告警？

**答**：避免告警风暴。如果设备温度持续超标，每上报一条数据就生成一条告警，会导致告警列表被同一问题淹没。只保留一条未处理告警，升级其级别，既保留了告警信息又避免了重复。

### Q3: 批量处理告警时，如果ids中包含已处理的ID会怎样？

**答**：不会有影响。`LambdaUpdateWrapper`的WHERE条件包含`eq(Alert::getIsHandled, 0)`，已处理的告警不会被更新。返回值是实际受影响的行数，可能小于传入的ids数量。

### Q4: DictMetricService获取失败时为什么只记warn不抛异常？

**答**：告警消息生成是辅助功能，核心是告警本身要生成成功。如果因为指标字典服务不可用导致告警消息生成失败，进而导致整个告警流程中断，得不偿失。使用原始metricKey作为兜底，保证告警一定能生成。

---

## 6. 沙盒演练

### 演练1：添加一个新的运算符"NEQ"（不等于）

**目标**：在现有5种运算符基础上，新增NEQ（不等于）支持。

**步骤**：
1. 在`AlertRuleDefinition.java`中修改`@Pattern`正则：`^(GT|LT|GTE|LTE|EQ|NEQ)$`
2. 在`AlertRuleEngine.isThresholdExceeded()`的switch中添加：
```java
case "NEQ" -> Double.compare(val, threshold) != 0;
```
3. 验证：创建一条NEQ规则的告警配置，上报数据验证触发

### 演练2：添加告警自动恢复功能

**目标**：当数据值恢复正常范围时，自动关闭对应的未处理告警。

**步骤**：
1. 在`AlertRuleEngine.evaluate()`中，阈值未超过时增加逻辑：
```java
if (!isThresholdExceeded(deviceData.getDataValue(), matchedRule)) {
    // 查找该设备同类型未处理告警，标记为已处理
    List<Alert> existing = alertService.findByDeviceId(deviceId).stream()
        .filter(a -> a.getAlertType().equals(matchedRule.getMetricKey()) && a.getIsHandled() == 0)
        .toList();
    if (!existing.isEmpty()) {
        Alert alert = existing.get(0);
        alert.setIsHandled(1);
        alert.setHandleTime(LocalDateTime.now());
        alert.setHandleUser("系统自动恢复");
        alertService.updateById(alert);
    }
    return;
}
```
2. 验证：先触发告警，再上报正常数据，检查告警是否自动关闭

### 演练3：为批量处理添加处理备注字段

**目标**：批量处理告警时允许填写处理备注。

**步骤**：
1. 在`BatchHandleAlertDTO`中添加`handleRemark`字段
2. 在`AlertServiceImpl.batchHandle()`的UPDATE语句中添加`set(Alert::getHandleRemark, handleRemark)`
3. 在`Alert`实体中添加`handleRemark`字段（需同步修改数据库表）
4. 验证：发送批量处理请求带备注，检查数据库中备注是否正确保存
