# config — 框架配置包

> **版本**: v1.4 | **更新日期**: 2026-05-16

---

## 一、功能用途

`config` 包集中管理 Spring Boot 框架级别的配置类，通过 Java Config 方式（`@Configuration`）替代传统的 XML 配置，实现对框架组件行为的定制化控制。

### 核心作用

- **组件注册**：将第三方框架的核心组件注册为 Spring Bean
- **行为定制**：通过配置类调整框架的默认行为
- **统一管理**：所有框架级配置集中存放，便于维护

### 与其他模块的关系

```
config/MybatisPlusConfig    → 为 mapper 层提供分页等增强功能
config/MyMetaObjectHandler  → 为 entity 层提供 createTime/updateTime 自动填充
config/CorsConfig           → 为前后端分离开发提供跨域支持
@EnableScheduling           → 为 task 包提供定时任务调度能力
application.yaml            → 与 config 包互补，管理外部化配置（含 task 定时任务 cron 表达式）
```

---

## 二、接口说明

### 2.1 MybatisPlusConfig — MyBatis-Plus 配置类

**完整类名**: `com.example.platform.config.MybatisPlusConfig`

**当前状态**：✅ 已启用

#### 配置内容

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | MyBatis-Plus 拦截器，注册分页插件 |

#### 分页插件参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `DbType.MYSQL` | - | 数据库类型，影响分页语句生成 |

### 2.2 CorsConfig — 跨域配置类

**完整类名**: `com.example.platform.config.CorsConfig`

通过注册 `CorsFilter` Bean，为前后端分离开发提供 CORS（Cross-Origin Resource Sharing）跨域支持。

#### 配置内容

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `addAllowedOriginPattern` | `http://localhost:*`, `http://127.0.0.1:*` | 允许本地开发服务器的跨域请求 |
| `addAllowedMethod` | `*` | 允许所有 HTTP 方法（GET/POST/PUT/DELETE 等） |
| `addAllowedHeader` | `*` | 允许所有请求头（含 Authorization、Content-Type 等） |
| `setAllowCredentials` | `true` | 允许携带 Cookie / Token 等凭证 |
| `setMaxAge` | `3600L` | 预检请求（OPTIONS）缓存 1 小时 |

#### 安全说明

- 使用 `addAllowedOriginPattern` 而非 `addAllowedOrigin("*")`，配合 `allowCredentials(true)` 是 Spring 5.3+ 的安全写法
- 当前仅允许 `localhost` 和 `127.0.0.1` 的跨域访问，生产环境需按需调整
- 预检请求缓存可减少浏览器 OPTIONS 请求次数，提升性能

### 2.3 MyMetaObjectHandler — 自动填充处理器

**完整类名**: `com.example.platform.config.MyMetaObjectHandler`

实现 `MetaObjectHandler` 接口，配合 Entity 中 `@TableField(fill = FieldFill.INSERT)` 等注解，自动填充 `createTime`、`updateTime` 和 `collectTime` 字段。

#### 填充策略

| 字段 | 填充时机 | 填充值 |
|------|----------|--------|
| `collectTime` | INSERT（插入时） | `LocalDateTime.now()` |
| `createTime` | INSERT（插入时） | `LocalDateTime.now()` |
| `updateTime` | INSERT + UPDATE（插入和更新时） | `LocalDateTime.now()` |

#### 工作原理

```
执行 save() → MyBatis-Plus 检测到 fill = FieldFill.INSERT
    → 调用 MyMetaObjectHandler.insertFill()
    → 自动设置 collectTime = 当前时间, createTime = 当前时间, updateTime = 当前时间

执行 updateById() → MyBatis-Plus 检测到 fill = FieldFill.INSERT_UPDATE
    → 调用 MyMetaObjectHandler.updateFill()
    → 自动设置 updateTime = 当前时间
```

### 2.4 @EnableScheduling — 定时任务调度启用

**注解位置**: `com.example.platform.IotPlatformApplication`（启动类）

```java
@SpringBootApplication
@MapperScan("com.example.platform.mapper")
@EnableScheduling
public class IotPlatformApplication { ... }
```

#### 作用

| 配置项 | 说明 |
|--------|------|
| `@EnableScheduling` | 启用 Spring 定时任务调度能力，使 `@Scheduled` 注解生效 |
| 依赖模块 | `task/DeviceMonitorTask` 使用 `@Scheduled(cron = "${task.check-device-cron}")` |

### 2.5 application.yaml — 定时任务配置

**配置文件**: `src/main/resources/application.yaml`

```yaml
task:
  check-device-cron: 0 0/5 * * * ?  # 每五分钟检查一次设备在线状态
```

#### 配置说明

| 配置键 | 默认值 | 说明 | 引用位置 |
|--------|--------|------|----------|
| `task.check-device-cron` | `0 0/5 * * * ?` | 设备在线状态检查的 cron 表达式 | `DeviceMonitorTask.checkDeviceOnlineStatus()` |

#### cron 表达式格式

```
0 0/5 * * * ?
│ │  │ │ │ │
│ │  │ │ │ └─ 星期（? 表示不指定）
│ │  │ │ └─── 月份
│ │  │ └───── 日期
│ │  └─────── 小时（* 每小时）
│ └────────── 分钟（0/5 从第0分钟开始，每5分钟）
└──────────── 秒（0 整秒触发）
```

---

## 三、使用示例

### 示例 1：分页查询

```java
Page<Device> page = new Page<>(1, 10);
LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Device::getStatus, 1);

Page<Device> result = deviceService.page(page, wrapper);
System.out.println("总记录数：" + result.getTotal());
System.out.println("当前页数据：" + result.getRecords());
```

### 示例 2：自动填充效果验证

```java
// 插入时：createTime 和 updateTime 自动填充
Device device = new Device();
device.setDeviceName("新设备");
device.setDeviceCode("SN-002");
device.setTypeId(1);
device.setStatus(0);
device.setLocation("测试位置");
// 无需手动设置 createTime / updateTime
deviceService.save(device);
// device.getCreateTime() → 2026-05-08 12:00:00
// device.getUpdateTime() → 2026-05-08 12:00:00

// 更新时：updateTime 自动更新
device.setLocation("新位置");
deviceService.updateById(device);
// device.getUpdateTime() → 2026-05-08 12:05:00（自动更新）
```

### 示例 3：跨域配置效果验证

启动项目后，从前端 `http://localhost:3000` 发起请求：

```bash
curl -H "Origin: http://localhost:3000" \
     -H "Access-Control-Request-Method: POST" \
     -X OPTIONS \
     http://localhost:8080/api/devices -i
```

响应头中应包含：

```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: POST,GET,PUT,DELETE,OPTIONS,PATCH
Access-Control-Allow-Credentials: true
Access-Control-Max-Age: 3600
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 跨域请求被拒绝 | 前端请求不在允许的 Origin 列表中 | 在 `CorsConfig.buildCorsConfiguration()` 中添加对应的 `addAllowedOriginPattern` |
| 自动填充不生效 | Entity 字段缺少 `fill` 属性 | 确保字段注解为 `@TableField(value = "create_time", fill = FieldFill.INSERT)` |
| 自动填充值为 null | `MyMetaObjectHandler` 未注册为 Spring Bean | 确保类有 `@Component` 注解 |
| 分页查询报错 | 分页插件未注册 | `MybatisPlusConfig` 已启用，确保类有 `@Configuration` 和 `@Bean` 注解 |
| 配置类不生效 | 缺少 `@Configuration` 注解 | 确保类上有 `@Configuration` 注解 |
| Bean 冲突 | 多个配置类注册了相同类型的 Bean | 使用 `@Primary` 或 `@Qualifier` 指定优先级 |
| 定时任务不执行 | `@EnableScheduling` 未添加 | 确保启动类上有 `@EnableScheduling` 注解 |
| cron 表达式不生效 | `@Scheduled` 未读取到配置值 | 检查 `application.yaml` 中 `task.check-device-cron` 配置是否存在 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，MybatisPlusConfig 为注释状态 |
| v1.1 | 2026-05-08 | `[f88934db]` | MybatisPlusConfig 已正式启用；新增 MyMetaObjectHandler 自动填充处理器文档 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | 新增 CorsConfig 跨域配置类文档 |
| v1.3 | 2026-05-15 | `[05b8ff81]` | MyMetaObjectHandler 新增 `collectTime` 自动填充（设备数据采集时间） |
| v1.4 | 2026-05-16 | `[240739a]` | 新增 `@EnableScheduling` 定时任务调度启用说明；新增 `application.yaml` 中 `task.check-device-cron` 配置说明 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-16。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
