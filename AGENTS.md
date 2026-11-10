# AGENTS.md · dcim-iot-platform

> 本文件分两块：文档体系纪律（固定，不可删除或弱化）+ 项目信息（协作维护）。
> 项目进行状态一律看 `docs/00_总控.md`，本文件不承载状态。

---

## 一、文档体系纪律（本节不可删除或弱化）

1. **会话开始**：先读 `docs/00_总控.md` + 当前进行中阶段的 `_中控.md`，
   再读本文件项目信息块。未读完不得开始任何任务。
2. **任务合法性**：只做「总控当前指针」所指阶段 `_中控.md` 中记录的任务，
   及其上游包文档推导出的任务。任何新想法 →
   `90_全局/非开发计划内灵感留存处.md`，只记录，不实施，不排序。
3. **冻结保护**：`10_系统定义/系统定义.md` 冻结后不得直接修改；
   需要变更时停下来向用户说明，走决策日志 → 整份重写流程。
   已完成阶段包（✅）的任何后续修改，同样必须先向用户确认。
4. **状态回写**：完成一个任务 → 更新 `_中控.md` 任务状态 + 必要的产物文档；
   完成一个阶段 → 逐条对照判据写验收记录 → 更新总控阶段表和当前指针。
   会话结束前必须完成回写，未回写的会话视为未完成。
5. **决策留痕**：任何技术选型、架构决策、定义变更 →
   `90_全局/决策日志.md` 一行记录（日期/决策/理由/影响的文档）。
6. **状态唯一来源**：除总控与各 `_中控.md` 外，任何文档不得出现阶段状态标记
   （⬜ 未开始 / 🔄 进行中 / ✅ 已完成）。
7. **归档纪律**：旧文档只进 `archive/`，只读不改；新体系文档是唯一权威。
8. **信息来源纪律**：各阶段文档引用外部信息时，必须在对应文档中标识权威来源
   （官方文档/规范/标准/一手资料），不允许引用任意博客信息或不保留来源。
9. **术语纪律**：执行任务接触到术语时，先查 `90_全局/术语表.md`；
   术语表中没有的，停下来询问用户，明确其含义、范围与易歧义处，
   写入术语表后再继续。文档与对话中的术语一律以术语表为准；
   发现同物异名（含代码层命名与文档用语不一致）时先收编进术语表再干活。

## 二、依赖纪律

阶段依赖链（任务只从上游文档推导，跳级引用视为违规）：

```
10 系统定义（锚点·冻结）
   ↓ 细化
20 需求分析 ←────────────────────┐
   ↓ 结构化                   60 评估验收（验收需求实现程度）
30 系统设计                       ↑
   ↓ 按设计实施                测试报告
40 开发实施 ──产出──→ 50 系统测试 ┘
70 部署运行 ← 依赖 40/50/60 产出
```

**依赖向上填补规则**：任何阶段文档的修改（如 20 包新增一条需求），必须能追溯
到上游文档的支撑；若上游没有对应定义，必须先补足上游定义，再在本层完整描述，
不允许只在当前层添加（杜绝悬空需求）。

---

## 三、项目信息

### 3.1 项目定位（一句话）

个人开发的数据中心动环监控平台（DCIM）：机房、设备、测点采样、查询与告警管理；网关按统一契约上报。同仓 simulator 是独立构建、独立进程，需与前端分别启动；Compose 只覆盖后端与依赖服务。

### 3.2 技术栈与版本约束（改代码时必须遵守，踩过坑）

| 组件 | 版本 | 注意事项 |
|------|------|---------|
| Spring Boot | **4.0.6** | 是 4.x 不是 3.x。Flyway 模块化、Security API 与 3.x 有差异 |
| MySQL Connector | **9.6.0**（mysql-connector-j） | 驱动类与 8.x 行为不同，连接参数有差异 |
| MyBatis-Plus | 3.5.16 | 分页插件、LambdaQueryWrapper、自动填充 |
| Druid | 1.2.28 | Spring Boot 4 专用 Starter |
| JWT | jjwt | 无状态认证 |
| Redis | 7.x + Lettuce 6.x | 异步非阻塞客户端 |

- 后端分层：Controller → Service（接口） → ServiceImpl → Mapper → Entity
- 前端：Vue 3 + Vite + Element Plus + Pinia + ECharts，日/夜双主题
- 仿真服务：`simulator/` 独立子项目（Spring Boot + JPA + MySQL 独立库，手写最小 Modbus TCP 子集）

### 3.3 代码规范与注释习惯

- **统一响应**：`Result<T>` + `ResultCode`（不要硬编码 HTTP 状态码，扩展 `ResultCode` 枚举）
- **异常**：抛 `BusinessException` 子类，由 `GlobalExceptionHandler` 统一兜底
- **分页**：`size ≤ 100, current ≥ 1`（用 `PaginationUtils.validate()` 校验）
- **审计**：关键写操作加 `@AuditLog` 注解
- **注入方式**：Lombok `@RequiredArgsConstructor` 构造器注入，不用 `@Autowired` 字段注入
- **缓存**：统一走 `CacheService` 接口，不直接使用 `RedisTemplate`
- **XSS 防御**：请求参数由 `XssFilter` 统一 HTML 转义
- **API 文档**：接口说明用 Swagger 注解（`@Tag`/`@Operation`/`@Schema`），启动后访问 `/api/swagger-ui.html`（context-path 为 `/api`）；不要在 src 下创建 README

#### 分层注释规范速查

| 分层 | 类级注释 | 方法级注释 | 字段注释 |
|------|---------|-----------|---------|
| **Controller** | `/** Javadoc */` + `@Tag` | `@Operation`（无需 Javadoc） | N/A |
| **Service 接口** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | N/A |
| **ServiceImpl** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | `//` 行内注释（常量说明） |
| **Entity** | `/** Javadoc */` + `@Schema` | N/A | `@Schema` 注解（不使用 `//`） |
| **DTO/VO** | `/** Javadoc */` + `@Schema` | N/A（静态工厂方法不加注释） | `@Schema` 注解（不使用 `//`） |
| **Mapper** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | N/A |
| **Config** | `/* 类功能:... */` | `/* 方法功能:... 参数:... 返回数据:... */` | `//` 行内注释 |
| **Exception** | `/* 类功能:... */` | 构造器不加注释 | N/A |

详细规则：Service 接口/ServiceImpl/Mapper 用老式 `/* */` 块注释，方法注释多参数用 `-` 分隔（`参数:name-描述`），无参/无返回值写 `参数:无`/`返回数据:无`，附加说明写 `说明:...`；Entity/DTO/VO 只写 `@Schema` 不在字段写 `//`；不允许类或方法完全无注释；不使用 `@author`/`@param`/`@return` 等 Javadoc 标准标签。

#### 禁忌（不要做）

- ❌ 不要在 src 各包下创建 `*README.md`
- ❌ 不要直接操作 `RedisTemplate`，走 `CacheService`
- ❌ 不要硬编码错误码，扩展 `ResultCode` 枚举
- ❌ 不要在实体类用 `@Autowired`
- ❌ 不要在 Service 接口描述与 ServiceImpl 实现之间制造矛盾（曾发生缓存描述冲突）
- ❌ 不要引用已删除的 v1.0 老链（DeviceData/Alert/DictMetric/NotificationLog/`getEffectiveRuleConfig`）——现行链是 ingest 门 + MetricSample + AlarmEvent 状态机 + V15 告警模版
- ❌ 不要用 `updateById` 显式置空字段——MyBatis-Plus 会忽略 null（解绑告警模版等场景必须用 `LambdaUpdateWrapper.set`，踩过坑）
- ❌ 不要让任何 Java 类完全无注释——至少要有类级功能描述
- ❌ **不要在企微智能机器人渠道启用状态下启动开发/测试平台**——该渠道（`alarm_webhook_channel` 中 `channel_type=WECOM_AIBOT`）已接入用户真实企微群，测试告警会直接骚扰真实用户。起平台前先置 `enabled=0`（渠道管理页开关或直接改库），测完按需恢复；凭证（botId/secret）用户可能在企微后台重置，订阅失败先想到凭证已换

### 3.4 目录结构约定

```
├── src/main/java/com/example/platform/
│   ├── controller/            # REST 控制器
│   ├── service/ + impl/       # 服务层（告警规则引擎、采样事务、离线检测）
│   ├── entity/ mapper/ dto/   # 数据模型
│   ├── ingest/                # 数据接收门：HTTP/MQTT 适配器 + 传输无关核心 + 机房配套凭证
│   ├── security/              # JWT 过滤链、三角色模型、WebSocket 鉴权与推送
│   ├── common/                # Result 统一响应、异常体系、审计切面、限流/XSS 过滤器
│   ├── config/                # Security/Redis/MyBatis-Plus/Async/生产安全门控
│   └── task/                  # 设备离线检测、网关离线检测、数据 TTL、幂等键清理
├── src/main/resources/db/
│   ├── migration/             # Flyway V1–V25（以迁移脚本为表结构事实来源）
│   └── seed/                  # V9 开发种子（仅 dev profile；设备零预置）
├── frontend/                  # Vue3 管理后台
├── docker/mosquitto/          # MQTT broker 配置
├── simulator/                 # dcim-simulator 仿真服务（独立进程独立构建，:8090）
├── docs/                      # 阶段文档包体系（00_总控 + 10~70 包 + 90_全局 + archive + 毕业设计）
├── test/                      # 毕设交付：测试文档与用例
├── deploy/                    # 毕设交付：部署脚本与说明
└── thesis/                    # 毕设交付：毕业论文与答辩材料
```

### 3.5 启动方式

| 命令 | 用途 |
|------|------|
| `mvn test` | 平台自动化测试（2026-11-10 verify：394 项，392 通过、2 邮箱条件跳过；测试库与 Redis 前提见验证记录） |
| `mvn test -Dtest=DeviceServiceImplTest` | 单个测试类 |
| `mvn spring-boot:run` | 启动应用（需先起 MySQL + Redis） |
| `mvn clean compile` | 仅编译（快速验证语法） |
| `cd simulator && mvn test` | 仿真服务测试（独立子项目；实际运行使用 MySQL 独立库） |
| `cd frontend && npm run test` | 前端 Vitest 单元测试 |
| `docker compose up -d --build` | 后端与依赖编排（app + MySQL + Redis + mosquitto；不含 frontend/simulator） |

**启动前置条件**：MySQL 运行中（库 `iot_device_db`）、Redis 运行中、`sys_user` 表存在 ID=1 管理员账号（V9 seed，测试依赖）。生产环境由 `ProductionSafetyGuard` 启动门控强制：必须替换默认 JWT 密钥、配置明确 CORS 来源、配置数据库与 Redis 密码。

**本机端口约定**：平台 8081 / 前端 5175 / 仿真器 8090；8080、5173、5174 属于用户其他项目，**绝不占用**。

### 3.6 关键业务模型速查

> 以实体类与 Flyway 迁移（V1–V25）为准。机房即接入主体，设备零预置，首包自动注册建档。

| 实体 | 表 | 要点 |
|------|----|------|
| CloudRoom | sys_cloud_room | 机房 = 接入主体：创建即初始化 AccessKey/SecretKey/TB token/MQTT 子凭证，网关凭签名上报；驾驶舱健康度/网关状态由统计聚合计算 |
| Device | devices | `deviceCode` 全局寻址；`alarmTemplateId` 设备级模版绑定（可空，为空跟随类型） |
| DeviceType | device_types | 类型目录 + `alarmTemplateId` 基础模版绑定；类型级指标模板（device_type_point_template） |
| PointDefinition / DevicePointBinding | point_definition / device_point_binding | 平台级指标字典与设备指标绑定 |
| MetricSample / MetricLatest / MetricRetentionPolicy | metric_sample / metric_latest / metric_retention_policy | 采样时序（V21 起按月分区，主键 `(id, sample_time)`）+ 最新值 + TTL 保留策略 |
| MetricSampleHourly | metric_sample_hourly | V21 小时聚合表：>48h 趋势查询走此表（cnt 加权均值） |
| AlarmTemplate / AlarmTemplateItem | alarm_template / alarm_template_item | V15 告警模版：规则融合模版，类型绑基础版、设备可改绑自定义版；V19 起规则项含三机制字段（streak / windowDelta / resend） |
| AlarmWebhookChannel | alarm_webhook_channel | V20 IM webhook 渠道：WECOM_AIBOT/DINGTALK/FEISHU/NTFY/TELEGRAM/GENERIC，`credentialJson` 结构随类型 |
| IngestPayloadMapping / IngestRawMessage | ingest_payload_mapping / ingest_raw_message | V25 自定义报文映射模板（特征匹配路由 + 字段路径转译，挂机房）与未匹配原始报文留存（ADR-009） |
| AlarmEvent / AlarmStateTransition / AlarmHandlingRecord | alarm_event / alarm_state_transition / alarm_handling_record | 告警状态机 TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED；`alarmLevel` 1-5（数字越小越紧急），逻辑删除；V22 起机房可配 ALARM_UPGRADED 下行动作 webhook |
| InAppNotification / AlarmNotificationLog | in_app_notification / alarm_notification_log | 站内通知（头部铃铛模态框）与通知发送日志/去重 |
| SysUser | sys_user | 单租户三角色：ADMIN/OPERATOR/VIEWER（role 写入 JWT） |
| SysAuditLog | sys_audit_log | 结构化操作审计 |

**告警模版解析链**：设备 `alarmTemplateId` 优先，为空降级类型 `alarmTemplateId`；解绑必须用 `LambdaUpdateWrapper.set` 显式置空。

**告警评估与升级**：`AlarmEvaluateServiceImpl` 对上报采样做模版规则阈值评估，命中最严重级别触发；`rule.level < 现有告警级别` 时升级（数字小 = 更紧急）。

**数据入口（四条，汇入同一条传输无关核心）**：
1. 密钥签名通道 HTTP `POST /api/ingest/samples`（`Authorization: Gateway {AK}:{sha256(SK+body)}`；当前实现为 `SHA-256(SecretKey + 原始正文)`，不是标准 HMAC）；
2. 密钥签名通道 MQTT `dcim/{accessKey}/samples`（报文 `{signature,batch}`，当前签名算法同上；含遗嘱/LWT 网关离线即时告警）；
3. TB 兼容开放通道 `POST /api/v1/{token}/telemetry`（token 鉴权无签名，ADR-008）；
4. MQTT 子凭证开放通道 `dcim-open/{mqttUsername}/samples`（连接层鉴权，broker 侧 dynamic-security）。

后两条开放通道带自定义报文映射回退（ADR-009）：标准解析失败 → 机房映射模板特征匹配 + 字段路径转译 → 同一条核心；未命中留存原始报文（`ingest_raw_message`）。模板管理在"零散设备接入"页（ADMIN）。

处理链：机房归属校验 → `deviceCode+pointCode+seq` 幂等去重（开放通道无 seq 降级 `+sampleTime`）→ 采样落库 → 告警评估与通知。旧 v1.0 链（DeviceData/Alert/DictMetric/NotificationLog）已删除，勿再引用。

### 3.7 文档导航（按任务类型）

| 任务类型 | 读什么 |
|---------|--------|
| 任意任务（必读） | `docs/00_总控.md` + 当前阶段 `_中控.md` + 本文件 |
| 新增/改需求 / 补写阶段文档 | 本包 `_中控.md` → 上游包文档（依赖链见第二节） |
| 改业务代码 | 本文件 §3.4/§3.6 → 对应 `.java` 源码（代码最权威） |
| 查接口 | 启动后 `/api/swagger-ui.html`（实时从注解生成） |
| 架构决策考据 | `docs/archive/adr/`（ADR-001~009）+ `90_全局/决策日志.md` |
| 阶段实施蓝本（历史） | `docs/archive/计划书/`（索引见 40/50 包 `_中控.md`） |
| 毕设文档/论文/答辩 | `docs/毕业设计/00-工作流规划与差距分析.md`（目标系统视角，与工程文档分开维护） |

**开发控制文档特别约定**：仓库根目录 `开发控制文档/` 是外部第四阶段公共控制包的本地分发包（仅任务分发与报告暂存），已本地排除提交，**禁止加入 Git、禁止移入 docs/**。若本会话看不到外部主控文档工作区，读 `开发控制文档/00-本地分发包说明.md`。

### 3.8 待修复缺陷队列（Bug Queue）

> 系统定义冻结后（2026-09-04）产出的深度自查结果。**全部缺陷按体系流程挂起：
> 系统定义 ✅ → 20 需求分析 → 30 系统设计 → 40 开发实施（修复阶段）**。
> 开发阶段的新 AI 会话读取本节 → 进入修复流程。

| 优先级 | 数量 | 报告 | 处置 |
|--------|------|------|------|
| **P0 紧急** | 1 | `docs/60_评估验收/本项目自查报告.md` W-001 | MQTT broker 重连后静默假死（不重订阅）——开发阶段**第一优先修复** |
| **P1 重要** | 16 | 同上 W-002~W-031 | 调度单线程、@Async 拒绝丢评估、dedup 无界 DELETE、sha256(SK+body) 签名缺少防重放、mosquitto 匿名、Token 黑名单 Redis 降级失效、告警 trigger 竞态、GATEWAY_OFFLINE 单槽、每采样全量评估零缓存等 |
| **P2 可延后** | 20 | 同上 W-007~W-037 | 批失败死信、metric_latest 竞态、refresh token、TraceId、系统自监控、Flyway 回滚等 |

**开发阶段修复流程**：
1. 读 `docs/30_系统设计/` 下的设计文档（含修复涉及的架构决策）
2. 读 `docs/60_评估验收/本项目自查报告.md` 对应 W-xxx 条目（文件路径/代码行号/修复建议）
3. 在 `docs/40_开发实施/_中控.md` 建 bug 修复任务（挂 Cx 编号 + 设计章节 + 完成判据）
4. 按任务实施，回写 `_中控.md` 任务状态
5. P0 修复完成 → 回写差距清单 G-015 状态 → 补测试用例

### 3.9 仓库展示与维护入口

- 仓库维护约定：`docs/90_全局/开发与提交约定.md`；后续使用短期任务分支，沿用真实提交历史，不重建历史或自动删除尚未核实 PR 的分支。
- 本轮检查与限制：`docs/50_系统测试/2026-11-10-仓库收口验证.md`；本地步骤：`docs/70_部署运行/本地开发与仿真联调.md`。
- 源码默认后端端口 8080；本机开发继续遵守 §3.5 的端口避让约定。本轮隔离端口为 8089/5189/8099，未占用上述保留端口。
- README 的实现范围、自动化检查、HTTP 合成联调与生产使用分开陈述；不把 `sha256(SK+body)` 称为标准 HMAC，不把配置映射泛化为任意设备免改代码。
- 许可证维持原样；仓库公开访问与远程分支清理结果以总控维护记录为准。
