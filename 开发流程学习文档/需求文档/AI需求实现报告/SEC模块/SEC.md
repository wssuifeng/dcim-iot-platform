
任务一、二完成。现在进入 **任务三：全面总结性汇报**。

---

# 📊 REQ-SEC-FR-001 & REQ-SEC-FR-002 模块总结性汇报

---

## 一、模块新增功能详细说明

### 1.1 REQ-SEC-FR-001：用户登录认证模块

| 功能项       | 需求编号       | 实现说明                                                     |
| :----------- | :------------- | :----------------------------------------------------------- |
| 用户登录认证 | REQ-SEC-FR-001 | `POST /auth/login`，验证用户名+密码，签发JWT Token           |
| 密码加密存储 | REQ-SEC-FR-001 | BCrypt算法加密，强度因子10，密码明文8-32位须含大小写字母和数字 |
| 账号锁定机制 | REQ-SEC-FR-001 | 连续5次登录失败锁定30分钟，锁定过期自动解锁并重置计数        |
| 账号停用检查 | REQ-SEC-FR-001 | 管理员可停用账号（status=1），停用账号无法登录               |
| 安全审计日志 | REQ-SEC-FR-001 | 登录成功/失败均记录审计日志，含操作人、IP、详情              |
| 防用户名枚举 | REQ-SEC-FR-001 | 用户不存在与密码错误返回相同提示"用户名或密码错误"           |

### 1.2 REQ-SEC-FR-002：Token校验与接口鉴权模块

| 功能项            | 需求编号       | 实现说明                                                     |
| :---------------- | :------------- | :----------------------------------------------------------- |
| JWT Token签发     | REQ-SEC-FR-002 | 登录成功后签发HS256签名JWT，有效期2小时，载荷含userId/username/role |
| Token校验过滤器   | REQ-SEC-FR-002 | `JwtAuthenticationFilter`拦截每个请求，从Authorization头提取Bearer Token |
| RBAC角色鉴权      | REQ-SEC-FR-002 | ADMIN/OPERATOR/VIEWER三级角色，URL+HTTP方法级别的访问控制    |
| 未认证处理(401)   | REQ-SEC-FR-002 | `CustomAuthenticationEntryPoint`返回标准JSON格式401响应      |
| 权限不足处理(403) | REQ-SEC-FR-002 | `CustomAccessDeniedHandler`返回标准JSON格式403响应           |
| CORS跨域支持      | REQ-SEC-FR-002 | 与Spring Security集成的CORS配置，支持localhost开发环境       |
| 无状态会话        | REQ-SEC-FR-002 | `SessionCreationPolicy.STATELESS`，不使用HttpSession         |

### 1.3 端点权限矩阵

| 端点模式                                   | ADMIN | OPERATOR | VIEWER | 未认证 |
| :----------------------------------------- | :---: | :------: | :----: | :----: |
| `POST /auth/login`                         |   ✅   |    ✅     |   ✅    |   ✅    |
| `GET /**`                                  |   ✅   |    ✅     |   ✅    |   ❌    |
| `POST /devices,/device-data,/metric`       |   ✅   |    ✅     |   ❌    |   ❌    |
| `PUT /devices/**,/device-types/**,/metric` |   ✅   |    ✅     |   ❌    |   ❌    |
| `GET /audit-logs/**`                       |   ✅   |    ✅     |   ❌    |   ❌    |
| `DELETE /**`                               |   ✅   |    ❌     |   ❌    |   ❌    |
| `/users/**`                                |   ✅   |    ❌     |   ❌    |   ❌    |
| `/druid/**`                                |   ✅   |    ❌     |   ❌    |   ❌    |

---

## 二、核心算法与实现思路

### 2.1 JWT Token 生成与验证流程

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Client      │     │  Filter      │     │  Controller  │
│  POST /login │────▶│  Chain       │────▶│  AuthCtrl    │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                  │ login()
                                                  ▼
                                          ┌──────────────┐
                                          │ AuthService  │
                                          │ ①查用户      │
                                          │ ②检锁定      │
                                          │ ③检停用      │
                                          │ ④验密码      │
                                          │ ⑤重置计数    │
                                          │ ⑥签发Token   │
                                          │ ⑦记审计日志  │
                                          └──────────────┘
```

**Token结构**:
```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "1", "username": "admin", "role": "ADMIN",
           "iat": 1716480000, "exp": 1716487200 }
Signature: HMACSHA256(base64(header) + "." + base64(payload), secretKey)
```

### 2.2 登录失败计数与锁定算法

```
输入: loginFailCount=N, lockTime=T

if T != null && T > now()     → 抛出AccountLockedException（锁定中）
if T != null && T <= now()    → 重置计数为0，清除lockTime（锁定过期）
密码错误:
  N = COALESCE(N, 0) + 1      → SQL原子递增（防并发竞态）
  if N >= 5 && lockTime == null → 设置lockTime = now() + 30min
密码正确:
  重置 N=0, lockTime=null
```

**关键设计决策**:
- 使用 `SET login_fail_count = COALESCE(login_fail_count, 0) + 1` 原子递增，避免 read-then-write 竞态条件
- 锁定过期时在 `checkAccountLocked` 中重置计数，而非等到登录成功后，确保后续密码错误从0开始计数
- `lockJustExpired` 标志避免 `resetLoginFailCount` 被重复调用

### 2.3 Spring Security 过滤链执行顺序

```
Request → CorsFilter → CsrfFilter(disabled) → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter → ... → Controller
                                         │
                                         ├─ Token有效 → 设置SecurityContext → 继续过滤链
                                         ├─ Token过期 → 不设置认证 → EntryPoint返回401
                                         └─ Token无效 → 不设置认证 → EntryPoint返回401
```

---

## 三、代码结构与设计模式分析

### 3.1 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                       │
│  AuthController    SysUserController    SysAuditLogCtrl  │
│  (POST /login)    (CRUD /users)        (GET /audit-logs)│
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Service Layer                          │
│  AuthServiceImpl    SysUserServiceImpl    SysAuditLog... │
│  (登录认证逻辑)    (用户CRUD+密码加密)   (审计日志记录)  │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Mapper Layer                           │
│  SysUserMapper              SysAuditLogMapper            │
│  (findByUsername)           (deleteByCreateTimeBefore)   │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│                    Entity Layer                           │
│  SysUser (id,username,password,role,status,              │
│           loginFailCount,lockTime)                       │
│  SysAuditLog (id,userId,username,operation,ip,detail)    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Security Layer                         │
│  JwtUtil → JwtAuthenticationFilter → SecurityConfig      │
│  CustomAuthenticationEntryPoint(401)                      │
│  CustomAccessDeniedHandler(403)                           │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                    Common Layer                           │
│  Result / ResultCode / BusinessException                 │
│  AccountLockedException / GlobalExceptionHandler         │
│  CreateGroup / UpdateGroup                               │
└─────────────────────────────────────────────────────────┘
```

### 3.2 使用的设计模式

| 设计模式       | 应用位置                  | 说明                                                    |
| :------------- | :------------------------ | :------------------------------------------------------ |
| **策略模式**   | `PasswordEncoder`         | 通过接口注入BCryptPasswordEncoder，可替换为其他加密算法 |
| **模板方法**   | `OncePerRequestFilter`    | JwtAuthenticationFilter继承模板，实现doFilterInternal   |
| **责任链模式** | SecurityFilterChain       | Spring Security过滤器链，逐层处理请求                   |
| **工厂方法**   | `JwtUtil.generateToken()` | 封装JWT构建逻辑，调用方无需关心签名细节                 |
| **统一外观**   | `Result<T>`               | 所有API响应统一包装，前端处理逻辑一致                   |
| **异常转译**   | `GlobalExceptionHandler`  | 将各类异常转译为统一的Result响应                        |
| **依赖注入**   | 全部Service/Controller    | 构造器注入，避免字段注入的隐藏依赖                      |

### 3.3 新增文件清单

| 文件                                                         |      行数 | 类型 | 说明                                         |
| :----------------------------------------------------------- | --------: | :--- | :------------------------------------------- |
| [SysUser.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/entity/SysUser.java) |        76 | 修改 | 新增loginFailCount/lockTime/status字段及校验 |
| [SysAuditLog.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/entity/SysAuditLog.java) |        45 | 新增 | 审计日志实体                                 |
| [LoginDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/LoginDTO.java) |        19 | 新增 | 登录请求DTO                                  |
| [LoginResponseDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/LoginResponseDTO.java) |        31 | 新增 | 登录响应DTO                                  |
| [PasswordResetDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/PasswordResetDTO.java) |        20 | 新增 | 密码重置DTO                                  |
| [SysUserMapper.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/mapper/SysUserMapper.java) |        22 | 修改 | 新增findByUsername                           |
| [SysAuditLogMapper.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/mapper/SysAuditLogMapper.java) |        24 | 新增 | 审计日志Mapper                               |
| [AuthService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/AuthService.java) |        17 | 新增 | 认证服务接口                                 |
| [AuthServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AuthServiceImpl.java) |       153 | 新增 | 认证服务实现                                 |
| [SysUserService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/SysUserService.java) |        74 | 修改 | 新增登录相关方法                             |
| [SysUserServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/SysUserServiceImpl.java) |       188 | 修改 | 实现登录计数/锁定/密码重置                   |
| [SysAuditLogService.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/SysAuditLogService.java) |        35 | 新增 | 审计日志服务接口                             |
| [SysAuditLogServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/SysAuditLogServiceImpl.java) |        73 | 新增 | 审计日志服务实现                             |
| [AuthController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/AuthController.java) |        55 | 新增 | 登录接口                                     |
| [SysUserController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/SysUserController.java) |       129 | 修改 | 新增密码重置/状态管理接口                    |
| [SysAuditLogController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/SysAuditLogController.java) |        54 | 新增 | 审计日志接口                                 |
| [JwtUtil.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/util/JwtUtil.java) |       105 | 新增 | JWT工具类                                    |
| [JwtAuthenticationFilter.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/JwtAuthenticationFilter.java) |        90 | 新增 | JWT认证过滤器                                |
| [CustomAuthenticationEntryPoint.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/CustomAuthenticationEntryPoint.java) |        44 | 新增 | 401处理器                                    |
| [CustomAccessDeniedHandler.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/CustomAccessDeniedHandler.java) |        44 | 新增 | 403处理器                                    |
| [SecurityConfig.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/config/SecurityConfig.java) |        97 | 新增 | Security核心配置                             |
| [CorsConfig.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/config/CorsConfig.java) |        43 | 新增 | CORS配置                                     |
| [AccountLockedException.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/exception/AccountLockedException.java) |        18 | 新增 | 账号锁定异常                                 |
| [ResultCode.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/ResultCode.java) |        24 | 修改 | 新增安全相关错误码                           |
| **合计**                                                     | **~1400** |      |                                              |

---

## 四、关键技术难点及解决方案

### 难点1：JWT与Spring Security集成

**问题**: Spring Security默认基于Session认证，需改为无状态JWT方式。

**解决方案**:
1. `SessionCreationPolicy.STATELESS` 禁用Session
2. 自定义 `JwtAuthenticationFilter` 继承 `OncePerRequestFilter`，在UsernamePasswordAuthenticationFilter之前执行
3. Token有效时手动设置 `SecurityContextHolder.getContext().setAuthentication()`
4. Token无效时不设置认证，由 `CustomAuthenticationEntryPoint` 返回401

### 难点2：登录失败计数的并发安全

**问题**: 先读后写（getById → update）存在竞态条件，并发请求可能读到相同值导致计数丢失。

**解决方案**: 使用 `SET login_fail_count = COALESCE(login_fail_count, 0) + 1` SQL原子递增，将读-改-写合并为单条SQL，利用数据库行锁保证原子性。

### 难点3：updateUser越权修改防护

**问题**: `updateById(user)` 会更新请求体中所有非null字段，攻击者可注入password/status/role等字段。

**解决方案**: 改用 `LambdaUpdateWrapper` 白名单模式，仅允许更新nickname和role字段，从根本上杜绝字段注入。

### 难点4：CORS与Spring Security的集成

**问题**: Spring Security的过滤器链在CORS之前执行，导致预检请求(OPTIONS)被拦截。

**解决方案**: 在SecurityConfig中通过 `http.cors(cors -> cors.configurationSource(corsConfigurationSource))` 将CORS配置注入Security过滤链，确保CORS在鉴权之前处理。

### 难点5：敏感数据防泄露

**问题**: SysUser实体的password/status/loginFailCount/lockTime字段会通过Jackson序列化暴露给前端。

**解决方案**: 为这4个字段添加 `@JsonIgnore` 注解，在序列化时自动排除。

---

## 五、测试结果与性能评估

### 5.1 编译验证

| 检查项        | 结果               |
| :------------ | :----------------- |
| `mvn compile` | ✅ 零错误零警告     |
| `mvn test`    | ✅ 已有测试全部通过 |

### 5.2 性能评估

| 指标           | 评估     | 说明                                                         |
| :------------- | :------- | :----------------------------------------------------------- |
| JWT解析性能    | ✅ 优     | 每请求仅解析1次Token（优化前3次），密钥@PostConstruct缓存    |
| 登录接口DB操作 | ⚠️ 可接受 | 正常登录2次DB操作（查用户+重置计数），失败登录3次（查用户+原子递增+查最新值） |
| 过滤器开销     | ✅ 优     | 无Token请求直接放行，不执行JWT解析                           |
| 密码加密       | ✅ 优     | BCrypt单次验证耗时~100ms，属安全设计预期                     |

### 5.3 安全评估

| 检查项         | 状态 | 说明                                          |
| :------------- | :--- | :-------------------------------------------- |
| 密码泄露防护   | ✅    | @JsonIgnore + BCrypt加密                      |
| 敏感字段泄露   | ✅    | status/loginFailCount/lockTime已加@JsonIgnore |
| 用户名枚举防护 | ✅    | 不存在与密码错误返回相同提示                  |
| 越权修改防护   | ✅    | LambdaUpdateWrapper白名单模式                 |
| 并发竞态防护   | ✅    | SQL原子递增                                   |
| Token伪造防护  | ✅    | HMAC-SHA256签名验证                           |
| CSRF防护       | ✅    | 无状态JWT无需CSRF                             |

---

## 六、后续优化建议

| 优先级 | 建议                              | 说明                                                         |
| :----: | :-------------------------------- | :----------------------------------------------------------- |
|  🔴 高  | 实现Token黑名单（REQ-SEC-FR-003） | 账号停用后已发放的Token仍可使用，需配合Redis实现Token主动失效 |
|  🔴 高  | JWT Secret环境变量化              | 生产环境应使用 `${JWT_SECRET}` 注入，避免密钥泄露到版本库    |
|  🟡 中  | 审计日志归档替代硬删除            | 大量DELETE可能锁表，建议改为归档或分表策略                   |
|  🟡 中  | SysUser status字段枚举化          | 定义 `STATUS_NORMAL=0`/`STATUS_DISABLED=1` 常量，消除魔法数字 |
|  🟡 中  | 登录接口限流                      | 添加IP级别限流（如5次/分钟），防止暴力破解                   |
|  🟢 低  | 审计日志异步写入                  | 使用 `@Async` 异步记录审计日志，减少登录接口响应时间         |
|  🟢 低  | Token刷新机制                     | 实现Refresh Token，支持Token续期而不需重新登录               |
|  🟢 低  | 登录设备管理                      | 记录用户登录设备信息，支持踢出其他设备                       |


toolName: todo_write
            
status: success
          
Todos updated: 10 items
          

---

三个任务全部完成。以下是执行摘要：

## 执行总结

### 任务一：系统性多轮复核与修复

发现并修复了 **5个问题**：

| #    | 问题                                                    | 修复                                           |
| ---- | :------------------------------------------------------ | :--------------------------------------------- |
| R1   | `resetPassword()` 使用 `updateById` 覆盖并发修改        | 改用 `LambdaUpdateWrapper` 仅更新password字段  |
| R2   | `search()` 返回HTTP 200+body 404，与 `getById()` 不一致 | 改为抛出 `UserNotFoundException`（HTTP 404）   |
| R3   | `checkAccountLocked` 过期重置与登录成功重置冗余         | 返回 `boolean lockJustExpired`，避免重复DB写入 |
| R4   | `update()` 中 `getUserOrThrow` 冗余调用                 | 移除Controller层冗余检查，由Service层保证      |
| R5   | `ObjectMapper` 各自new实例，未复用Spring容器实例        | 改为构造注入+`@Component`注册                  |

### 任务二：标准化注释

- 补充了 `AuthServiceImpl` 3个常量字段的文档注释
- 为 `login()` 方法7个步骤添加了行内注释
- 为 `incrementLoginFailCount()` 3个步骤添加了行内注释
- 为 `updateUser()` 添加了安全设计说明注释
- 为 `SecurityConfig` 9条授权规则添加了行内注释
- 为 `JwtAuthenticationFilter` 关键步骤添加了行内注释

### 任务三：总结性汇报

已在上方完整输出，涵盖6个维度：功能说明、核心算法、代码结构、技术难点、测试评估、优化建议。