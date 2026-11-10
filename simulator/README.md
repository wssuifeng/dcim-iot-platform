# DCIM Simulator

同仓独立子项目，用虚拟 Modbus TCP 设备、采集网关和环境模型为动环平台提供联调数据。独立 Maven 构建、独立进程，使用 MySQL 独立库；不在平台应用进程内运行，也未纳入根 Compose。

## 启动

需要 JDK 17、Maven 与 MySQL。先建 dcim_simulator_db，再在 PowerShell 中设置测试数据库连接：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/dcim_simulator_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:SIM_DB_USERNAME="root"
$env:SIM_DB_PASSWORD="填写本地测试数据库密码"
cd simulator
mvn spring-boot:run
```

管理页默认为 http://localhost:8090。环境变量只对当前终端生效；本模块不会自动读取父目录的 .env。完整三进程步骤见 [本地开发与仿真联调](../docs/70_部署运行/本地开发与仿真联调.md)。

## 与平台联调

1. 平台创建机房，保存一次性显示的接入凭证；需要自动创建设备时启用首包登记。
2. 仿真器“网关与观测”新增网关，绑定机房并填平台地址、AccessKey、SecretKey。默认平台地址为 http://localhost:8080，修改后以实际地址为准。
3. 创建机房设备套件或单台虚拟设备，启动网关，检查上报成功/失败计数。
4. 在平台机房工作台查看设备、最新值、历史曲线和告警；告警验证使用合成阈值场景。

## 能力与边界

- 五类预设：温湿度、UPS、PDU、精密空调、漏水；设备指标与平台字典对齐。
- 虚拟设备寄存器、Modbus TCP 轮询、工程量解码、周期/死区/越限上报策略。
- 每机房独立网关配置与运行时；HTTP/MQTT 客户端及上报统计。
- 环境模型、故障注入与运行态持久化供开发演练；不等同于真实设备精度或真实机房验证。

[环境模型设计](docs/环境物理引擎设计.md) · [原实施蓝图](../docs/archive/计划书/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md)

## 检查

```sh
# 在 simulator 目录
mvn -B verify
```

2026-11-10：79 项测试通过并打包成功；在隔离 MySQL/Redis 环境与平台完成 HTTP 联调，观测截点 236 次请求全部成功。MQTT 端到端、真实硬件和长期运行未在本轮验证，细节见 [本轮验证](../docs/50_系统测试/2026-11-10-仓库收口验证.md)。
