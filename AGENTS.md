# AGENTS.md — AI Agent 上下文文件

> 任何新 AI 会话第一步：读本文件 + `README.md` 建立全貌。

---

## 1. 项目身份

- **项目**：IoT-Platform — 物联网设备管理后端服务
- **技术栈**：Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + **MySQL 9.6.0** + Redis 7 + JDK 17
- **当前版本**：v4.0（Redis 多层缓存、JWT 安全防护、审计日志、RBAC 三级角色、仪表盘统计）
- **分层架构**：Controller → Service（接口） → ServiceImpl → Mapper → Entity

---

## 2. 版本约束（改代码时必须遵守，踩过坑）

| 组件 | 版本 | 注意事项 |
|------|------|---------|
| Spring Boot | **4.0.6** | 是 4.x 不是 3.x。Flyway 模块化、Security API 与 3.x 有差异 |
| MySQL Connector | **9.6.0**（mysql-connector-j） | 驱动类与 8.x 行为不同，连接参数有差异 |
| MyBatis-Plus | 3.5.16 | 分页插件、LambdaQueryWrapper、自动填充 |
| Druid | 1.2.28 | Spring Boot 4 专用 Starter |
| JWT | jjwt | 无状态认证 |
| Redis | 7.x + Lettuce 6.x | 异步非阻塞客户端 |

---

## 3. 代码约定

- **统一响应**：`Result<T>` + `ResultCode`（**不要硬编码 HTTP 状态码**，扩展 `ResultCode` 枚举）
- **异常**：抛 `BusinessException` 子类，由 `GlobalExceptionHandler` 统一兜底
- **分页**：`size ≤ 100, current ≥ 1`（用 `PaginationUtils.validate()` 校验）
- **审计**：关键写操作加 `@AuditLog` 注解
- **注入方式**：Lombok `@RequiredArgsConstructor` 构造器注入，**不用 `@Autowired` 字段注入**
- **缓存**：统一走 `CacheService` 接口，**不直接使用 `RedisTemplate`**
- **XSS 防御**：请求参数由 `XssFilter` 统一 HTML 转义
- **API 文档**：接口说明用 Swagger 注解（`@Tag`/`@Operation`/`@Schema`），启动后访问 `/swagger-ui.html`，**不要再在 src 下创建 README**

---

## 4. 禁忌（不要做）

- ❌ 不要在 src 各包下创建 `*README.md`（已清理，会重新冗余、漂移）
- ❌ 不要直接操作 `RedisTemplate`，走 `CacheService`
- ❌ 不要硬编码错误码，扩展 `ResultCode` 枚举
- ❌ 不要在实体类用 `@Autowired`
- ❌ 不要在 Service 接口描述与 ServiceImpl 实现之间制造矛盾（曾发生 DictMetric 缓存描述冲突）
- ❌ 不要把 DictMetric 描述成 "ConcurrentHashMap 内存缓存"——**已迁移到 Redis**（v4.0）

---

## 5. 测试与构建命令

| 命令 | 用途 |
|------|------|
| `mvn test` | 全量单元测试 |
| `mvn test -Dtest=DeviceServiceImplTest` | 单个测试类 |
| `mvn spring-boot:run` | 启动应用（需先起 MySQL + Redis） |
| `mvn clean compile` | 仅编译（快速验证语法） |

**启动前置条件**：MySQL 运行中（库 `iot_platform`）、Redis 运行中、初始数据中 `sys_user` 表存在 ID=1 的管理员账号（测试依赖）。

---

## 6. 文档导航（新会话按需读取）

| 任务类型 | 读什么 |
|---------|--------|
| 任意任务（必读） | `README.md` + 本文件 |
| 新增/改需求 | `开发流程学习文档/需求文档/` 下三件套（需求规格说明书 / 开发优先级计划 / 标准化方案） |
| 改业务代码 | `README.md` 结构树 → **直接读对应 `.java`**（代码最权威，勿读已删除的旧 README） |
| 改 API / 查接口 | 启动后访问 `/swagger-ui.html`（实时从注解生成） |
| 学习某模块原理 | `开发流程学习文档/学习笔记/` |
| 涉及架构决策 | `git log` + 相关代码注释 |

---

## 7. 当前状态

- **分支**：`feature/infra-observability`（基础设施可观测性）
- **路线图**：见 `开发优先级计划.md`，P0 已完成，进行中 P1
- **Git 工作流**：Feature Branch（每个特性独立分支，PR 合并到 main）

---

## 8. 关键业务模型速查

| 实体 | 表 | 要点 |
|------|----|------|
| Device | devices | 设备 CRUD，`status`：0-离线/1-在线/2-故障 |
| DeviceType | device_types | 设备类型，承载类型级告警规则（降级方案） |
| DeviceData | device_data | 上报数据，单条/批量，触发告警检查 |
| Alert | alerts | 告警记录，`alertLevel`：1-紧急 … 5-提示（数字越小越紧急） |
| DictMetric | dict_metric | 指标元数据，Redis 缓存，填充告警消息中文/单位 |
| SysUser | sys_user | RBAC 三级角色：ADMIN/OPERATOR/VIEWER |
| SysAuditLog | sys_audit_log | 操作审计，过期自动清理 |

**告警规则降级策略**：设备级配置优先，缺失时降级到类型级配置（`getEffectiveRuleConfig`）。

**告警升级判定**：`rule.level < existingAlert.alertLevel` 时升级（数字小 = 更紧急）。
