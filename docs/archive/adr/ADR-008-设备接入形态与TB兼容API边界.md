# ADR-008 设备接入形态事实与 ThingsBoard 兼容 API 边界

- 状态：已接受（2026-08-31）
- 关联：阶段八 S8-6（接入形态前置事实调研）/ S8-10（TB 兼容设备 API）/ S8-5（MQTT 子凭证）
- 证据底座：`docs/阶段八-真实用户打磨计划/00-调研问题总账与开源项目清单.md`

## 一、背景

阶段八定位"真实用户半天接完设备"。定义常见设备预设、开放接入通道之前，必须先回答一个前置事实：**设备到底以什么形态接入云平台**。本 ADR 给出接入形态分类与取证（S8-6），并据此划定 ThingsBoard 兼容 API 的安全/限流/类型推断边界（S8-10 的决策前提）。

## 二、接入形态事实（S8-6 调研结论）

| 形态 | 链路 | 典型器件 | 平台侧凭证形态 |
|------|------|---------|---------------|
| **A 裸设备经采集网关** | RS485/Modbus RTU 等现场总线 → 采集网关做协议转换与轮询 → 北向上报 | 温湿度变送器（Modbus RTU）、PZEM 电表、带 485 卡的 UPS、水浸绳 | 机房 AK/SK（现 HMAC 通道），网关侧管理 |
| **B 智能网关（多协议驱动）** | 同 A，但网关自带多协议驱动与边缘逻辑 | ThingsBoard IoT Gateway（Modbus/MQTT/BACnet 驱动）、Milesight UG65、Node-RED 流 | 机房 AK/SK；**TB 网关生态按"每子设备一个 token"上报**（TB 兼容层的直接动机） |
| **C 独立联网设备自报** | 设备自带 HTTP/MQTT 客户端，自己上报自己的数据，无网关 | ESPHome 节点、NUT（UPS 监控守护）、厂商云平移过来的直连表计 | 生态组件不支持 HMAC 签名 → 形态 C-MQTT 走 S8-5 子凭证；形态 C-HTTP 走本 ADR 的 TB 兼容 token |

结论：**预设/寄存器库只对形态 A/B 有意义**（形态 C 没有寄存器概念，直接以 JSON key 上报）；预设功能随 ADR 结论另行排期，不阻塞本 ADR。

## 三、决策（S8-10 TB 兼容设备 API 边界）

### D1 路径与格式

`POST /api/v1/{token}/telemetry`（context-path `/api` 之外，即 servlet 路径 `/v1/**`）。兼容 ThingsBoard 遥测报文三形态，并做一处显式扩展：

```jsonc
// T1 TB 简单对象：值集，时间取服务器接收时刻
{ "temperature": 25.1, "humidity": 60 }

// T2 TB 时间戳对象：ts 为毫秒 epoch
{ "ts": 1693468800000, "values": { "temperature": 25.1 } }

// T3 TB 数组：批量
[ { "ts": ..., "values": {...} }, ... ]

// 扩展 E1（网关场景显式扩展）：对象/时间戳对象可携带 device 字段指定设备编码
{ "device": "tb-gw-01-meter", "ts": ..., "values": {...} }
```

设备定位：报文带 `device` → 以该 deviceCode 落库（网关场景，一台 TB 网关多子设备）；无 `device` → 使用凭证上绑定的 `telemetry_default_device`（单设备直连场景，如 ESPHome/NUT：一设备一 token）。两者皆无 → 422 拒绝。

### D2 token 语义与安全边界

- token **映射到机房凭证**（而非设备）：新增 `ingest_credential.telemetry_token`（随机 40-hex，唯一索引，ADMIN 可重置），机房归属 = token → 凭证 → cloudRoomId，与 HMAC 通道同源。
- **不复用 secret 作为 token**：URL path 会进访问日志/代理日志，现有纪律是 secret 零落日志；telemetry_token 是独立可重置的低敏感度凭证，泄露面与 secret 隔离。
- 无 HMAC 签名是该兼容层的**既定取舍**（动机即 TB 生态不支持逐请求签名）；仅建议 HTTPS 部署。开放通道整体纳入后期网安专项（与 S8-5 同口径）。
- 鉴权失败统一 401（防 token 枚举）；成功返回 200 + 平台 Result 结构（TB 客户端只关注 HTTP 状态码）。

### D3 类型推断与建模

- key → `point_code` 直接映射指标字典；未登记指标按 ingest 现口径报错入 failures。
- 首包自动注册：凭证开启 `auto_register` 时复用 `typeInferencer.inferByPointCodes`（类型推断）→ 凭证 `defaultTypeId` 兜底，与 HMAC 通道完全同链路（复用 `IngestCoreService.processSamples`，传输无关核心不感知新入口）。
- 设备必须归属凭证机房，越权 device 字段按 `INGEST_ROOM_ACCESS_DENIED` 拒绝。

### D4 幂等口径

TB 报文无 seq。按 S8-5 已定的开放通道降级口径：seq 缺失时幂等键取 `deviceCode + pointCode + sampleTime`（dedupKey 扩展，HMAC 通道 seq 语义不变）。T1 简单对象时间取服务器接收时刻，此时幂等仅对"同毫秒重复"有效——**明确接受：TB 模式不承诺严格幂等**，可靠性由 HTTP 单次语义承担。

### D5 限流

复用全局 `RateLimitFilter`（现状按来源 IP 滑窗），不新增 per-token 维度——单租户小规模场景下全局窗口已覆盖；批内条目上限 200 与 ingest 同口径。后续出现多租户或滥用再引入 per-token 桶。

### D6 放弃项

- 不做 TB 属性 API（`/attributes`）、RPC、设备侧订阅——仅 telemetry 单向上报，保持"通知/数据入口"边界。
- 不为 TB 兼容层单独建设备表结构：设备仍是平台 `devices` 统一建模，token 只是凭证侧的另一种开锁方式。

## 四、后果与影响面

- `ingest_credential` 新增 2 列（V23）：`telemetry_token`、`telemetry_default_device`；凭证管理接口补 token 生成/重置。
- `GatewaySignUtils.dedupKey` 扩展 seq 缺失降级（HMAC 通道行为不变）。
- 新增 `/v1/**` 自鉴权路径（SecurityConfig permitAll + controller 内 token 校验，复用 `IngestCoreService`）。
- S8-5 MQTT 子凭证（下一卡）复用本 ADR 的 D2 归属校验与 D4 幂等降级，不重复决策。
