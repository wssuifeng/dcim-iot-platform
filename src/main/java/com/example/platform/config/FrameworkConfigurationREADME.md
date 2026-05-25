# config — 框架配置包

> **版本**: v3.0 | **更新日期**: 2026-05-24

---

## 一、功能用途

`config` 包集中管理 Spring Boot 框架级别的配置类，通过 Java Config 方式（`@Configuration`）替代传统的 XML 配置，实现对框架组件行为的定制化控制。

### 核心作用

- **组件注册**：将第三方框架的核心组件注册为 Spring Bean
- **行为定制**：通过配置类调整框架的默认行为
- **统一管理**：所有框架级配置集中存放，便于维护

### 与其他模块的关系

```
config/MybatisPlusConfig    → 为 mapper 层提供乐观锁、分页等增强功能
config/MyMetaObjectHandler  → 为 entity 层提供 createTime/updateTime 自动填充
config/CorsConfig           → 为 Spring Security 过滤链提供 CORS 配置源
config/SecurityConfig       → 为应用提供 JWT 认证、RBAC 鉴权、密码加密
security/JwtAuthenticationFilter  → JWT Token 提取与校验过滤器
security/CustomAuthenticationEntryPoint → 未认证请求返回 401 JSON
security/CustomAccessDeniedHandler    → 权限不足请求返回 403 JSON
@EnableScheduling           → 为 task 包提供定时任务调度能力
application.yaml            → 与 config 包互补，管理外部化配置（含 task 定时任务 cron 表达式、CORS 允许源、JWT 密钥等）
```

---

## 二、接口说明

### 2.1 MybatisPlusConfig — MyBatis-Plus 配置类

**完整类名**: `com.example.platform.config.MybatisPlusConfig`

**当前状态**：✅ 已启用

#### 配置内容

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | MyBatis-Plus 拦截器，依次注册乐观锁插件和分页插件 |

#### 插件参数

**乐观锁插件（OptimisticLockerInnerInterceptor）**

| 参数 | 说明 |
|------|------|
| 用途 | 防止并发更新时丢失更新，适用于 IoT 设备状态更新等场景 |
| 使用方式 | Entity 需添加 `@Version` 注解字段，MyBatis-Plus 自动在 UPDATE 语句中追加版本条件 |

**分页插件（PaginationInnerInterceptor）**

| 参数 | 值 | 说明 |
|------|-----|------|
| `DbType.MYSQL` | - | 数据库类型，影响分页语句生成 |

#### 插件注册顺序

```java
MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
// 1. 乐观锁插件（需在分页插件之前注册）
interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
// 2. 分页插件
interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
```

> **注意**：乐观锁插件必须注册在分页插件之前，否则乐观锁功能可能不生效。

### 2.2 CorsConfig — 跨域配置类

**完整类名**: `com.example.platform.config.CorsConfig`

通过注册 `CorsConfigurationSource` Bean，为 Spring Security 过滤链提供 CORS（Cross-Origin Resource Sharing）跨域配置。SecurityConfig 中的 `http.cors()` 会自动引用此 Bean。

#### 配置内容

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `allowedOrigins` | 从配置文件动态读取 `@Value("${cors.allowed-origins}")` | 支持生产环境灵活部署 |
| `addAllowedMethod` | `*` | 允许所有 HTTP 方法（GET/POST/PUT/DELETE 等） |
| `addAllowedHeader` | `*` | 允许所有请求头（含 Authorization、Content-Type 等） |
| `setAllowCredentials` | `true` | 允许携带 Cookie / Token 等凭证 |
| `setMaxAge` | `3600L` | 预检请求（OPTIONS）缓存 1 小时 |
| `registerCorsConfiguration` | `/**` | 对所有路径生效 |

#### 安全说明

- 使用 `addAllowedOriginPattern` 而非 `addAllowedOrigin("*")`，配合 `allowCredentials(true)` 是 Spring 5.3+ 的安全写法
- `allowedOrigins` 从配置文件 `cors.allowed-origins` 动态读取，默认值为 `http://localhost:*,http://127.0.0.1:*`
- 生产环境只需修改 `application.yaml` 中的 `cors.allowed-origins` 配置，无需改动代码
- 返回 `CorsConfigurationSource` 类型 Bean，供 `SecurityConfig.securityFilterChain()` 中 `http.cors(cors -> cors.configurationSource(corsConfigurationSource))` 引用

### 2.3 MyMetaObjectHandler — 自动填充处理器

**完整类名**: `com.example.platform.config.MyMetaObjectHandler`

实现 `MetaObjectHandler` 接口，配合 Entity 中 `@TableField(fill = FieldFill.INSERT)` 等注解，自动填充 `createTime`、`updateTime` 和 `collectTime` 字段。

#### 填充策略

| 字段 | 填充时机 | 填充方式 | 填充值 |
|------|----------|----------|--------|
| `collectTime` | INSERT（插入时） | `strictInsertFill`（仅 null 时填充） | `LocalDateTime.now()` |
| `createTime` | INSERT（插入时） | `strictInsertFill`（仅 null 时填充） | `LocalDateTime.now()` |
| `updateTime` | INSERT + UPDATE（插入和更新时） | `setFieldValByName`（强制覆盖） | `LocalDateTime.now()` |

#### 填充方式说明

- **`strictInsertFill`**：仅在字段值为 `null` 时填充，尊重手动传入的值。`createTime` 和 `collectTime` 使用此方式，避免覆盖业务指定的时间。
- **`setFieldValByName`**：强制覆盖字段值。`updateTime` 使用此方式，确保每次操作都记录最新时间。

#### 工作原理

```
执行 save() → MyBatis-Plus 检测到 fill = FieldFill.INSERT
    → 调用 MyMetaObjectHandler.insertFill()
    → strictInsertFill: collectTime = 当前时间（仅null时）, createTime = 当前时间（仅null时）
    → setFieldValByName: updateTime = 当前时间（强制覆盖）

执行 updateById() → MyBatis-Plus 检测到 fill = FieldFill.INSERT_UPDATE
    → 调用 MyMetaObjectHandler.updateFill()
    → setFieldValByName: updateTime = 当前时间（强制覆盖）
```

### 2.4 SecurityConfig — Spring Security 核心配置类

**完整类名**: `com.example.platform.config.SecurityConfig`

通过 `@Configuration` + `@EnableWebSecurity` 注册，定义 JWT 过滤链、端点访问规则和 RBAC 鉴权策略。

#### 注册的 Bean

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `passwordEncoder` | `BCryptPasswordEncoder` | 密码加密器，用于用户注册/登录时的密码哈希 |
| `customAuthenticationEntryPoint` | `CustomAuthenticationEntryPoint` | 未认证入口点，返回 401 JSON 响应 |
| `customAccessDeniedHandler` | `CustomAccessDeniedHandler` | 权限不足处理器，返回 403 JSON 响应 |
| `securityFilterChain` | `SecurityFilterChain` | 安全过滤链，定义认证/鉴权规则 |

#### RBAC 权限层级

| 角色 | 权限范围 |
|------|----------|
| `ADMIN` | 全部操作 + 用户管理 + 删除操作 + Druid 监控 + 审计日志清理 |
| `OPERATOR` | 设备管理 + 告警处理 + 数据上报 + 只读查询 |
| `VIEWER` | 只读查询（设备/数据/告警/日志） |

#### 端点访问规则

| 规则 | 路径/方法 | 允许角色 |
|------|-----------|----------|
| 公开端点 | `/auth/login` | 无需认证 |
| Druid 监控 | `/druid/**` | ADMIN |
| DELETE 操作 | 所有 DELETE 请求 | ADMIN |
| 用户管理 | `/users/**` | ADMIN |
| 审计日志 | `/audit-logs/**`（DELETE 由上方规则控制） | ADMIN, OPERATOR |
| 设备写入/数据上报/指标创建 | POST `/devices`, `/device-data`, `/device-data/batch`, `/metric` | ADMIN, OPERATOR |
| 设备更新/类型更新/指标更新 | PUT `/devices/**`, `/device-types/**`, `/metric` | ADMIN, OPERATOR |
| 只读查询 | 所有 GET 请求 | 已认证用户（含 VIEWER） |
| 兜底规则 | 其余请求 | 需认证 |

#### 安全过滤链配置

```
1. CORS → 使用 CorsConfig 提供的 CorsConfigurationSource
2. CSRF → 禁用（JWT 无状态，无需 CSRF 防护）
3. Session → 无状态（SessionCreationPolicy.STATELESS）
4. 异常处理 → CustomAuthenticationEntryPoint(401) + CustomAccessDeniedHandler(403)
5. 授权规则 → 如上表
6. JWT 过滤器 → JwtAuthenticationFilter 插入 UsernamePasswordAuthenticationFilter 之前
```

#### 设计说明

- `CustomAuthenticationEntryPoint` 和 `CustomAccessDeniedHandler` 通过 `@Bean` 方法在 `SecurityConfig` 中创建，而非 `@Component` 自动扫描，避免 `ObjectMapper` 构造注入导致的循环依赖
- JWT 过滤器在 Token 过期或无效时不抛出异常，仅不设置 `SecurityContext`，由后续 `AuthenticationEntryPoint` 返回 401

### 2.5 security 子包 — JWT 认证与鉴权组件

#### JwtAuthenticationFilter

**完整类名**: `com.example.platform.security.JwtAuthenticationFilter`

继承 `OncePerRequestFilter`，从请求头 `Authorization: Bearer <token>` 提取 JWT Token，校验签名与有效期，将用户身份与角色写入 `SecurityContext`。

| 步骤 | 说明 |
|------|------|
| 1. 提取 Token | 从 `Authorization` 请求头中提取 `Bearer ` 前缀后的 Token |
| 2. 解析 Claims | 调用 `jwtUtil.parseToken(token)` 解析 Token |
| 3. 提取身份 | 从 Claims 中提取 `userId`（sub）、`username`、`role` |
| 4. 构建权限 | 添加 `ROLE_` 前缀构建 `SimpleGrantedAuthority`，匹配 `hasRole()` 规则 |
| 5. 设置上下文 | 将 `UsernamePasswordAuthenticationToken` 写入 `SecurityContextHolder` |

异常处理：
- `ExpiredJwtException`：Token 过期，不设置认证信息，由 `CustomAuthenticationEntryPoint` 返回 401
- `JwtException`：Token 无效（签名错误/格式错误），不设置认证信息，由 `CustomAuthenticationEntryPoint` 返回 401

#### CustomAuthenticationEntryPoint

**完整类名**: `com.example.platform.security.CustomAuthenticationEntryPoint`

实现 `AuthenticationEntryPoint`，当用户未携带有效 Token 访问受保护资源时返回 401 JSON 响应。

返回格式：

```json
{
  "code": 401,
  "message": "未登录或Token已过期"
}
```

#### CustomAccessDeniedHandler

**完整类名**: `com.example.platform.security.CustomAccessDeniedHandler`

实现 `AccessDeniedHandler`，当已认证用户访问超出其角色权限的资源时返回 403 JSON 响应。

返回格式：

```json
{
  "code": 403,
  "message": "权限不足，无法访问该资源"
}
```

### 2.6 @EnableScheduling — 定时任务调度启用

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

### 2.7 application.yaml — 应用配置

```yaml
# 设备监控时间字段配置
task:
  check-device-cron: 0 0/5 * * * ?
  offline-threshold-seconds: 300

# CORS跨域配置
cors:
  allowed-origins:
    - http://localhost:*
    - http://127.0.0.1:*
```

#### 配置说明

| 配置键 | 默认值 | 说明 | 引用位置 |
|--------|--------|------|----------|
| `task.check-device-cron` | `0 0/5 * * * ?` | 设备在线状态检查的 cron 表达式 | `DeviceMonitorTask.checkDeviceOnlineStatus()` |
| `task.offline-threshold-seconds` | `300` | 设备离线判定阈值（秒），超过此时间无数据上报则判定为离线 | `DeviceMonitorTask` |
| `cors.allowed-origins` | `http://localhost:*,http://127.0.0.1:*` | CORS 允许的来源列表，支持通配符模式 | `CorsConfig.buildCorsConfiguration()` |

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
| 跨域请求被拒绝 | 前端请求不在允许的 Origin 列表中 | 在 `application.yaml` 的 `cors.allowed-origins` 中添加对应的源地址 |
| 自动填充不生效 | Entity 字段缺少 `fill` 属性 | 确保字段注解为 `@TableField(value = "create_time", fill = FieldFill.INSERT)` |
| 自动填充值为 null | `MyMetaObjectHandler` 未注册为 Spring Bean | 确保类有 `@Component` 注解 |
| 分页查询报错 | 分页插件未注册 | `MybatisPlusConfig` 已启用，确保类有 `@Configuration` 和 `@Bean` 注解 |
| 配置类不生效 | 缺少 `@Configuration` 注解 | 确保类上有 `@Configuration` 注解 |
| Bean 冲突 | 多个配置类注册了相同类型的 Bean | 使用 `@Primary` 或 `@Qualifier` 指定优先级 |
| 定时任务不执行 | `@EnableScheduling` 未添加 | 确保启动类上有 `@EnableScheduling` 注解 |
| cron 表达式不生效 | `@Scheduled` 未读取到配置值 | 检查 `application.yaml` 中 `task.check-device-cron` 配置是否存在 |
| 乐观锁不生效 | Entity 缺少 `@Version` 字段 | Entity 需添加 `@Version` 注解的整型字段（如 `private Integer version;`），MyBatis-Plus 会在 UPDATE 时自动追加 `WHERE version = 旧值` 条件 |
| 乐观锁更新失败 | 并发修改导致版本号不匹配 | `updateById` 返回 false 时表示数据已被他人修改，需重新查询后重试 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，MybatisPlusConfig 为注释状态 |
| v1.1 | 2026-05-08 | `[f88934db]` | MybatisPlusConfig 已正式启用；新增 MyMetaObjectHandler 自动填充处理器文档 |
| v1.2 | 2026-05-09 | `[0e2c8e48]` | 新增 CorsConfig 跨域配置类文档 |
| v1.3 | 2026-05-15 | `[05b8ff81]` | MyMetaObjectHandler 新增 `collectTime` 自动填充（设备数据采集时间） |
| v1.4 | 2026-05-16 | `[240739a]` | 新增 `@EnableScheduling` 定时任务调度启用说明；新增 `application.yaml` 中 `task.check-device-cron` 配置说明 |
| v2.0 | 2026-05-17 | — | MybatisPlusConfig 新增 `OptimisticLockerInnerInterceptor` 乐观锁插件（IoT 设备状态更新防丢失更新）；CorsConfig `allowedOrigins` 改为从配置文件动态读取 `@Value("${cors.allowed-origins}")`，不再硬编码；MyMetaObjectHandler `createTime` 改用 `strictInsertFill`（仅 null 时填充），`updateTime` 仍用 `setFieldValByName` 强制覆盖；`application.yaml` 新增 `cors.allowed-origins` 和 `task.offline-threshold-seconds` 配置项 |
| v3.0 | 2026-05-24 | — | 新增 SecurityConfig 文档（JWT 过滤链、RBAC 鉴权、BCrypt 密码加密）；新增 security 子包文档（JwtAuthenticationFilter、CustomAuthenticationEntryPoint、CustomAccessDeniedHandler）；CorsConfig 改为提供 `CorsConfigurationSource` Bean 供 Spring Security 引用（不再注册 CorsFilter）；新增 JWT 相关配置项说明 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-24。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
