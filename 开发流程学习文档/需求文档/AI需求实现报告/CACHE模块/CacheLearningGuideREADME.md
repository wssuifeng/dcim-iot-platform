# CACHE 缓存模块学习指南

> **版本**: V1.0.0 | **创建**: 2026-05-28
>
> **适用对象**: 掌握 Java 基础与 Spring Boot 常用注解，需要理解 Redis 缓存基础设施设计的开发者
>
> **关联需求**: REQ-CACHE-FR-001（指标字典缓存）至 REQ-CACHE-FR-007（穿透防御）

---

## 1. 模块概述（宏观视角）

### 一句话概述

CACHE 模块是物联网平台从**单实例内存缓存**升级为**分布式 Redis 缓存**的基础设施层——它像一座统一管理的**智能仓库**，所有业务模块不再各自存放物品（数据），而是通过统一的仓管员（`CacheService`）进行存取。仓库支持设置有效期（TTL）、防止无效查询击穿仓库（空值标记）、在仓库故障时自动降级（异常兜底），确保平台在多实例部署下数据一致、内存可控、故障不扩散。

### 解决的痛点

| 痛点 | 旧方案（ConcurrentHashMap） | 新方案（Redis + CacheService） |
|:-----|:---------------------------|:-------------------------------|
| **多实例不共享** | 3 个服务节点各自维护一份内存缓存，数据不一致 | Redis 集中存储，所有节点共享同一份数据 |
| **无 TTL 管理** | 数据永久驻留 JVM 堆，内存只增不减 | Redis 原生支持过期，自动清理无用数据 |
| **缓存穿透** | 查不到数据每次穿透到 DB，恶意查询打垮数据库 | `NullValue` 占位标记，5 分钟窗口内不再穿透 |
| **直接操作 RedisTemplate** | 业务代码耦合 Redis API，无法切换缓存实现 | `CacheService` 统一入口，未来可切换 Caffeine 等本地缓存 |
| **KEYS 命令风险** | 全量刷新时若误用 KEYS 可阻塞 Redis 数秒 | 强制使用 SCAN 游标迭代，不阻塞主线程 |
| **故障无降级** | Redis 宕机 = 系统不可用 | 读写静默降级，计数器抛 `CacheOperationException` → HTTP 503 |

### 核心职责清单

| # | 职责 | 对应生活类比 |
|---|:-----|:-------------|
| 1 | **统一缓存入口**：所有 Redis 操作通过 `CacheService`，禁止业务层直接使用 `RedisTemplate` | 仓库管理统一走仓管员，不让人随便进仓库乱翻 |
| 2 | **序列化策略**：Key 用 `StringRedisSerializer`（可读），Value 用 `Jackson2JsonRedisSerializer`（JSON） | 标签用中文写清楚，货物用标准包装盒 |
| 3 | **缓存穿透防御**：DB 中不存在的数据存 `NullValue` 占位（TTL=5min） | 「此货缺货」告示牌，5 分钟内不再问仓管 |
| 4 | **降级容错**：有 DB 兜底的操作静默降级，无 DB 兜底的操作抛异常（503） | 仓库停电→常用货去原始档案查，计数器停电→暂停服务通知 |
| 5 | **原子去重**：`SETNX` 实现告警通知去重，处理完成后主动删除 Key | 一个警报只按一次喇叭，处理完把标记擦掉 |
| 6 | **Pipeline 批量写入**：`multiSet` 用 Pipeline 打包发送，减少网络往返 | 一次送一车货到仓库，不一趟一趟跑 |
| 7 | **SCAN 替代 KEYS**：`deleteByPattern` 用 SCAN 游标迭代，不阻塞 Redis | 盘点库存一页一页翻，不一键锁死整个仓库 |

---

## 2. 基础设施规范

### 2.1 依赖要求

```xml
<!-- pom.xml 关键依赖 -->
<!-- Spring Data Redis：Lettuce 客户端 + RedisTemplate -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- commons-pool2：Lettuce 连接池依赖，不引入则 lettuce.pool 配置全部失效 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

| 依赖 | 作用 | 缺失后果 |
|:-----|:-----|:---------|
| `spring-boot-starter-data-redis` | 提供 `RedisTemplate`、Lettuce 客户端、自动配置 | 无法连接 Redis |
| **`commons-pool2`** | Lettuce 连接池底层依赖 | **`spring.redis.lettuce.pool.*` 全部配置项静默失效，每次请求新建连接，高并发下 TCP 握手风暴** |

> ⚠️ **重要**：`spring-boot-starter-data-redis` 默认**不传递** `commons-pool2`，必须显式引入。否则 Spring Boot 不会报错，但连接池配置全部被忽略。

### 2.2 序列化策略

```
┌───────────────────────────────────────────────────┐
│                  Redis Key/Value 结构               │
├─────────────┬─────────────────────────────────────┤
│ Key         │ StringRedisSerializer（明文可读）     │
│             │ 示例: "dict:metric:temperature"      │
├─────────────┼─────────────────────────────────────┤
│ Value       │ Jackson2JsonRedisSerializer（JSON）   │
│             │ 含 "@class":"com.xxx.Device" 头信息   │
├─────────────┼─────────────────────────────────────┤
│ Hash Key    │ StringRedisSerializer（同 Key）       │
├─────────────┼─────────────────────────────────────┤
│ Hash Value  │ Jackson2JsonRedisSerializer（同上）   │
└─────────────┘
```

| 选型 | 方案 | 原因 |
|:-----|:-----|:-----|
| **Key 序列化** | `StringRedisSerializer` | ① redis-cli 可直接阅读 `get device:info:1`；② 跨语言客户端可解析；③ 不会出现 JDK 序列化的 `\xac\xed` 乱码 |
| **Value 序列化** | `Jackson2JsonRedisSerializer` + `activateDefaultTyping` | ① JSON 格式可读可调试；② `@class` 自动记录 Java 类型，反序列化无需手动指定；③ 替代已 `@Deprecated` 的 `GenericJackson2JsonRedisSerializer` |

#### @class 隐患说明

```json
// Redis 中实际存储的 JSON（注意 @class 字段）
{
  "@class": "com.example.platform.entity.Device",
  "id": 1,
  "deviceName": "温度传感器A",
  "status": 1
}
```

| 隐患 | 影响 | 规避方案 |
|:-----|:-----|:---------|
| **类重构** | 改包名/类名后，老缓存数据 `@class` 指向旧全限定名，反序列化失败 | 重构后执行 `deleteByPattern` 全量清除旧缓存 |
| **体积膨胀** | 每条数据多存一个全限定类名（30~50 字节），大量数据时浪费内存 | 数据量极大时关闭 `activateDefaultTyping`，改用手动 JSON |
| **跨语言** | Go/Python 不认识 `@class` 字段，解析报错 | 需跨语言共享时，改用纯 JSON 序列化（无类型头） |

### 2.3 统一接口 CacheService 设计理念

#### 为什么要禁止业务层直接用 RedisTemplate？

```
❌ 旧方式：业务代码直接注入 RedisTemplate
    DeviceServiceImpl {
        redisTemplate.opsForValue().set(key, value);  // 直接依赖 Redis API
    }

✅ 新方式：业务代码只依赖 CacheService 接口
    DeviceServiceImpl {
        cacheService.set(key, value, 1, TimeUnit.HOURS);  // 语义化调用
    }
```

| 优势 | 说明 |
|:-----|:-----|
| **实现可替换** | 接口抽象后，未来可切换为 Caffeine 本地缓存、Redisson 等，业务代码**零改动** |
| **横切关注点集中** | 序列化、异常降级、Key 格式规范、日志记录全部在 `CacheServiceImpl` 中统一处理 |
| **方法签名语义化** | `set(key, value, 1, HOURS)` 比 `redisTemplate.opsForValue().set(key, value, Duration.ofHours(1))` 更清晰 |
| **编译期安全** | `get(key, Device.class)` 显式声明期望类型，类型不匹配时抛 `ClassCastException` → GlobalExceptionHandler 统一返回 400 |

#### SCAN 替代 KEYS —— 为什么严禁 KEYS？

| 命令 | 原理 | 风险 | 本模块使用位置 |
|:-----|:-----|:-----|:---------------|
| **KEYS pattern** | O(N) 全库扫描 | 100 万个 Key 时阻塞数秒，等同于制造一次 Redis 宕机 | ❌ **本模块零使用** |
| **SCAN cursor** | 基于游标迭代，每次返回一小批 + 下一个游标位置 | 不阻塞 Redis，不保证不重复（删除场景可容忍） | `deleteByPattern("dict:metric:*")` |

#### Pipeline 与原子性说明

| 特性 | Pipeline（multiSet） | Lua 脚本（executeLua） |
|:-----|:---------------------|:-----------------------|
| **原子性** | ❌ 非原子，中间某条 SET 失败不影响其他 | ✅ 强原子，全部成功或全部失败 |
| **性能** | 10 条数据约节省 9 次网络往返 | 脚本传输 + 执行有额外开销 |
| **适用场景** | 批量缓存写入，容忍个别失败 | 分布式锁、限流计数器等需要原子性 |

---

## 3. 核心设计模式与防御策略

### 3.1 Cache-Aside 读写模式

```
【读流程】                                【写流程】
  ①查缓存 → 命中返回                      ①更新DB
  ②未命中 → 查DB                          ②删除缓存（不更新，懒加载回填）
  ③回填缓存
```

| 关键决策 | 方案 | 原因 |
|:---------|:-----|:-----|
| **为什么先写 DB 再删缓存？** | ①更新DB → ②删除缓存 | 反过来存在并发不一致窗口：「删缓存→读 miss 查 DB 旧值→写缓存旧值→更新 DB 新值」→ **缓存永远是旧值** |
| **为什么是删除缓存而非更新缓存？** | `delete(key)` 而非 `set(key, newValue)` | ① 并发写可能顺序错乱；② 删除后下次读自然回填最新值（懒加载）；③ 避免无效写入（写后不再读的数据） |

### 3.2 缓存穿透防御（空值标记）

> **什么是缓存穿透？** 查询一个 DB 中根本不存在的数据，每次都穿透缓存打到数据库。

```
请求 metricKey="unknown_key"
  → 查缓存 miss → 查 DB miss
  → 写缓存 NullValue（TTL=5min）
  → 5 分钟内后续请求直接返回 null，不走 DB ✅
```

**技术细节**：

| 问题 | 方案 |
|:-----|:-----|
| **为什么不用 "NULL" 字符串？** | `Jackson2JsonRedisSerializer` 反序列化得 `String`，`instanceof DictMetric` 判断失效 |
| **为什么 NullValue 不能是 final class？** | `DefaultTyping.NON_FINAL` 不为 final 类型嵌入 `@class`，反序列化后丢失类型 |
| **为什么需要 marker 字段？** | Jackson 序列化要求对象至少有一个可序列化属性，空类抛 `InvalidDefinitionException` |

### 3.3 缓存雪崩防御（随机 TTL）

| 防御策略 | 本模块实施 |
|:---------|:----------|
| **固定 TTL + 分散过期** | ✅ 各业务独立设定 TTL（1h / 24h / 48h），不会同时过期 |
| **随机 TTL 抖动** | ⚠️ 预留扩展点——后续可在 `CacheServiceImpl.set()` 中统一添加 `±10%` 随机抖动 |

### 3.4 降级容错策略

| 操作类型 | Redis 不可用时 |
|:---------|:--------------|
| **有 DB 兜底**（get/set/delete/hasKey） | **静默降级**——返回 null/false，业务层查 DB，功能不中断 |
| **无 DB 兜底**（increment/decrement） | **抛 `CacheOperationException`** → GlobalExceptionHandler → **HTTP 503** |
| **类型不匹配**（ClassCastException） | **向上传播** → GlobalExceptionHandler → **HTTP 400** |

---

## 4. 业务场景实现明细（7 个 FR）

### 总览表

| FR# | 场景 | Key 格式 | TTL | 核心命令/特性 | 简述 |
|:----|:-----|:---------|:----|:-------------|:-----|
| **001** | 指标字典缓存 | `dict:metric:{metricKey}` | 24h / 5min | `SET`, `GET`, `DELETE` | Cache-Aside，CRUD 后自动删除对应 Key |
| **002** | 告警规则配置缓存 | `config:alert:device:{id}`<br>`config:alert:type:{id}` | 1h / 5min | `SET`, `GET`, `DELETE` | 两级降级查询，设备级优先 |
| **003** | 设备级配置失效 | `config:alert:device:{id}` | — | `DELETE` | `DeviceServiceImpl.updateAlertConfig()` 更新 DB 后删缓存 |
| **004** | 类型级配置失效 | `config:alert:type:{id}` | — | `DELETE` | `DeviceTypeServiceImpl.updateAlertConfig()` 更新 DB 后删缓存 |
| **005** | 告警通知去重 | `notify:alert:{alertId}` | 48h | **`SETNX`** + `DELETE` | 原子去重，处理完成后主动释放 Key |
| **006** | 指标字典全量刷新 | `dict:metric:*` | 24h | **`SCAN`** + **Pipeline** | `deleteByPattern` 清旧 → `multiSet` 批量回填 |
| **007** | 缓存穿透防御 | 所有场景共用 | 5min | `instanceof NullValue` | DB 无数据存占位，5 分钟内不再穿透 |

### FR-005 告警通知去重 —— 关键设计详解

```
T0: Alert(id=100) → SETNX notify:alert:100 → true → 发通知 ✅
T1: batchHandle([100]) → releaseDedupMark(100) → DELETE → Key 删除 ✅
T2: 同类再异常 → Alert(id=101) → SETNX notify:alert:101 → true → 发通知 ✅

降级: releaseDedupMark 失败 → Key notify:alert:100 残留
      → 新告警 Alert(id=101) → Key notify:alert:101 天然隔离 → 仍正确 ✅
```

### 各场景代码位置

| FR# | 核心实现类 |
|:----|:----------|
| FR-001/006/007 | [DictMetricServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/DictMetricServiceImpl.java) |
| FR-002/003/004/007 | [AlertConfigServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AlertConfigServiceImpl.java) |
| FR-005 | [NotificationDedupServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/NotificationDedupServiceImpl.java) |
| FR-003 调用方 | [DeviceServiceImpl.java#L148-L153](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/DeviceServiceImpl.java) |
| FR-004 调用方 | [DeviceTypeServiceImpl.java#L43-L48](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/DeviceTypeServiceImpl.java) |
| FR-005 调用方 | [AlertServiceImpl.java#L84-L93](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AlertServiceImpl.java) |
| FR-007 基础设施 | [CacheService.java#L27-L51](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/CacheService.java) |

---

## 5. 运维配置说明

### 5.1 环境变量注入

```yaml
# application.yaml —— 支持环境变量覆盖
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}          # 生产必须设置
      database: ${REDIS_DATABASE:0}
      connect-timeout: 2000                 # TCP 连接超时（ms）
      lettuce:
        shutdown-timeout: 3000
        pool:
          max-active: 20                    # 本地开发够用，生产调至 50~100
          max-idle: 10
          min-idle: 5
          max-wait: 3000                    # 获取连接最大等待（ms）
```

### 5.2 本地开发

```bash
# 默认值: localhost:6379，无需环境变量
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine

# 带密码：
docker run -d --name redis-dev -p 6379:6379 redis:7-alpine \
  redis-server --requirepass mypassword
# 对应: REDIS_PASSWORD=mypassword
```

### 5.3 生产 Checklist

| ✅ | 检查项 | 要求 |
|:--|:------|:-----|
| ✅ | `REDIS_HOST` / `REDIS_PASSWORD` | 已配置生产 Redis 地址 + 强密码 |
| ✅ | `commons-pool2` | 确认 `pom.xml` 中存在 |
| ✅ | 连接池 `max-active` | 按 QPS `×` 平均响应时间调整 |
| ✅ | Redis 内存策略 | 建议 `volatile-lru` |
| ✅ | 监控 | Redis 连接数、命中率、内存告警 |

---

## 附录: 核心类索引

| 类名 | 路径 | 职责 |
|:-----|:-----|:-----|
| `CacheService` | [CacheService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/CacheService.java) | 统一接口 + `NullValue` 防穿透 |
| `CacheServiceImpl` | [CacheServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/CacheServiceImpl.java) | 实现：降级 + SCAN + Pipeline + Lua |
| `RedisConfig` | [RedisConfig.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/config/RedisConfig.java) | Key/Value 序列化策略 |
| `CacheOperationException` | [CacheOperationException.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/exception/CacheOperationException.java) | 计数器无 DB 兜底异常 |
| `GlobalExceptionHandler` | [GlobalExceptionHandler.java#L161-L180](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/exception/GlobalExceptionHandler.java) | `CacheOperationException`→503, `ClassCastException`→400 |