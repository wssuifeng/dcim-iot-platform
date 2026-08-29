# dcim-iot-platform 数据中心动环监控平台

> **一句话定位**：自部署的数据中心动环监控平台——`docker compose up` 之后，你就拥有一个活着的数据中心：机房、设备、流动的数据、会响的告警。你的采集网关只凭三样配置（平台地址 + AccessKey + SecretKey）往北向契约里推数，配套的仿真服务让整个系统开箱即活。

> **当前基线**：`refactor/depivot` 分支（单租户收束 + 机房凭证合一）。多组织 SaaS 版本封存于 tag `v5.4-saas-final`，收束决策见 `docs/adr/`。

---

## 一、五分钟看到活的数据中心

```bash
# 1. 准备：MySQL 9.x + Redis 7 运行中，复制 .env.example 为 .env 并填好数据库密码
cp .env.example .env

# 2. 一键启动（app + MySQL + Redis + mosquitto）
docker compose up -d --build

# 3. 打开前端，登录种子账号
#    http://localhost:5173
#    admin / Admin123（另有 operator / viewer 同密码）
```

登录后的接入全流程（机房与接入凭证合一，设备零预置）：

1. **机房管理**页新增机房——弹窗一次性给出该机房的 **AccessKey / SecretKey**（创建机房即初始化接入凭证，无独立凭证管理页）；
2. 打开仿真服务（[dcim-simulator](../dcim-simulator)，默认 :8090）管理页，网关配置**只填三样**：平台地址 + AccessKey + SecretKey，保存并启动，点"平台连通自检"；
3. 点"拉取告警阈值"（顺带同步机房目录），创建虚拟设备（Modbus TCP slave）并初始化环境档案；
4. 网关轮询寄存器、按策略北向上报——平台**首包自动注册**设备（按上报点位智能推断类型，如 temperature → 温湿度），数据落库、超阈值告警触发：右上角滑出告警弹窗，状态机 TRIGGERED → ACKED → HANDLING → RECOVERED → CLOSED 全程流转，站内通知（头部铃铛模态框）同步到达。

本地开发：

```bash
./mvnw spring-boot:run        # 后端 :8080（dev profile 自动执行 Flyway V1–V15 + 种子）
cd frontend && npm run dev    # 前端 :5173，/api 代理到后端
```

> 多项目并行开发端口冲突时：后端 `SERVER_PORT=8081 ./mvnw spring-boot:run`，前端 `DEV_PORT=5174 API_TARGET=http://localhost:8081 npm run dev`。

---

## 二、这是什么

面向数据中心动力环境监控（DCIM）的自部署平台：

| 能力 | 说明 |
|------|------|
| 机房工作台 | 机房为一等导航上下文（主从式下钻）：仪表盘/机房管理卡片点击进入 `/rooms/:id` 工作台，概览（机房级 KPI/趋势/级别分布/设备 TOP）+ 设备与数据（卡片表+数据抽屉）+ 告警（表格全操作）三 tab；机房卡片默认按未处理告警降序排序 |
| 设备接入 | 首包自动注册（未知 deviceCode 自动建档 + 模板点位绑定，类型按点位编码智能推断）、网关清单上报、编码防重查询 |
| 数据接收门 | HTTP + MQTT 双协议适配器汇入传输无关核心：机房归属校验、`deviceCode+pointCode+seq` 幂等去重（QoS1 重发友好）、断网补传（迟到但有序）、批量逐条独立事务 |
| 网关观测 | 心跳保活（恒走 HTTP，与数据传输解耦）、last_seen 持续刷新、网关离线告警（每 5 分钟扫描 + MQTT 遗嘱即时触发，恢复自动闭环，从未上线也可发现） |
| 点位与指标 | 点位定义、类型级点位模板、设备点位绑定；指标采样（时序）+ 最新值 + 保留策略与 TTL 清理 |
| 告警模版 | 可命名的告警规则融合模版（V15）：一套安装场景的规则聚合，类型绑定基础版作默认，具体设备可改绑自定义模版（规则整体切换），自动注册设备零配置跟随类型；复制模版即自定义起点 |
| 跨机房检索 | 2-4 机房并排对比：设备命中/在线率/活动告警/级别分布对比卡（在线率异常高亮）+ 设备/告警明细双 tab，支持类型与名称过滤 |
| 告警状态机 | 模版规则引擎、五级告警色带、最严重命中、级别升级、全程留痕；阈值可经网关签名导出（供网关本地告警判定，松耦合） |
| 通知 | 头部铃铛通知模态框（未读管理/单条与全部已读）、WebSocket（STOMP）用户隔离推送、右上角卡片式告警弹窗（级别色带顶边、手动关闭队列+堆叠上限、Web Audio 提醒音效可静音）、邮件告警、通知日志与去重 |
| 认证与审计 | JWT 无状态认证、三角色 RBAC、账号锁定防爆破、IP 限流、邮箱验证码注册、结构化审计日志 |
| 可观测 | TraceId 全链路、Druid 慢 SQL 监控、Actuator 健康检查、生产安全门控 |

**平台内不做仿真**（V12 已退役）：造数职责由独立仓库 **dcim-simulator** 承担——虚拟设备（Modbus TCP slave，环境引擎四函数 + 确定性噪声 + 故障注入）+ 智能网关（FC03 轮询 / 工程量解码 / 周期-死区-越限上报策略 / MQTT-HTTP 双传输北向客户端），设备与网关间真实 Modbus TCP 通信，与真实机房两级链路同构。网关对云平台零管理依赖：全部交互走 Gateway 签名（上报、心跳自检、阈值拉取、清单同步）。

### 技术栈

平台：Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + MySQL 9.6 + Redis 7 + JDK 17；前端 Vue 3 + Vite + Element Plus + Pinia + ECharts，日间/夜间双主题。
仿真服务：Spring Boot + JPA + H2（本地自包含），手写最小 Modbus TCP 子集（零协议依赖），Vue3 + Element Plus 单文件管理页。

---

## 三、权限模型（单租户三角色）

| 角色 | 能做什么 |
|------|---------|
| **ADMIN** | 全部：用户管理、设备类型、机房（含接入凭证生命周期）/设备/点位/规则、审计清理、Druid 监控 |
| **OPERATOR** | 业务写入：设备、采样上报、告警处理；不碰用户与设备类型目录 |
| **VIEWER** | 只读：设备/数据/告警/指标/日志 |

角色写入 JWT（`role` 声明），历史多组织 Token 的 `platformRole` 声明自动归一化为 ADMIN。用户来源两条：ADMIN 在用户管理页创建；或开放注册（`AUTH_REGISTRATION_ENABLED=true` 时邮箱验证码自助注册，注册即 VIEWER）。开发环境初始用户由 V9 seed 提供。

---

## 四、数据上报（网关北向契约）

现场设备（RS485/Modbus/SNMP/BACnet）接入采集网关，网关做协议翻译后向北推送统一 JSON——**平台不实现任何南向协议**（决策记录：`docs/adr/ADR-002`）。

**机房即接入主体**：每个机房自带一套接入凭证（创建机房时初始化，停用机房联动停凭证、删除机房级联删凭证）。网关凭 AccessKey/SecretKey 经两条等价通道上报：

- **HTTP**：`POST /api/ingest/samples`，请求头 `Authorization: Gateway {AccessKey}:{sha256(SecretKey + rawBody)}`
- **MQTT**：发布到 `dcim/{accessKey}/samples`（QoS 1），报文 `{"signature","batch"}`；断连由 broker 遗嘱 `dcim/{accessKey}/lwt` 上报（broker 由 `docker compose up -d mosquitto` 提供，平台侧 `INGEST_MQTT_ENABLED=true` 启用；仿真器网关 MQTT 不可达时自动回退 HTTP，数据不断流）

两种通道汇入同一条传输无关处理链：机房归属校验 → 未知设备自动注册（可选，凭证维度开关）→ `deviceCode+pointCode+seq` 幂等去重 → 采样落库 → 告警评估与通知。批量 ≤200 条逐条独立事务，部分成功合法；`sampleTime` 早于平台时间为断网补传（合法），晚于 +5 分钟拒绝；越权条目失败不落库。

辅助契约（同样 Gateway 签名）：`POST /api/ingest/heartbeat` 心跳保活；`POST /api/ingest/inventory` 设备清单上报（显式导入或 unknown 比对）；`GET /api/ingest/export/thresholds` 阈值与机房目录导出（网关本地告警判定的配置源）。

用户态 API `POST /metric-samples` 保留用于手工调试；dcim-simulator 作为"官方诚实网关"走同一条 ingest 契约。

---

## 五、目录结构

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
│   ├── migration/             # Flyway V1–V15（V10 用户域收束，V11 接收门，V12 仿真退役，
│   │                          #   V13 设备接入打通，V14 机房凭证合一，V15 告警模版）
│   └── seed/                  # V9 开发种子（仅 dev profile；设备零预置，留给接入全流程）
├── frontend/                  # Vue3 管理后台（机房工作台 + 全局告警流 + 跨机房检索 + 驾驶舱仪表盘 + 日/夜双主题）
├── docker/mosquitto/          # MQTT broker 配置（数据接收门北向通道）
└── docs/                      # 架构决策记录 + 实施计划书
```

启动后访问 `/api/swagger-ui.html` 查看实时 API 文档（应用 context-path 为 `/api`）；`/api/druid/index.html` 查看 SQL 监控（dev）。

---

## 六、构建与测试

| 命令 | 用途 |
|------|------|
| `./mvnw test` | 全量测试（280+ 用例） |
| `./mvnw spring-boot:run` | 启动后端（需 MySQL + Redis） |
| `cd frontend && npm run build` | 前端生产构建 |
| `docker compose up -d --build` | 一键容器化部署 |

环境变量见 `.env.example`（数据库/Redis/JWT/邮件/注册开关/MQTT 接收门）。生产环境由 `ProductionSafetyGuard` 启动门控强制：必须替换默认 JWT 密钥、配置明确 CORS 来源、配置数据库与 Redis 密码。

---

## 七、路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| 一 | 用户域收束：多组织 SaaS → 单租户三角色 | ✅ 完成 |
| 二 | 数据接收门：传输无关核心 + HTTP/MQTT 双协议适配器，网关凭证鉴权、幂等、心跳 | ✅ 完成 |
| 三 | 独立机房仿真服务（dcim-simulator）：虚拟设备（Modbus TCP slave）+ 智能网关（轮询/策略/北向上报），还原设备→网关→云平台两级链路 | ✅ 完成（E2E：45℃ 平滑过渡 4 秒触发告警，kill 重启自动恢复） |
| 四 | 设备接入打通：机房凭证合一、首包自动注册 + 类型智能匹配、清单上报、网关离线告警、通知模态框与告警弹窗 | ✅ 完成 |
| 五 | 告警点位模版重构（可命名/可复用/可改绑的规则融合模版，V15）+ 两服务前端 UI 重设计（驾驶舱仪表盘、五级告警色带、仿真器统一视觉语言与暗色） | ✅ 完成 |
| 六 | 机房工作台架构：主从式下钻（三全局页收进 `/rooms/:id` 工作台）、通知队列化+告警音效、机房管理卡片化、跨机房检索对比 | ✅ 完成（本版本） |

**文档地图**（详见 [docs/文档导览.md](docs/文档导览.md)）：现行事实来源是本 README、[阶段二与三实施计划](docs/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md)、[阶段五计划书](docs/点位模版重构与前端UI重设计计划.md) 与 ADR；历史设计文档已标注过时范围，与代码冲突时以代码为准。

架构决策与放弃项的完整记录见 `docs/adr/`：

- [ADR-001 放弃多组织 SaaS，收束为单租户自部署](docs/adr/ADR-001-放弃多组织SaaS收束为单租户自部署.md)
- [ADR-002 南向协议出局，只做网关北向契约](docs/adr/ADR-002-南向协议出局只做网关北向契约.md)
- [ADR-003 仿真引擎重建：从步骤编排到设备仿真器](docs/adr/ADR-003-仿真引擎重建从步骤编排到设备仿真器.md)
- [ADR-004 用户来源与 /setup 向导删除](docs/adr/ADR-004-用户来源与setup向导删除.md)
- [ADR-005 机房与接入凭证合一，网关零管理依赖](docs/adr/ADR-005-机房与接入凭证合一网关零管理依赖.md)
- [ADR-006 首包自动注册与设备类型智能匹配](docs/adr/ADR-006-首包自动注册与设备类型智能匹配.md)

---

*基于 refactor/depivot 分支编写，最后更新于 2026-08-29（机房工作台架构基线；补充本地开发端口覆盖说明）。*
