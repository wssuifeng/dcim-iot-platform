# SEC模块学习指南

> **版本**: V2.0.0 | **创建**: 2026-05-23 | **修订**: 2026-05-23
>
> **适用对象**: 掌握Java基础与Spring常用注解，但不熟悉JWT与Spring Security的开发者
>
> **关联需求**: REQ-SEC-FR-001（用户JWT认证登录）、REQ-SEC-FR-002（Token校验与接口鉴权）

### 本次重点优化了以下3个方面

1. **术语去黑话化**：将"竞态条件""原子递增""行锁""COALESCE""序列化"等高级术语全部替换为大白话+业务场景解释，不再假设你懂任何未声明的概念
2. **阅读路线图细化**：每一步补充了"为什么这一步要先读"的理由，以及"读完这一步你应该能回答的问题"作为自测打卡点
3. **沙盒演练可操作性**：三道题全部补充了具体要改哪个文件的哪一行、改完怎么验证、预期看到什么结果，做到只看文档和代码就敢动手

---

## 1. 模块全景定位（宏观视角）

### 一句话概述

SEC模块就像一栋办公大楼的**安保门禁系统**——大门口有个前台（登录接口），你报上工号和密码，前台核实后给你一张临时通行证（JWT Token）；之后你进出任何房间，保安（过滤器）只看通行证，不再反复查你身份证；而不同楼层有不同权限，普通员工只能进开放区域，经理能进更多房间，老板哪里都能去（RBAC角色鉴权）。

### 核心职责清单

| # | 职责 | 对应生活类比 |
|---|:-----|:-------------|
| 1 | **用户登录认证**：验证用户名+密码，签发JWT通行证 | 前台核验身份后发放门禁卡 |
| 2 | **请求拦截鉴权**：每个请求自动校验Token，无证或过期拒绝进入 | 保安在每个门口刷卡验卡 |
| 3 | **角色权限控制**：ADMIN/OPERATOR/VIEWER三级权限，不同角色访问不同接口 | 普通卡/经理卡/老板卡能刷开的门不同 |
| 4 | **账号安全防护**：连续5次密码错误锁定30分钟，防暴力破解 | 连续输错密码ATM机吞卡 |
| 5 | **安全审计追踪**：登录成功/失败均记录操作日志，含IP和时间 | 大楼门禁系统记录每次刷卡 |

> **这5项职责的协作关系**：职责1是"入口"，职责2是"守门"，职责3是"分级"，职责4是"防破"，职责5是"留痕"。它们共同构成了一条完整的安全防线。

---

## 2. 技术盲区速查字典（扫清障碍）

> **使用方法**：遇到看不懂的术语，先来这里查。每个术语都附有"生活类比"和"代码位置"，看完类比再去看代码，就不会懵。

### 2.1 框架与规范

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **JWT (JSON Web Token)** | 用户登录成功后，服务端生成一串加密字符串作为"通行证"，客户端后续请求携带此串即可证明身份 | 像一张自带防伪标记的临时通行证，上面写了你的姓名、部门、有效期，保安只需验证防伪标记就能确认真假，不需要打电话回前台查 | JwtUtil.java 第41行 `generateToken()` |
| **Spring Security** | Spring官方的安全框架，提供"谁可以访问什么接口"的声明式配置 | 像一整套安保管理制度：前台怎么验证、保安怎么巡逻、门禁规则怎么配，都按制度执行 | SecurityConfig.java |
| **RBAC (Role-Based Access Control)** | 基于角色的访问控制：给用户分配角色，给角色分配权限，而非直接给用户分配权限 | 像公司按职位发工牌：老板工牌刷所有门，经理工牌刷部分门，普通员工工牌只能刷大门和自己的办公室 | SecurityConfig.java 第79-99行 `authorizeHttpRequests` |
| **BCrypt** | 一种密码加密算法，每次加密同一密码会产生不同的密文，且验证速度故意很慢（约100ms），让暴力破解成本极高 | 像一个特殊的保险箱锁：每次锁上的方式都不同，即使小偷拿到锁的样品，也猜不出钥匙长什么样 | SysUserServiceImpl.java 第61行 `passwordEncoder.encode()` |
| **CORS (跨域资源共享)** | 浏览器安全策略，默认禁止网页向不同域名的服务器发请求，需服务端声明"允许哪些来源访问" | 像小区门禁默认不让外卖员进，物业需要在系统中登记"允许美团外卖进入"才行 | CorsConfig.java |

### 2.2 Spring Security核心概念

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **SecurityFilterChain** | Spring Security的核心执行链，请求按顺序经过多个过滤器，每个过滤器负责一个安全检查环节 | 像机场安检流水线：先查身份证→再过金属探测器→再查行李→最后登机，每个环节由不同人负责 | SecurityConfig.java 第70行 `securityFilterChain()` |
| **OncePerRequestFilter** | Spring提供的抽象类，确保过滤器对每个HTTP请求只执行一次（不会因转发而重复执行） | 像安检只做一次，不会因为你从候机厅走回安检口就再安检一遍 | JwtAuthenticationFilter.java 第28行 继承声明 |
| **SecurityContext** | Spring Security的"安全上下文"，就是一个内存中的小盒子，存储当前请求的认证信息（谁登录了、什么角色），后续鉴权直接从这个盒子里取 | 像你过了安检后拿到的登机牌，后续每个环节只需看登机牌，不用再查身份证 | JwtAuthenticationFilter.java 第70行 |
| **AuthenticationEntryPoint** | Spring Security的接口，当未认证用户访问受保护资源时触发，负责返回401响应 | 像保安拦住没带工牌的人，告诉他"请先去前台办证" | CustomAuthenticationEntryPoint.java |
| **AccessDeniedHandler** | Spring Security的接口，当已认证用户访问超出其权限的资源时触发，负责返回403响应 | 像普通员工刷老板专属房间时，保安说"你的工牌等级不够" | CustomAccessDeniedHandler.java |
| **UsernamePassword<br>AuthenticationToken** | Spring Security的认证令牌对象，就是一个Java对象，里面封装了"用户ID + 权限列表"，写入SecurityContext后即表示"此人已认证" | 像一张电子工牌，上面有你的工号和权限等级 | JwtAuthenticationFilter.java 第66-67行 |

### 2.3 JWT内部结构

> **JWT长什么样？** 它是一串用`.`分隔的三段式字符串：`eyJhbG...（头部）.eyJzdW...（载荷）.SflKx...（签名）`。下面解释这三段分别是什么。

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **Claims（声明）** | JWT第二段"载荷"中携带的用户数据，包括userId、username、role | 像通行证正面写的"姓名：张三，部门：运维，有效期至：18:00" | JwtUtil.java 第60行 `parseToken()` 返回值 |
| **Subject (sub)** | JWT的一个标准字段，表示"这张证是谁的"（本模块存用户ID） | 通行证上的"工号"字段 | JwtUtil.java 第46行 `.subject(String.valueOf(userId))` |
| **Expiration (exp)** | JWT的一个标准字段，表示Token的过期时间戳 | 通行证上的"有效期至"字段 | JwtUtil.java 第50行 `.expiration(expiryDate)` |
| **HMAC-SHA256签名** | Token的签名算法：用一把只有服务端知道的密钥，对"头部+载荷"进行数学运算，生成一串防篡改的校验码 | 像通行证上的防伪钢印——用专用工具压出来的，别人没有工具，伪造不出一样的钢印 | JwtUtil.java 第51行 `.signWith(signingKey)` |
| **SecretKey（密钥）** | HMAC签名的密钥，只有服务端持有，用于生成签名和验证签名 | 像制作防伪钢印的模具，只有官方才有，别人做不出一样的钢印 | JwtUtil.java 第25行 `signingKey` 字段 |

### 2.4 设计模式与高级语法

| 技术名词 | 在本模块中的作用 | 生活类比解释 | 对应代码位置 |
|:---------|:-----------------|:-------------|:-------------|
| **策略模式 (PasswordEncoder)** | 密码加密通过接口注入，可随时替换加密算法（如从BCrypt换为SCrypt），无需修改业务代码 | 像锁匠用统一接口装锁，客户要装电子锁就装电子锁，要装机械锁就装机械锁，锁匠的安装流程不变 | SecurityConfig.java 第43行 `@Bean PasswordEncoder` |
| **模板方法模式 (OncePerRequestFilter)** | Spring定义了过滤器的骨架流程（何时执行、如何保证只执行一次），我们只需填充`doFilterInternal`的具体逻辑 | 像考试答题卡已经印好了题目框架，你只需在横线上写答案，不用管答题卡怎么印刷的 | JwtAuthenticationFilter.java 第45行 `doFilterInternal()` |
| **责任链模式 (FilterChain)** | 请求依次经过多个过滤器，每个过滤器处理自己关心的部分，然后传递给下一个 | 像公文审批流程：科员→科长→处长→局长，每人批自己的部分然后往下传 | JwtAuthenticationFilter.java 第81行 `filterChain.doFilter()` |
| **@PostConstruct** | Bean初始化后自动执行的方法，本模块用于预生成签名密钥，避免每次请求重复计算 | 像餐厅开门前先把调料备好，不用等客人点菜再切葱姜蒜 | JwtUtil.java 第32行 `init()` |
| **@Transactional** | 声明事务边界，方法内所有数据库操作要么全部成功，要么全部回滚 | 像银行转账：扣款和入账必须同时成功，任何一步失败就全部撤销 | AuthServiceImpl.java 第57行 |
| **LambdaUpdateWrapper** | MyBatis-Plus提供的工具类，用Java代码（而非手写SQL）来构建UPDATE语句的WHERE条件和SET子句，编译期能检查字段名是否拼错 | 像填表模板，你只需在指定位置填值，不用手写SQL，也不会拼错字段名 | SysUserServiceImpl.java 第82行 |
| **@JsonIgnore** | 让Jackson在把Java对象转成JSON字符串时跳过这个字段，防止密码等敏感数据通过API返回给前端 | 像员工信息表上"薪资"一栏用黑笔涂掉，外人看不到 | SysUser.java 第35行 |
| **@Value** | 从application.yaml配置文件中读取值，注入到字段中 | 像从公司制度手册中查阅"上班时间：9:00"然后记住 | JwtUtil.java 第19-23行 |
| **@Bean** | 标注在方法上，将方法返回值注册为Spring容器管理的对象 | 像人事部门把新员工录入系统，之后所有人都能通过名字找到他 | SecurityConfig.java 第43行 |

---

## 3. 架构与数据流转（见森林）

> **阅读提示**：第2章帮你扫清了术语障碍，从这一章开始，我们要把那些术语"串起来"，看看它们在实际运行中是如何协作的。先看整体架构图（谁依赖谁），再看数据流转图（一次请求经过了哪些人的手）。

### 3.1 模块架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    【第1层】HTTP 请求入口                       │
│                    (前端发送 HTTP 请求)                           │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                【第2层】Security 过滤器链                                    │
│  ┌──────────┐  ┌──────────┐  ┌─────────────────────────────────────────┐  │
│  │CorsFilter│─▶│CsrfFilter│─▶│ JwtAuthenticationFilter                │  │
│  │(跨域检查)  │  │(已禁用)  │   │(提取Token→验签→设置SecurityCtx(安全上下文))│  │
│  └──────────┘  └──────────┘  └──────────────┬──────────────────────────┘  │
│                                              │                            │
│                    ┌─────────────────────────┤                            │
│                    │ Token无效/过期           │ Token有效                   │
│                    ▼                         ▼                            │
│  ┌──────────────────────────┐  ┌──────────────────────────┐               │
│  │CustomAuthenticationEntry │  │  Spring Security 鉴权     │               │
│  │Point → 返回401 JSON      │  │  (hasRole/hasAnyRole)    │                │
│  └──────────────────────────┘  └────────────┬─────────────┘               │
│                                              │                            │
│                              ┌───────────────┤                            │
│                              │ 权限不足       │ 权限通过                     │
│                              ▼               ▼                            │
│               ┌──────────────────────┐  ┌─────────────┐                   │
│               │CustomAccessDenied    │  │ Controller  │                   │
│               │Handler → 返回403 JSON│   │ (业务处理)   │                   │
│               └──────────────────────┘  └─────────────┘                   │
└───────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  【第3层】Controller 层                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ AuthController   │  │ SysUserController│  │SysAuditLog   │   │
│  │ POST /auth/login │  │ CRUD /users/**   │  │Controller    │   │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘   │
└───────────┼──────────────────────┼────────────────────┼─────────┘
            │                      │                    │
            ▼                      ▼                    ▼
┌───────────────────────────────────────────────────────────────────┐
│                   【第4层】Service 层                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐     │
│  │ AuthServiceImpl  │  │SysUserServiceImpl│  │SysAuditLog   │     │
│  │ 登录7步流程        │  │ 密码加密/计数锁定  │  │ServiceImpl   │     │
│  └────┬───┬───┬─────┘  └──────────────────┘  └──────────────┘     │
│       │   │   │                                                   │
│       ▼   ▼   ▼                                                   │
│  ┌─────────┐ ┌───────────────┐ ┌──────────┐                       │
│  │JwtUtil  │ │PasswordEncoder│ │ResultCode│                       │
│  │签发Token │ │BCrypt加密/匹配 │ │错误码常量  │                       │
│  └─────────┘ └───────────────┘ └──────────┘                       │
└───────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                 【第5层】Mapper / 数据库层                         │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │ SysUserMapper    │  │SysAuditLogMapper │                     │
│  │ findByUsername() │  │ insert/select    │                     │
│  └────────┬─────────┘  └────────┬─────────┘                     │
│           │                     │                                │
│           ▼                     ▼                                │
│  ┌──────────────────┐  ┌──────────────────┐                      │
│  │   sys_user 表    │  │  sys_audit_log 表 │                     │
│  └──────────────────┘  └──────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

> **架构图阅读方法**：从上往下看。请求从第1层进来，经过第2层的过滤器链检查，通过后到达第3层Controller，Controller调用第4层Service处理业务，Service操作第5层数据库。**任何请求都必须先过第2层这一关**，这是SEC模块的核心设计。

### 3.2 核心数据流转图：一次完整的登录请求

以下追踪用户从点击"登录"按钮到收到Token的完整数据流转过程：

```
【阶段A：前端发请求】

① 前端发送请求
   POST /api/auth/login
   Body: { "username": "admin", "password": "Admin123" }
          │
          ▼
【阶段B：过滤器链过安检】

② Spring Security 过滤器链
   CorsFilter → CsrfFilter(disabled) → JwtAuthenticationFilter
   (此请求无Token，JwtAuthenticationFilter直接放行——不设置SecurityContext，但也不拦截)
          │
          ▼
【阶段C：Controller接收请求】

③ AuthController.login()                          [AuthController.java 第31行]
   - 从HttpServletRequest提取客户端IP（支持代理场景：先查X-Forwarded-For头）
   - 调用 authService.login(loginDTO, ip)
          │
          ▼
【阶段D：Service执行7步核心流程】

④ AuthServiceImpl.login() — 7步核心流程           [AuthServiceImpl.java 第59行]
   ┌─────────────────────────────────────────────────────────────┐
   │ 第1步: sysUserService.findByUsername("admin")               │
   │        → SysUserMapper.findByUsername()                     │
   │        → SQL: SELECT * FROM sys_user WHERE username='admin' │
   │        → 返回 SysUser 对象（含加密密码、角色、锁定状态）     │
   │                                                             │
   │ 第2步: checkAccountLocked(user)                             │
   │        → 检查 lockTime 是否在未来 → 锁定中则抛异常           │
   │        → lockTime 在过去则重置计数 → 返回 lockJustExpired    │
   │                                                             │
   │ 第3步: checkAccountDisabled(user)                           │
   │        → 检查 status == 1 → 停用则抛异常                    │
   │                                                             │
   │ 第4步: passwordEncoder.matches("Admin123", 数据库密文)       │
   │        → BCrypt内部: 取密文前缀的盐值 → 重新哈希 → 对比      │
   │        → 匹配成功继续，失败则调用 handleLoginFailure()        │
   │                                                             │
   │ 第5步: sysUserService.resetLoginFailCount(userId)            │
   │        → SQL: UPDATE sys_user SET login_fail_count=0,       │
   │               lock_time=NULL WHERE id=?                     │
   │        (如果lockJustExpired=true则跳过，避免重复写入)         │
   │                                                             │
   │ 第6步: jwtUtil.generateToken(userId, username, role)         │
   │        → 构建Header: {"alg":"HS256","typ":"JWT"}            │
   │        → 构建Payload: {"sub":"1","username":"admin",         │
   │          "role":"ADMIN","exp":1716460800}                   │
   │        → 用signingKey生成签名                                │
   │        → 拼接为: header.payload.signature                   │
   │        → 返回 Token 字符串                                   │
   │                                                             │
   │ 第7步: sysAuditLogService.log(userId, username,              │
   │                              "LOGIN_SUCCESS", ip, "登录成功") │
   │        → INSERT INTO sys_audit_log(...)                      │
   └─────────────────────────────────────────────────────────────┘
          │
          ▼
【阶段E：构建响应返回前端】

⑤ 构建响应
   buildLoginResponse(token, user)
   → LoginResponseDTO {
       token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIi...",
       tokenType: "Bearer",
       expiresIn: 7200,
       userId: 1,
       username: "admin",
       nickname: "管理员",
       role: "ADMIN"
     }
          │
          ▼
⑥ 前端收到响应
   HTTP 200
   { "code": 200, "message": "success",
     "data": { "token": "eyJ...", "tokenType": "Bearer", ... } }
   → 前端将token存入localStorage
   → 后续请求在Header中携带: Authorization: Bearer eyJ...
```

### 3.3 核心数据流转图：后续请求的Token校验

登录成功后，前端每次请求都会携带Token。以下是"保安查证"的完整流程：

```
【阶段A：前端发业务请求】

① 前端发送业务请求
   GET /api/devices
   Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
          │
          ▼
【阶段B：过滤器校验Token】

② JwtAuthenticationFilter.doFilterInternal()      [JwtAuthenticationFilter.java 第45行]
   ┌─────────────────────────────────────────────────────────────┐
   │ extractToken(request)                                       │
   │ → 从Header提取 "Bearer " 后面的部分                         │
   │ → token = "eyJhbGciOiJIUzI1NiJ9..."                        │
   │                                                             │
   │ jwtUtil.parseToken(token)                                   │
   │ → 拆分: header.payload.signature                            │
   │ → 用signingKey验证签名（防篡改）                             │
   │ → 检查exp是否过期                                           │
   │ → 返回 Claims { sub="1", username="admin", role="ADMIN" }   │
   │                                                             │
   │ 提取身份: userId=1, username="admin", role="ADMIN"          │
   │                                                             │
   │ 构建权限: authorities = [ROLE_ADMIN]                        │
   │           (注意ROLE_前缀，hasRole()会自动补这个前缀来匹配)   │
   │                                                             │
   │ 设置SecurityContext:                                        │
   │ authentication = UsernamePasswordAuthenticationToken(       │
   │     principal=1, credentials=null, authorities=[ROLE_ADMIN])│
   │ authentication.setDetails("admin")                          │
   │ SecurityContextHolder.setAuthentication(authentication)      │
   └─────────────────────────────────────────────────────────────┘
          │
          ▼
【阶段C：Spring Security鉴权】

③ Spring Security 鉴权
   匹配规则: requestMatchers(HttpMethod.GET).authenticated()
   → SecurityContext中有认证信息 → 通过
          │
          ▼
【阶段D：到达Controller】

④ 到达Controller，正常处理业务
```

> **两条流转的对比**：登录请求（3.2）走的是"前台发证"路线，Token校验请求（3.3）走的是"保安验卡"路线。两者的关键区别在于：登录时JwtAuthenticationFilter直接放行（因为没有Token），而后续请求时JwtAuthenticationFilter会解析Token并设置SecurityContext。

---

## 4. 代码阅读路线图（向导）

> **为什么需要路线图？** 这些代码文件之间有明确的依赖关系：底层被上层调用。如果先看上层，你会不断遇到"这个类是干嘛的"的疑问；按照从底层到上层的顺序读，每一步都建立在前一步的理解之上，读起来才顺畅。

按照依赖关系从底层到上层阅读，每一步建立在前一步的理解之上：

### 第1步：看数据模型 — 理解"存了什么"

> **为什么先看这个？** 数据模型是所有业务逻辑的基础。不知道数据库里存了哪些字段，后面看Service层时你会一头雾水："这个lockTime哪来的？"

**打卡点**：[SysUser.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/entity/SysUser.java)、[SysAuditLog.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/entity/SysAuditLog.java)

**重点关注**：
- SysUser有哪些字段？特别注意 `password`、`status`、`loginFailCount`、`lockTime` 这4个字段上的 `@JsonIgnore` —— 为什么密码和锁定状态不能返回给前端？（答案：防止攻击者通过API响应看到密码哈希或锁定状态，从而推测系统安全策略）
- `@TableField("login_fail_count")` 做了什么？它把Java的驼峰命名（loginFailCount）映射到数据库的下划线命名（login_fail_count）
- `@NotBlank`、`@Size`、`@Pattern` 这些校验注解的 `groups` 参数 —— 同一个字段在创建和更新时校验规则可以不同

**自测问题**：SysUser中哪些字段被`@JsonIgnore`标注了？为什么偏偏是这几个字段？

---

### 第2步：看DTO — 理解"传了什么"

> **为什么第2步看这个？** DTO是Controller和Service之间传递数据的"信封"。知道了信封里装了什么，才能理解Service方法接收的参数是什么意思。

**打卡点**：[LoginDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/LoginDTO.java)、[LoginResponseDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/LoginResponseDTO.java)、[PasswordResetDTO.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/dto/PasswordResetDTO.java)

**重点关注**：
- LoginDTO只有username和password —— 请求越精简越好，不需要的字段不要传
- LoginResponseDTO有token/tokenType/expiresIn/userId/username/nickname/role —— 响应要给前端足够的信息，但**绝不包含password**
- PasswordResetDTO的密码正则 `^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$` —— 这行正则要求密码必须同时包含小写字母、大写字母和数字

**自测问题**：为什么LoginDTO不包含role字段？角色应该由谁决定？

---

### 第3步：看JWT工具 — 理解"通行证怎么制作"

> **为什么第3步看这个？** JwtUtil是整个认证体系的"印钞机"——它负责制作通行证（generateToken）和验证通行证（parseToken）。理解了它，你就理解了JWT的核心机制。

**打卡点**：[JwtUtil.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/util/JwtUtil.java)

**重点关注**：
- `@PostConstruct init()` 方法 —— 为什么不在构造器里生成密钥？因为 `@Value` 注入发生在构造器执行之后，构造器里secret还是null
- `generateToken()` 的链式调用 —— Jwts.builder()是建造者模式，.subject()/.claim()/.signWith()像流水线一样逐步组装Token
- `parseToken()` 的验证过程 —— 它不仅解析，还自动验签和检查过期，任何一步失败都会抛出JwtException

**自测问题**：如果有人修改了Token中的role值（比如把VIEWER改成ADMIN），parseToken()能检测出来吗？为什么？

---

### 第4步：看认证过滤器 — 理解"保安怎么查证"

> **为什么第4步看这个？** 有了第3步对JwtUtil的理解，现在可以看"谁在调用JwtUtil"了。JwtAuthenticationFilter就是那个"保安"——每个请求都经过它，它调用JwtUtil验证Token，然后把结果放进SecurityContext。

**打卡点**：[JwtAuthenticationFilter.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/JwtAuthenticationFilter.java)

**重点关注**：
- 继承 `OncePerRequestFilter` 而不是实现 `Filter` 接口 —— 为什么？因为Filter可能因请求转发被执行多次，OncePerRequestFilter保证只执行一次
- `extractToken()` 方法 —— 为什么是 `"Bearer "` 而不是 `"Bearer"`？注意Bearer后面有个空格，`substring` 时要跳过7个字符
- Token无效时只是 `log.debug` 而不是抛异常 —— 为什么？因为很多请求（如登录）本来就不需要Token，不设置SecurityContext即可，后续Spring Security会根据接口规则决定是否拦截
- `ROLE_` 前缀 —— `new SimpleGrantedAuthority("ROLE_" + role)` 为什么要加前缀？因为Spring Security的 `hasRole("ADMIN")` 内部会自动补 `ROLE_` 前缀来匹配

**自测问题**：如果Token过期了，JwtAuthenticationFilter会返回401吗？（提示：不会直接返回，想想是谁返回的）

---

### 第5步：看异常处理器 — 理解"被拦下后怎么回复"

> **为什么第5步看这个？** 第4步中Token无效时"不设置SecurityContext"，那请求最终怎么被拦住？就是靠这两个异常处理器——它们是Spring Security的"话务员"，负责把拦截结果翻译成前端能读懂的JSON。

**打卡点**：[CustomAuthenticationEntryPoint.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/CustomAuthenticationEntryPoint.java)、[CustomAccessDeniedHandler.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/security/CustomAccessDeniedHandler.java)

**重点关注**：
- 这两个类没有 `@Component` —— 为什么？因为如果用 `@Component` + 构造器注入 `ObjectMapper`，会和 `SecurityConfig` 形成循环依赖（SecurityConfig需要创建这两个Handler，Handler又需要ObjectMapper，而ObjectMapper的初始化可能依赖SecurityConfig）。所以改用 `SecurityConfig` 中的 `@Bean` 方法创建实例
- **401和403的区别** —— 401是"你没带证"（未认证），403是"你的证不够等级"（已认证但权限不足）
- 手动写JSON响应 —— 为什么不用 `@RestController` 返回？因为这些不是Controller方法，是Spring Security回调的接口方法，只能直接操作 `HttpServletResponse`

**自测问题**：如果一个VIEWER角色的用户调用DELETE接口，会触发哪个Handler？返回的状态码是401还是403？

---

### 第6步：看安全配置 — 理解"门禁规则怎么配"

> **为什么第6步看这个？** 前面5步看了"谁在查证"和"被拦后怎么回复"，但还没看"查证的规则是什么"。SecurityConfig就是那张"门禁规则表"——它定义了哪些接口需要什么角色才能访问。

**打卡点**：[SecurityConfig.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/config/SecurityConfig.java)

**重点关注**：
- `SessionCreationPolicy.STATELESS` —— 这一行把Spring Security从Session模式切换到JWT模式，是最关键的配置之一。Session模式是"服务端记住你"，JWT模式是"你自己带证来"
- `authorizeHttpRequests` 中规则的顺序 —— **规则是按声明顺序匹配的**，先匹配到的先生效。所以 `permitAll()` 的登录接口必须写在最前面，`anyRequest().authenticated()` 兜底规则写在最后
- `addFilterBefore` —— 把我们的JwtAuthenticationFilter插在Spring Security默认的表单登录过滤器之前，这样JWT校验先于表单登录执行

**自测问题**：如果把 `anyRequest().authenticated()` 移到 `requestMatchers("/auth/login").permitAll()` 前面，登录接口还能正常访问吗？

---

### 第7步：看登录核心逻辑 — 理解"前台怎么发证"

> **为什么第7步看这个？** 这是整个SEC模块最核心的方法——7步登录流程。有了前面6步的铺垫，你现在已经有足够的知识储备来理解每一步在做什么、为什么这么做。

**打卡点**：[AuthServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AuthServiceImpl.java)

**重点关注**：
- `login()` 方法的7步流程 —— 这是整个SEC模块最核心的方法，每一步都有明确的安全目的
- `@Transactional(rollbackFor = Exception.class)` —— 为什么登录需要事务？因为第5步重置计数和第7步写审计日志必须同时成功或同时失败
- `lockJustExpired` 标志 —— 这个boolean解决了什么问题？如果锁定刚过期，`checkAccountLocked` 已经重置了计数，`login` 方法就不需要再调 `resetLoginFailCount`，避免冗余数据库写入
- 用户不存在时也记录审计日志 —— 为什么？安全审计需要知道谁在尝试登录，即使账号不存在

**自测问题**：如果用户输错4次密码后第5次输对了，loginFailCount会变成几？

---

### 第8步：看用户服务 — 理解"计数锁定怎么实现"

> **为什么最后看这个？** incrementLoginFailCount()是整个模块中**唯一涉及并发安全**的方法。它用了一个巧妙的设计来防止"两个人同时输错密码但只计数一次"的问题。理解了这个，你就理解了为什么不能简单地"先查后改"。

**打卡点**：[SysUserServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/SysUserServiceImpl.java)

**重点关注**：
- `incrementLoginFailCount()` 的3步 —— 第1步用SQL直接在数据库中做+1（为什么不用先查后改？见第5章深潜三），第2步查最新值，第3步判断是否达到阈值
- `updateUser()` 使用 `LambdaUpdateWrapper` 而非 `updateById` —— 为什么？`updateById` 会更新请求体中所有非null字段，攻击者可以注入password字段来改密码
- `resetPassword()` 也用 `LambdaUpdateWrapper` —— 只更新password字段，不会意外覆盖其他并发修改

**自测问题**：如果两个线程同时为同一个用户调用incrementLoginFailCount()，最终计数会少算吗？（提示：不会，第5章会详细解释为什么）

---

## 5. 核心逻辑深潜（见树木）

> **阅读提示**：第4章的路线图带你走完了所有文件，这一章挑出其中**最复杂、最核心的3段代码**，逐行拆解。每段代码都用"作用→执行流→为什么→逐行注释"的四层结构剖析。

### 5.1 深潜一：登录认证7步流程

**作用说明**：这是整个SEC模块的"心脏"，用户每次登录都要走完这7步。它不仅验证密码，还做了锁定检查、停用检查、失败计数、审计日志等安全防护。

**执行流拆解**：

1. **查用户** → 用用户名从数据库查出完整用户信息（含加密密码、角色、锁定状态）
2. **查锁定** → 如果lockTime在未来，说明还在锁定期，直接拒绝；如果lockTime在过去，说明锁定已过期，自动重置计数
3. **查停用** → 如果status=1，说明管理员已停用此账号，拒绝登录
4. **验密码** → 用BCrypt的matches方法对比明文密码和数据库密文
5. **重置计数** → 登录成功，把失败计数归零（锁定刚过期时跳过，因为第2步已重置）
6. **签发Token** → 调用JwtUtil生成JWT字符串
7. **记日志** → 写入审计日志表

**为什么这么写（难点解析）**：

> **为什么用户不存在也返回"用户名或密码错误"？**
> 如果提示"用户不存在"，攻击者就能通过不断尝试用户名来探测系统中存在哪些账号（这叫"用户名枚举攻击"）。统一提示让攻击者无法区分是用户名错了还是密码错了。

> **为什么lockJustExpired要用局部变量？**
> 因为Spring的Service默认是单例（整个应用只有一个实例），如果用实例变量存储lockJustExpired，当两个用户同时登录时，用户A的lockJustExpired可能被用户B的值覆盖，导致逻辑错误。用局部变量（方法内的变量）就没有这个问题，因为每次方法调用都有自己独立的局部变量。

> **为什么登录成功要重置失败计数？**
> 如果不重置，用户之前输了4次错误密码，后来输对了，下次再输1次错就会被锁定，这不合理。登录成功意味着"用户确实知道密码"，之前的错误应该一笔勾销。

**对应代码片段（逐行注释）**：

```java
// AuthServiceImpl.java — login() 方法
@Transactional(rollbackFor = Exception.class)  // 事务：7步中任何一步抛异常，所有DB操作回滚
@Override
public LoginResponseDTO login(LoginDTO loginDTO, String ip) {

    // ===== 第1步：查用户 =====
    // 调用Mapper执行SQL: SELECT * FROM sys_user WHERE username = ?
    SysUser user = sysUserService.findByUsername(loginDTO.getUsername());

    // 用户不存在 → 记录审计日志（注意userId传null，因为用户不存在）
    // 抛出统一错误提示，不透露"用户不存在"（防用户名枚举攻击）
    if (user == null) {
        sysAuditLogService.log(null, loginDTO.getUsername(), "LOGIN_FAIL", ip,
                "用户不存在");
        throw new BusinessException(
                ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
        // 关键：错误码INVALID_CREDENTIALS，和密码错误用的是同一个错误码
        // 攻击者无法从错误信息中判断是用户名错了还是密码错了
    }

    // ===== 第2步：查锁定 =====
    // checkAccountLocked返回boolean：
    //   true  = 锁定刚过期（已自动重置计数），login方法不需要再调resetLoginFailCount
    //   false = 没有锁定 或 从未被锁定
    boolean lockJustExpired = checkAccountLocked(user);

    // ===== 第3步：查停用 =====
    // status=1表示停用，由管理员操作，停用后无法登录
    checkAccountDisabled(user);

    // ===== 第4步：验密码 =====
    // BCrypt.matches(明文, 密文) → 内部提取密文中的盐值 → 用盐值对明文重新哈希 → 对比结果
    // 注意：BCrypt每次加密同一明文会产生不同密文（因为盐值随机），但matches总能正确验证
    if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
        handleLoginFailure(user, ip);  // 密码错误 → 递增计数 + 可能锁定 + 抛异常
    }

    // ===== 第5步：重置计数 =====
    // 只有lockJustExpired=false时才需要重置
    // 如果lockJustExpired=true，说明checkAccountLocked中已经重置过了，再调一次是浪费DB操作
    if (!lockJustExpired) {
        sysUserService.resetLoginFailCount(user.getId());
    }

    // ===== 第6步：签发Token =====
    // 将userId、username、role写入JWT载荷，设置2小时过期，用HMAC-SHA256签名
    String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

    // ===== 第7步：记日志 =====
    sysAuditLogService.log(user.getId(), user.getUsername(), "LOGIN_SUCCESS", ip,
            "登录成功");

    // 构建响应：token + 用户基本信息（不含密码！）
    return buildLoginResponse(token, user);
}
```

---

### 5.2 深潜二：JWT过滤器Token校验

**作用说明**：每个HTTP请求都会经过这个过滤器，它负责从请求头中提取Token、验证签名和有效期、将用户身份写入Spring Security上下文。这是"保安查证"的核心环节。

**执行流拆解**：

1. **提取Token** → 从Authorization请求头中取出 `Bearer xxx` 格式的Token字符串
2. **解析Token** → 验证HMAC签名（防篡改）+ 检查exp过期时间
3. **提取身份** → 从Claims中读取userId、username、role
4. **构建权限** → 创建 `ROLE_ADMIN`/`ROLE_OPERATOR`/`ROLE_VIEWER` 格式的权限列表
5. **设置上下文** → 将认证信息写入SecurityContext，后续Spring Security自动鉴权
6. **放行请求** → 调用 `filterChain.doFilter()` 传递给下一个过滤器

**为什么这么写（难点解析）**：

> **为什么Token无效时不抛异常而是静默放行？**
> 因为很多请求本来就不需要Token（如 `POST /auth/login`），如果无Token就抛异常，登录接口也会被拦截。正确做法是：不设置SecurityContext，让Spring Security的鉴权规则决定是否放行——登录接口配了 `permitAll()` 所以能通过，其他接口配了 `authenticated()` 所以会被拦截到EntryPoint返回401。

> **为什么用 `UsernamePasswordAuthenticationToken` 而不是自定义类？**
> 这是Spring Security的标准认证令牌，框架内部的所有鉴权逻辑都认这个类型。它的构造函数三参数版（principal, credentials, authorities）表示"已认证"状态，两参数版表示"未认证"状态。我们用三参数版，告诉Spring Security"这个人已经验证过了"。

> **为什么 `authentication.setDetails(username)` ？**
> principal已经存了userId，但很多业务场景需要username（如审计日志），存在details里方便后续取用，不用再查数据库。

**对应代码片段（逐行注释）**：

```java
// JwtAuthenticationFilter.java — doFilterInternal() 方法
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {

    // ===== 第1步：从请求头提取Token =====
    String token = extractToken(request);
    // extractToken内部逻辑：
    //   String bearerToken = request.getHeader("Authorization");
    //   if (有值 && 以"Bearer "开头) return 去掉前缀后的部分;
    //   else return null;
    // 注意："Bearer "有7个字符（含空格），代码用 BEARER_PREFIX.length() 跳过它们

    if (token != null) {  // 有Token才校验，无Token直接放行（由后续规则决定是否拦截）
        try {
            // ===== 第2步：解析Token，验证签名+有效期 =====
            // 内部流程：拆分header.payload.signature → 用signingKey验证签名 → 检查exp
            // 签名不匹配 → 抛出JwtException（说明Token被篡改）
            // 已过期     → 抛出ExpiredJwtException（说明Token超时）
            Claims claims = jwtUtil.parseToken(token);

            // ===== 第3步：从Claims提取用户身份 =====
            Long userId = Long.parseLong(claims.getSubject());  // subject存的是userId的字符串形式
            String username = claims.get("username", String.class);  // 自定义claim
            String role = claims.get("role", String.class);          // 自定义claim

            // ===== 第4步：构建Spring Security权限列表 =====
            // ⚠️ 必须加"ROLE_"前缀！
            // 原因：Spring Security的 hasRole("ADMIN") 内部会自动补"ROLE_"前缀来匹配
            // 如果这里只写"ADMIN"，hasRole匹配的是"ROLE_ADMIN"，对不上就永远403
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            // ===== 第5步：将认证信息写入SecurityContext =====
            // 三参数构造 = 已认证状态
            //   principal   = userId（后续可通过authentication.getPrincipal()获取）
            //   credentials = null（密码不需要存在Token里）
            //   authorities = 权限列表（用于鉴权）
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(username);  // 附加信息：存username方便后续取用

            SecurityContextHolder.getContext().setAuthentication(authentication);
            // 写入后，当前线程的任何位置都可以通过 SecurityContextHolder.getContext()
            // .getAuthentication() 获取到这个认证信息

        } catch (ExpiredJwtException e) {
            // Token过期：不设置认证信息 → SecurityContext为空 → 后续鉴权判定为"未认证"
            // → 请求被拦截到CustomAuthenticationEntryPoint → 返回401
            log.debug("JWT Token已过期: {}", e.getMessage());
        } catch (JwtException e) {
            // Token无效（签名错误/格式错误）：同上，不设置认证信息
            log.debug("JWT Token无效: {}", e.getMessage());
        }
    }

    // ===== 第6步：放行请求，传递给过滤器链的下一个环节 =====
    // ⚠️ 无论Token是否有效，都必须调用这行！
    // 否则请求被"吞掉"，永远到不了Controller
    filterChain.doFilter(request, response);
}
```

---

### 5.3 深潜三：登录失败计数与锁定（并发安全）

**作用说明**：当用户输入错误密码时，系统需要记录失败次数，达到5次就锁定30分钟。这个看似简单的计数器，在"两个人同时输错密码"的场景下暗藏陷阱——如果设计不当，两次失败可能只被计数一次。

**执行流拆解**：

1. **数据库直接+1** → 用一条SQL `SET login_fail_count = COALESCE(login_fail_count, 0) + 1` 直接在数据库中完成递增，而不是先查出来再加1写回去
2. **读取最新值** → 递增后再查一次数据库，拿到最新的计数值
3. **判断锁定** → 如果计数≥5且尚未设置lockTime，则设置lockTime为当前时间+30分钟

**为什么这么写（难点解析）**：

> **为什么不能用"先查后改"？**
> 假设当前loginFailCount=4，两个请求几乎同时到达：
> ```
> 线程A: SELECT login_fail_count → 得到4
> 线程B: SELECT login_fail_count → 得到4  (线程A还没UPDATE，B读到的还是旧值)
> 线程A: UPDATE SET login_fail_count = 5   (4+1=5)
> 线程B: UPDATE SET login_fail_count = 5   (4+1=5，应该是6才对！)
> ```
> 两次失败只计数了一次！这就是"先读后写"的问题——两个线程读了同一个旧值，各自+1后写入，结果少算了一次。
>
> 用SQL直接做+1（`SET login_fail_count = login_fail_count + 1`），数据库会在内部给这行数据加一把"锁"，同一时刻只允许一个线程修改，另一个线程必须等第一个改完才能改，这样就不会少算了。

> **为什么COALESCE(login_fail_count, 0)？**
> 如果loginFailCount字段为NULL（新用户从未失败过），`NULL + 1` 在SQL中结果是NULL，不是1。COALESCE是SQL的一个函数，意思是"如果第一个值是NULL，就用第二个值代替"。所以 `COALESCE(login_fail_count, 0)` 就是"如果是NULL就当0处理"，确保第一次失败也能正确计数为1。

> **为什么第2步要再查一次？**
> 因为第1步的UPDATE只返回"影响了多少行"，不返回"更新后的值是几"。我们需要知道递增后到底是几，才能判断是否达到5次阈值。

**对应代码片段（逐行注释）**：

```java
// SysUserServiceImpl.java — incrementLoginFailCount() 方法
@Transactional(rollbackFor = Exception.class)
@Override
public int incrementLoginFailCount(Long id) {

    // ===== 第1步：SQL直接+1，不走"先查后改" =====
    // 生成SQL: UPDATE sys_user SET login_fail_count = COALESCE(login_fail_count, 0) + 1 WHERE id = ?
    // 关键点1：COALESCE把NULL转为0，+1在数据库层面完成
    // 关键点2：数据库在执行UPDATE时会自动给这行数据加锁，同一时刻只有一个线程能修改
    //         这就避免了"两个线程同时读到旧值，各自+1后写入，结果少算一次"的问题
    LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
    wrapper.eq(SysUser::getId, id)
            .setSql("login_fail_count = COALESCE(login_fail_count, 0) + 1");
    update(wrapper);  // 执行UPDATE，此时数据库中的值已经是+1后的结果

    // ===== 第2步：读取递增后的最新值 =====
    // 必须重新查询，因为UPDATE不会返回更新后的值
    SysUser afterUpdate = getById(id);
    int currentCount = afterUpdate.getLoginFailCount() != null
            ? afterUpdate.getLoginFailCount() : 0;  // 防御性编程：null当0处理

    // ===== 第3步：达到阈值且尚未锁定时，设置锁定截止时间 =====
    // 条件1: currentCount >= 5 → 达到阈值
    // 条件2: afterUpdate.getLockTime() == null → 尚未被锁定
    //        （如果已被锁定，说明之前已经设过lockTime，不需要重复设置）
    if (currentCount >= 5 && afterUpdate.getLockTime() == null) {
        LambdaUpdateWrapper<SysUser> lockWrapper = new LambdaUpdateWrapper<>();
        lockWrapper.eq(SysUser::getId, id)
                .set(SysUser::getLockTime, java.time.LocalDateTime.now().plusMinutes(30));
        // 生成SQL: UPDATE sys_user SET lock_time = '2026-05-23 15:30:00' WHERE id = ?
        update(lockWrapper);
    }

    return currentCount;  // 返回当前失败次数，供调用方写审计日志
}
```

---

## 6. 沙盒演练场（知行合一）

> **使用方法**：三道题按难度递增。每道题都给出了**具体改哪个文件、改哪一行、改完怎么验证**。做完后找我要答案和解析。

### 🟢 基础题：修改锁定阈值，观察行为变化

**任务**：将登录失败锁定阈值从5次改为3次，锁定时长从30分钟改为10分钟。

**具体操作步骤**：

1. 打开 [AuthServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AuthServiceImpl.java)
2. 找到第30行 `private static final int MAX_LOGIN_FAIL_COUNT = 5;`，把5改为3
3. 找到第32行 `private static final int LOCK_DURATION_MINUTES = 30;`，把30改为10
4. **⚠️ 还有一处硬编码！** 打开 [SysUserServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/SysUserServiceImpl.java)，找到第186行 `if (currentCount >= 5`，把5也改为3（或改为引用常量）
5. 重启应用

**验证方法**：
- 故意输错3次密码（用同一个用户名）
- 第4次尝试登录，应该看到"账号已被锁定，请于 xxxx 后重试"
- 等待10分钟后再次尝试，应该能正常登录（如果密码正确的话）

**思考题**：如果只改了AuthServiceImpl的常量，没改SysUserServiceImpl中的硬编码5，会发生什么？（提示：handleLoginFailure中的提示消息会显示"已锁定"，但incrementLoginFailCount中要5次才真正设lockTime）

---

### 🟡 进阶题：为登录接口添加IP限流

**任务**：当同一IP在1分钟内登录失败超过5次时，直接拒绝请求，不再查数据库。

**具体操作步骤**：

1. 打开 [AuthServiceImpl.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/service/impl/AuthServiceImpl.java)
2. 在类中添加一个字段，用来记录每个IP的失败次数：

```java
private final java.util.concurrent.ConcurrentHashMap<String, Integer> ipFailCount
        = new java.util.concurrent.ConcurrentHashMap<>();
```

> **什么是ConcurrentHashMap？** 它是Java提供的一个"线程安全的HashMap"——普通HashMap在多线程同时读写时可能出错，ConcurrentHashMap不会。你可以把它理解为一个"多人可以同时查阅和修改的通讯录"。

3. 在 `login()` 方法开头（第1步"查用户"之前），添加IP限流检查：

```java
Integer count = ipFailCount.getOrDefault(ip, 0);
if (count >= 5) {
    throw new BusinessException(ResultCode.INVALID_CREDENTIALS,
            "登录尝试过于频繁，请1分钟后再试");
}
```

4. 在 `handleLoginFailure()` 方法中，密码错误时递增IP计数：

```java
ipFailCount.merge(ip, 1, Integer::sum);  // ip对应的值+1，不存在则设为1
```

5. 在登录成功后（第5步重置计数之后），清除该IP的计数：

```java
ipFailCount.remove(ip);
```

**验证方法**：
- 用Postman快速对同一用户输错5次密码
- 第6次请求应该立即返回"登录尝试过于频繁"（注意响应速度——不会查数据库，应该非常快）
- 等待1分钟后再次尝试，应该能正常走登录流程

**思考题**：这种把计数存在内存里的方案有什么缺陷？（提示：如果部署了2台服务器、如果服务器重启、如果1分钟到了计数没清零）

---

### 🔴 挑战题：实现Token刷新机制

**任务**：新增 `POST /auth/refresh` 接口，当Token即将过期（剩余不足30分钟）时，允许用户用旧Token换取新Token，无需重新登录。

**具体操作步骤**：

**第1步：在JwtUtil中新增判断方法**

打开 [JwtUtil.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/common/util/JwtUtil.java)，在 `getExpiration()` 方法后面添加：

```java
public boolean isTokenExpiringSoon(String token) {
    Claims claims = parseToken(token);
    Date expiration = claims.getExpiration();
    // 如果Token在30分钟内过期，返回true
    return expiration.getTime() - System.currentTimeMillis() < 30 * 60 * 1000;
}
```

**第2步：在AuthService接口中新增方法声明**

打开 `AuthService.java` 接口，添加：

```java
LoginResponseDTO refreshToken(String token);
```

**第3步：在AuthServiceImpl中实现刷新逻辑**

```java
@Override
public LoginResponseDTO refreshToken(String oldToken) {
    // 1. 验证旧Token是否有效（parseToken失败会抛异常）
    Claims claims = jwtUtil.parseToken(oldToken);
    // 2. 检查是否即将过期（可选：只允许即将过期的Token刷新）
    if (!jwtUtil.isTokenExpiringSoon(oldToken)) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "Token尚未接近过期，无需刷新");
    }
    // 3. 从旧Token中提取用户信息
    Long userId = Long.parseLong(claims.getSubject());
    String username = claims.get("username", String.class);
    String role = claims.get("role", String.class);
    // 4. 用同样的信息签发新Token
    String newToken = jwtUtil.generateToken(userId, username, role);
    // 5. 构建响应（需要查一次数据库获取nickname）
    SysUser user = sysUserService.getUserOrThrow(userId);
    return buildLoginResponse(newToken, user);
}
```

**第4步：在AuthController中新增接口**

打开 [AuthController.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/controller/AuthController.java)，添加：

```java
@PostMapping("/refresh")
public Result<LoginResponseDTO> refresh(@RequestHeader("Authorization") String authHeader) {
    String token = authHeader.replace("Bearer ", "");
    return Result.success(authService.refreshToken(token));
}
```

**第5步：在SecurityConfig中放行刷新接口**

打开 [SecurityConfig.java](file:///d:/code/iot-platform/src/main/java/com/example/platform/config/SecurityConfig.java)，在 `requestMatchers("/auth/login").permitAll()` 后面添加：

```java
.requestMatchers("/auth/refresh").permitAll()
```

**验证方法**：
1. 正常登录获取Token
2. 将 `application.yaml` 中的 `jwt.expiration` 临时改为 `120`（2分钟），重启
3. 等待约1.5分钟后，用旧Token调用 `POST /auth/refresh`，应该能获取新Token
4. 用新Token访问业务接口（如 `GET /api/devices`），验证新Token有效
5. 用旧Token再访问一次业务接口，验证旧Token是否还能用（思考：应该还能用吗？）

**思考题**：
- 如果允许已过期的Token也能刷新，安全风险是什么？（提示：Token被偷后，小偷可以无限续期）
- 旧Token刷新后是否应该作废？如果要作废，需要什么机制？（提示：需要服务端维护一个"黑名单"，但这就违背了JWT"无状态"的设计初衷）

---

> **提示**：完成练习后，可以找我要答案和详细解析。每个练习都对应了SEC模块的一个核心知识点，动手修改代码是理解内部机制最快的方式。
