# dcim-iot-platform 数据中心动环监控平台

> **一句话定位**：自部署的数据中心动环监控平台——`docker compose up` 之后，你就拥有一个活着的数据中心：机房、设备、流动的数据、会响的告警。你的采集网关往一个干净的北向契约里推数，内置的仿真网关让系统开箱即活。

> **当前基线**：`refactor/depivot` 分支（单租户收束）。多组织 SaaS 版本封存于 tag `v5.4-saas-final`，收束决策见 `docs/adr/`。

---

## 一、五分钟看到活的数据中心

```bash
# 1. 准备：MySQL 9.x + Redis 7 运行中，复制 .env.example 为 .env 并填好数据库密码
cp .env.example .env

# 2. 一键启动（app + MySQL + Redis）
docker compose up -d --build

# 3. 打开前端，登录种子账号
#    http://localhost:5173
#    admin / Admin123（另有 operator / viewer 同密码）
```

你会看到：两个演示机房、四台设备（其中一台离线，用于演示离线告警）、预置的温湿度/电压点位与告警规则。向设备上报一条超阈值的数据，告警状态机（TRIGGERED → ACKED → HANDLING → RECOVERED → CLOSED）、站内通知、邮件通道都会依次活动起来。

本地开发：

```bash
./mvnw spring-boot:run        # 后端 :8080（dev profile 自动执行 Flyway V1–V10 + 演示种子）
cd frontend && npm run dev    # 前端 :5173，/api 代理到后端
```

---

## 二、这是什么

面向数据中心动力环境监控（DCIM）的自部署平台：

| 能力 | 说明 |
|------|------|
| 机房与设备 | 机房为顶层资源；设备档案、五类产品类型（UPS/PDU/精密空调/温湿度/漏水）、Excel 导入导出 |
| 点位与指标 | 点位定义、类型级点位模板、设备点位绑定；指标采样（时序）+ 最新值 + 保留策略与 TTL 清理 |
| 告警状态机 | 模板级/设备级规则引擎（替换式降级）、五级告警、TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED 全程留痕 |
| 通知 | 站内通知未读管理、WebSocket（STOMP）用户隔离摘要推送、邮件告警、通知日志与去重 |
| 仿真推演 | 模拟场景/步骤编排、异步运行与取消、生成数据可追溯（阶段三将重建为环境引擎+虚拟设备） |
| 认证与审计 | JWT 无状态认证、三角色 RBAC、账号锁定防爆破、IP 限流、邮箱验证码注册、结构化审计日志 |
| 可观测 | TraceId 全链路、Druid 慢 SQL 监控、Actuator 健康检查、生产安全门控 |

### 技术栈

Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + MySQL 9.6 + Redis 7 + JDK 17；前端 Vue 3 + Vite + Element Plus + Pinia + ECharts，日间/夜间双主题。

---

## 三、权限模型（单租户三角色）

| 角色 | 能做什么 |
|------|---------|
| **ADMIN** | 全部：用户管理、设备类型、机房/设备/点位/规则/场景的增删改、审计清理、Druid 监控 |
| **OPERATOR** | 业务写入：设备、采样上报、告警处理、仿真运行；不碰用户与设备类型目录 |
| **VIEWER** | 只读：设备/数据/告警/指标/日志 |

角色写入 JWT（`role` 声明），历史多组织 Token 的 `platformRole` 声明自动归一化为 ADMIN。用户来源两条：ADMIN 在用户管理页创建；或开放注册（`AUTH_REGISTRATION_ENABLED=true` 时邮箱验证码自助注册，默认 OPERATOR 不可、注册即 VIEWER）。开发环境初始用户由 V9 seed 提供。

---

## 四、数据上报（网关北向契约，阶段二路线）

现场设备（RS485/Modbus/SNMP/BACnet）接入采集网关，网关做协议翻译后向北推送统一 JSON——**平台不实现任何南向协议**（决策记录：`docs/adr/ADR-002`）。北向契约要点：网关凭证鉴权、批量 JSON（deviceId/pointCode/值/设备时间戳/seq）、幂等去重、断网补传。当前版本使用用户态 API `POST /metric-samples` 上报（阶段二将由 `/ingest/samples` 网关通道取代）；内置仿真引擎将作为"官方诚实网关"走同一条契约。

---

## 五、目录结构

```
├── src/main/java/com/example/platform/
│   ├── controller/            # REST 控制器
│   ├── service/ + impl/       # 服务层（含 AlertRuleEngine 告警规则引擎）
│   ├── entity/ mapper/ dto/   # 数据模型
│   ├── security/              # JWT 过滤链、三角色模型、WebSocket 鉴权
│   ├── common/                # Result 统一响应、异常体系、审计切面、限流/XSS 过滤器
│   ├── config/                # Security/Redis/MyBatis-Plus/Async/生产安全门控
│   └── task/                  # 离线检测、数据 TTL 清理
├── src/main/resources/db/
│   ├── migration/             # Flyway V1–V10（V10 为用户域收束迁移）
│   └── seed/                  # V9 开发种子（仅 dev profile 加载）
├── frontend/                  # Vue3 管理后台（12 个业务页面）
└── docs/adr/                  # 架构决策记录
```

启动后访问 `/swagger-ui.html` 查看实时 API 文档；`/druid/index.html` 查看 SQL 监控（dev）。

---

## 六、构建与测试

| 命令 | 用途 |
|------|------|
| `./mvnw test` | 全量测试（225 个用例） |
| `./mvnw spring-boot:run` | 启动后端（需 MySQL + Redis） |
| `cd frontend && npm run build` | 前端生产构建 |
| `docker compose up -d --build` | 一键容器化部署 |

环境变量见 `.env.example`（数据库/Redis/JWT/邮件/注册开关）。生产环境由 `ProductionSafetyGuard` 启动门控强制：必须替换默认 JWT 密钥、配置明确 CORS 来源、关闭模拟写入。

---

## 七、路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| 一 | 用户域收束：多组织 SaaS → 单租户三角色（本版本） | ✅ 完成 |
| 二 | 数据接收门：传输无关核心 + HTTP/MQTT 双协议适配器，网关凭证鉴权、幂等、心跳 | 📋 规划中 |
| 三 | 独立机房仿真服务（新仓库）：设备模块（Modbus TCP slave）+ 网关模块（轮询/策略/北向上报），完整还原设备→网关→云平台两级链路 | 📋 规划中 |

详细实施计划见 [docs/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md](docs/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md)。

架构决策与放弃项的完整记录见 `docs/adr/`：

- [ADR-001 放弃多组织 SaaS，收束为单租户自部署](docs/adr/ADR-001-放弃多组织SaaS收束为单租户自部署.md)
- [ADR-002 南向协议出局，只做网关北向契约](docs/adr/ADR-002-南向协议出局只做网关北向契约.md)
- [ADR-003 仿真引擎重建：从步骤编排到设备仿真器](docs/adr/ADR-003-仿真引擎重建从步骤编排到设备仿真器.md)
- [ADR-004 用户来源与 /setup 向导删除](docs/adr/ADR-004-用户来源与setup向导删除.md)

---

*基于 refactor/depivot 分支编写，最后更新于 2026-08-27。*
