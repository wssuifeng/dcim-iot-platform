# dcim-iot-platform 数据中心动环监控平台

> **一句话定位**：自部署的数据中心动环监控平台——`docker compose up` 之后，你就拥有一个活着的数据中心：机房、设备、流动的数据、会响的告警。你的采集网关只凭三样配置（平台地址 + AccessKey + SecretKey）往北向契约里推数，配套的仿真服务让整个系统开箱即活。

> 对外说明文档。项目进行状态见 `docs/00_总控.md`（本文件不含任何状态信息，这是文档体系的纪律要求）。

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
2. 打开仿真服务（[simulator/](simulator/) 子项目，默认 :8090，独立进程独立构建）管理页，网关配置**只填三样**：平台地址 + AccessKey + SecretKey，保存并启动，点"平台连通自检"；
3. 点"拉取告警阈值"（顺带同步机房目录），创建虚拟设备（**5 类设备预设一键创建**：温湿度/UPS/智能电表/精密空调/漏水检测）并初始化环境档案；
4. 网关轮询寄存器、按策略北向上报——平台**首包自动注册**设备（按上报指标智能推断类型，如 temperature → 温湿度），数据落库、超阈值告警触发：右上角滑出告警弹窗，状态机 TRIGGERED → ACKED → HANDLING → RECOVERED → CLOSED 全程流转，站内通知（头部铃铛模态框）同步到达。

本地开发：

```bash
./mvnw spring-boot:run        # 后端（dev profile 自动执行 Flyway 迁移 + 种子）
cd frontend && npm run dev    # 前端，/api 代理到后端
```

启动后访问 `/api/swagger-ui.html` 查看实时 API 文档（应用 context-path 为 `/api`）；`/api/druid/index.html` 查看 SQL 监控（dev）。

---

## 二、这是什么

面向数据中心动力环境监控（DCIM）的自部署平台：

| 能力 | 说明 |
|------|------|
| 机房工作台 | 机房为一等导航上下文（主从式下钻）：概览（机房级 KPI/趋势/级别分布/设备 TOP）+ 设备与数据 + 告警三 tab；机房卡片默认按未处理告警降序排序 |
| 设备接入 | 首包自动注册（未知 deviceCode 自动建档 + 模板指标绑定，类型按指标编码智能推断）、网关清单上报、编码防重查询 |
| 数据接收门 | 四入口汇入传输无关核心：HTTP/MQTT 双 HMAC 通道 + TB token / MQTT 子凭证开放通道；机房归属校验、幂等去重、断网补传、批量逐条独立事务 |
| 零散设备接入 | 自定义报文映射模板（挂机房，特征匹配路由 + 字段路径转译）；原始报文留存 + dry-run 预览（"零散设备接入"页，ADMIN） |
| 网关观测 | 心跳保活（恒走 HTTP）、last_seen 持续刷新、网关离线告警（周期扫描 + MQTT 遗嘱即时触发，恢复自动闭环） |
| 指标字典 | 指标定义、类型级指标模板、设备指标绑定；指标采样（时序月分区）+ 最新值 + 保留策略与 TTL 清理 |
| 告警模版 | 可命名的告警规则融合模版：类型绑定基础版作默认，设备可改绑自定义模版，自动注册设备零配置跟随类型 |
| 跨机房检索 | 2-4 机房并排对比：设备命中/在线率/活动告警/级别分布对比卡 + 设备/告警明细双 tab |
| 告警状态机 | 模版规则引擎、五级告警色带、最严重命中、级别升级、全程留痕；阈值可经网关签名导出 |
| 通知 | 站内通知模态框、WebSocket（STOMP）用户隔离推送、告警弹窗（级别色带 + 提醒音效可静音）、邮件、IM webhook 渠道（企微智能机器人/钉钉/飞书/ntfy/Telegram）、下行动作 webhook、通知日志与去重 |
| 认证与审计 | JWT 无状态认证、单租户三角色 RBAC（ADMIN/OPERATOR/VIEWER）、账号锁定防爆破、IP 限流、邮箱验证码注册、结构化审计日志 |
| 可观测 | TraceId 全链路、Druid 慢 SQL 监控、Actuator 健康检查、生产安全门控 |

**平台内不做仿真**：造数职责由 **dcim-simulator** 仿真服务承担（`simulator/` 子目录，独立进程独立构建）——虚拟设备（Modbus TCP slave，5 类设备预设，双层环境物理引擎：室外气象确定性函数 + 室内热湿平衡持久积分，故障注入）+ 智能网关（FC03 轮询 / 工程量解码 / 周期-死区-越限上报策略 / MQTT-HTTP 双传输北向客户端），设备与网关间真实 Modbus TCP 通信，与真实机房两级链路同构。网关对云平台零管理依赖：全部交互走 Gateway 签名（上报、心跳自检、阈值拉取、清单同步）。

### 技术栈

平台：Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + MySQL 9.6 + Redis 7 + JDK 17；前端 Vue 3 + Vite + Element Plus + Pinia + ECharts，日间/夜间双主题。
仿真服务：Spring Boot + JPA + H2（本地自包含），手写最小 Modbus TCP 子集（零协议依赖），Vue3 + Element Plus 单文件管理页。

---

## 三、数据上报（网关北向契约）

现场设备（RS485/Modbus/SNMP/BACnet）接入采集网关，网关做协议翻译后向北推送统一 JSON——**平台不实现任何南向协议**。

**机房即接入主体**：每个机房自带一套接入凭证（创建机房时初始化）。网关凭 AccessKey/SecretKey 经两条等价通道上报：

- **HTTP**：`POST /api/ingest/samples`，请求头 `Authorization: Gateway {AccessKey}:{sha256(SecretKey + rawBody)}`
- **MQTT**：发布到 `dcim/{accessKey}/samples`（QoS 1），报文 `{"signature","batch"}`；断连由 broker 遗嘱上报（仿真器网关 MQTT 不可达时自动回退 HTTP，数据不断流）

另有两条**开放通道**（面向不支持逐请求 HMAC 签名的生态组件，鉴权降级为凭证级 token / MQTT 账号密码）：

- **TB 兼容遥测 API**：`POST /api/v1/{token}/telemetry`（context-path `/api` 之外），报文兼容 ThingsBoard 三形态 + `device` 字段多子设备扩展
- **MQTT 子凭证开放通道**：发布到 `dcim-open/{mqttUsername}/samples`（每机房一组 MQTT 用户名/密码，连接层鉴权由 broker 承担）

**自定义报文映射**：两条开放通道均带方言回退——标准报文解析失败时，报文按机房映射模板做特征匹配（eq/contains/exists）+ 字段路径转译后进同一条核心链路；未命中的原始报文留存（每凭证一条），"零散设备接入"页可可视化配置与 dry-run 预览。

四种入口汇入同一条传输无关处理链：机房归属校验 → 未知设备自动注册（可选）→ `deviceCode+pointCode+seq` 幂等去重（开放通道无 seq 降级 `sampleTime`）→ 采样落库 → 告警评估与通知。批量 ≤200 条逐条独立事务，部分成功合法；`sampleTime` 早于平台时间为断网补传（合法），晚于 +5 分钟拒绝。

辅助契约（同样 Gateway 签名）：`POST /api/ingest/heartbeat` 心跳保活；`POST /api/ingest/inventory` 设备清单上报；`GET /api/ingest/export/thresholds` 阈值与机房目录导出（网关本地告警判定的配置源）。

---

## 四、目录结构

```
├── src/main/java/com/example/platform/   # 平台后端（controller/service/ingest/security/...）
├── src/main/resources/db/migration/      # Flyway 迁移（表结构事实来源）
├── frontend/                             # Vue3 管理后台
├── docker/mosquitto/                     # MQTT broker 配置
├── simulator/                            # dcim-simulator 仿真服务（独立进程独立构建）
├── docs/                                 # 阶段文档包体系（00_总控 为唯一入口）
├── test/  deploy/  thesis/               # 毕设交付目录（测试/部署/论文）
└── 开发控制文档/                          # 外部控制包本地分发（不入 Git）
```

---

## 五、构建与测试

| 命令 | 用途 |
|------|------|
| `./mvnw test` | 平台全量测试 |
| `./mvnw spring-boot:run` | 启动后端（需 MySQL + Redis） |
| `cd simulator && mvn test` | 仿真服务测试（独立子项目，独立 H2） |
| `cd frontend && npm run test` | 前端 Vitest 单元测试 |
| `cd frontend && npm run build` | 前端生产构建 |
| `docker compose up -d --build` | 一键容器化部署 |

环境变量见 `.env.example`（数据库/Redis/JWT/邮件/注册开关/MQTT 接收门）。生产环境由 `ProductionSafetyGuard` 启动门控强制：必须替换默认 JWT 密钥、配置明确 CORS 来源、配置数据库与 Redis 密码。

---

## 六、文档体系导航

- 项目状态与阶段进度：`docs/00_总控.md`（全项目唯一状态源）
- 系统定义（目标/非目标）：`docs/10_系统定义/系统定义.md`
- 各阶段产物：`docs/10~70 各阶段包`（每包入口为 `_中控.md`）
- 决策与灵感：`docs/90_全局/`（决策日志 / 术语表 / 灵感留存处）
- 架构决策记录：`docs/archive/adr/`（ADR-001~009）
- 历史文档：`docs/archive/`（只进不改，仅考据用）
- 毕业设计交付文档：`docs/毕业设计/`（目标系统视角，入口 `00-工作流规划与差距分析.md`）
