# 点位模版重构与前端 UI 重设计计划

> 持久化计划书 · 2026-08-28 · 分支 `refactor/depivot`（平台）/ `main`（dcim-simulator）
> 覆盖：① 告警点位模版（规则融合模版）重构；② 两服务前端 UI 重构级美化；③ README 修订
> 事实核对基线：平台 commit `bc2a063`（V14 机房凭证合一）、仿真器 commit `9b5f8ec`

> **执行前置（新会话按此启动）**：
> 1. 必读：`README.md` + `AGENTS.md` + `docs/文档导览.md`，然后回到本文（本文是本任务唯一执行蓝本）；
> 2. 告警链路背景：`docs/历史归档/2026-08-24-数据上报与告警模块重构设计.md` §1 决策表（D01-D25）与 §6 事务边界——三段单向链路铁律不得破坏；
> 3. 前端 skill：主用 `redesign-existing-projects`（审查→改→不破坏功能），每步用 `webapp-testing` 截图验收（亮/暗双主题、空态）；**不要**用 `design-taste-frontend`/`frontend-design`（落地页美学，不适用驾驶舱 UI）；
> 4. 两仓库位置：平台 `D:/code/iot-platform-main - codex`，仿真器 `D:/code/dcim-simulator`（独立 git 仓库，提交署名同平台）；
> 5. 本地服务约定：平台 :8081 / 前端 :5174 / 仿真器 :8090（8080/5173 被用户其他项目占用，勿动）；三服务当前在运行中，改完代码重启验证；
> 6. 按第四部分实施顺序执行，每步独立提交；步骤 1-2 与步骤 3 可并行。

---

## 0. 设计取向声明（Design Read）

两个前端均为**企业级监控驾驶舱**，不是营销页。参照对象：Grafana 的数据密度、Linear 的克制工具美学、Element Plus 企业组件规范。设计三参数：

| 参数 | 取值 | 理由 |
|------|------|------|
| DESIGN_VARIANCE | 3 | 工具属性，可预期布局优先 |
| MOTION_INTENSITY | 3 | 仅状态反馈动效（悬停/切换/告警进入），无装饰动效 |
| VISUAL_DENSITY | 6 | 驾驶舱密度：紧凑表格 + 等宽数字 + 1px 分隔 |

设计铁律（对全部 UI 生效）：
- **单一强调色锁定**：主色 `#2563eb`（蓝）贯穿两服务；语义色仅限 success/warning/danger/info 四态 + 告警五级色带；
- **圆角体系锁定**：卡片 8px、按钮/输入遵循 Element Plus 默认（4px）、标签 pill——不混用；
- **等宽数字**：所有数据值（温度/功率/时间戳/统计数）使用 `font-variant-numeric: tabular-nums` + 等宽栈（`JetBrains Mono, Consolas, monospace`）；
- **状态完整性**：每个数据视图必须有骨架态（加载）/空态（引导创建）/错误态（重试入口）三件套；
- **暗色模式**：平台沿用现有日/夜双主题令牌；仿真器管理页补齐暗色（当前纯亮色）；
- **无 AI 套路**：无渐变文字、无外发光、无装饰性彩色圆点（状态点仅表达真实语义）、无 emoji 图标。

---

# 第一部分：点位模版（告警规则融合模版）重构

## 1.1 现状与问题

### 现有模型（事实核对）

```
device_type_point_template（"模版"）= 类型→点位清单（is_required/sort_order），不含任何规则
device_point_alarm_rule（规则）     = 挂在 template_item_id 上；device_id 空=模板级、非空=该设备覆盖
解析链：设备→typeId→生效模板项→规则；按 pointId 分组"替换式降级"（有设备级覆盖则整组取设备级）
```

### 与设计意图的差距（用户原话对齐）

| 用户意图 | 现状 | 差距 |
|---------|------|------|
| 点位模版 = **可命名、可自定义的告警规则融合模版**（一套安装场景的规则聚合） | "模版"只是点位清单，规则是散行 | 模版不是一级实体，无法命名/复用/整体改绑 |
| 相同要求的设备（不同风口温湿度）**绑同一个自定义模版** | 只能逐条建"设备级覆盖规则" | 无法按"安装场景"批量管理规则 |
| 设备类型绑定**通用基础模版**（默认设置） | 类型绑定的是点位清单，规则另挂 | 类型与规则模版割裂 |
| 自动注册设备**自动绑定设备级聚合模版** | 自动注册只拷贝点位绑定，规则靠 template_item_id 动态解析"伪跟随" | 概念绕、无法给不同机房的同类型设备不同规则 |
| 具体设备可**改绑其他自定义模版** | 不存在改绑语义 | 缺失 |

另核对出两处**实现与注释不符**（重构时一并修正）：
- 表注释声称"三级降级（设备级→组织模板→平台模板）"，实际只有两级，且 `resolveTemplateSource` 是死代码；
- 告警规则前端 CRUD API 已封装（`api/pointAlarmRule.js`）但**无任何页面引用**——规则配置界面从未落地。

## 1.2 目标模型

### 概念

**告警模版（alarm_template）**：命名的规则融合模版。一个模版 = 若干点位 × 每点位若干条规则（多级阈值）+ 可选的通用规则（离线检测）。绑定语义两级：

```
设备.alarm_template_id（可空，"改绑"）
        │ 为空 ↓
设备类型.alarm_template_id（基础模版，默认设置）
        │
        ▼
alarm_template_item（规则行）── 告警引擎按模版解析生效规则
```

自动注册的设备 `alarm_template_id` 为空 → 天然跟随类型的模版，零额外动作；需要差异化的设备（如某机房风口温度上限更严）改绑自定义模版即可，规则整体切换。

### 数据模型（V15 迁移）

```sql
-- 告警模版主表
CREATE TABLE alarm_template (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(50)  NOT NULL UNIQUE COMMENT '模版编码，如 TEMP_HUM_STD',
    template_name VARCHAR(100) NOT NULL COMMENT '显示名，如"温湿度标准监控"',
    description   VARCHAR(300) COMMENT '用途说明，如"机房通用温湿度阈值"',
    status        TINYINT NOT NULL DEFAULT 0 COMMENT '0-启用 1-停用',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '告警规则融合模版';

-- 模版规则行（原 device_point_alarm_rule 的模板级语义迁入）
CREATE TABLE alarm_template_item (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id  BIGINT NOT NULL COMMENT '所属模版',
    point_id     BIGINT NULL COMMENT '关联点位（通用规则如 OFFLINE 为空）',
    rule_type    VARCHAR(20) NOT NULL DEFAULT 'METRIC' COMMENT 'METRIC/OFFLINE',
    rule_name    VARCHAR(100) NOT NULL,
    operator     VARCHAR(10) COMMENT 'GT/LT/GE/LE/EQ/NE',
    threshold    DECIMAL(18,4) COMMENT 'OFFLINE 时=分钟数',
    alarm_level  TINYINT NOT NULL DEFAULT 3 COMMENT '1紧急-5提示',
    enabled      TINYINT NOT NULL DEFAULT 1,
    sort_order   INT NOT NULL DEFAULT 0,
    KEY idx_tpl_item_tpl (template_id)
) COMMENT '模版规则行';

-- 绑定关系（新增列）
ALTER TABLE device_types ADD COLUMN alarm_template_id BIGINT NULL COMMENT '该类型的基础告警模版';
ALTER TABLE devices      ADD COLUMN alarm_template_id BIGINT NULL COMMENT '设备改绑的模版（空=跟随类型）';
```

规则行不再需要 `template_item_id` 锚定"类型×点位"，点位直接挂在规则行上——旧的三表绕行（模版项→类型→规则）坍缩为两表直连。

### 解析链（AlarmRuleEngine 改造）

`findEffectiveRules(deviceId, pointId)` 新逻辑：

```
1. 设备.alarm_template_id ?? 设备类型.alarm_template_id → 生效模版
2. 生效模版 enabled=1 且 status=0 的规则行按 pointId 过滤
3. 通用规则（point_id 为空，如 OFFLINE）设备级全局生效
```

- 删除"按 pointId 分组替换式降级"（改绑即整体切换，语义更清晰）；
- 同点位多级阈值（40 紧急 + 35 严重）继续由多行同 pointId 规则表达，最严重命中逻辑不变；
- 级别升级/恢复/通知链路（AlarmEventService）零改动。

### 存量数据迁移（V15 内完成）

1. **每设备类型生成一个基础模版**：聚合该类型全部模板项的模板级规则（device_id IS NULL）→ `alarm_template("TEMP_HUMIDITY-基础", ...)` + 规则行；回填 `device_types.alarm_template_id`；
2. **有设备级覆盖规则的设备**：复制类型基础模版 → 应用覆盖规则（按 pointId 整组替换）→ 生成"<设备名>专用模版"并绑定 `devices.alarm_template_id`；
3. 旧表 `device_point_alarm_rule` 保留数据冻结（迁移源），`AlarmEvaluateServiceImpl` 切换读新表；运行一个验证周期后随 V16 删除；
4. `device_type_point_template` 保留——它退回纯粹职责：**类型→点位清单**（自动注册拷贝绑定的依据），与告警模版解耦。

### REST 与权限

新增 `AlarmTemplateController`（`/alarm-templates`）：

| 端点 | 说明 | 权限 |
|------|------|------|
| `GET /page` | 模版分页（名称/编码/状态筛选，含引用计数：绑定类型数+绑定设备数） | 已认证 |
| `GET /{id}` / `GET /{id}/items` | 模版详情与规则行 | 已认证 |
| `POST` / `PUT /{id}` / `DELETE /{id}` | 模版 CRUD（删除前校验无绑定） | ADMIN |
| `POST /{id}/items` / `PUT /items/{itemId}` / `DELETE /items/{itemId}` | 规则行 CRUD（形状校验复用现有 METRIC/OFFLINE 规则） | ADMIN/OPERATOR |
| `POST /{id}/duplicate` | 复制模版（自定义模版的常规起点：复制基础模版→改） | ADMIN/OPERATOR |

设备类型与设备的绑定：
- `device_types` 的 `PUT /device-types/{id}` 增加 `alarmTemplateId` 字段（ADMIN）；
- `PUT /devices/{id}` 增加 `alarmTemplateId`（空=跟随类型；ADMIN/OPERATOR）；
- 设备详情 VO 返回 `effectiveTemplateName`（解析结果，便于前端展示"生效模版"）。

阈值导出接口（`/ingest/export/thresholds`）改为按网关所属机房内全部设备的**生效模版**聚合导出，仿真器 ON_ALARM 拉取口径不变。

## 1.3 前端界面

### 新页面：告警模版管理（`/alarm-templates`，ADMIN 写 / 全角色读）

```
┌ 告警模版 ──────────────────────────────────────────────┐
│ [搜索名称/编码] [状态▾]                    [+ 新建模版] │
├────────────────────────────────────────────────────────┤
│ 编码      名称            绑定            引用   状态    │
│ TEMP_HUM  温湿度标准监控  类型:温湿度      3台    启用    │
│ TEMP_HUM_A机房 风口加严版  设备:2台       2台    启用    │
└────────────────────────────────────────────────────────┘
```

**模版编辑器**（抽屉或独立视图）：
- 左列：模版信息（编码/名称/描述/状态）；
- 主体：规则行表格，按点位分组折叠（`temperature` 组：GT 35→严重 / GT 40→紧急 / LT 10→警告；`OFFLINE` 组：>15 分钟→严重）；
- 每行内联编辑（operator 下拉 + threshold 数字输入 + level 下拉），支持"复制行"快速建多级阈值；
- 底部显示"绑定引用"清单（哪些类型/设备在用），改阈值前可见影响面。

### 既有页面适配

- **设备类型页**：类型卡片/编辑框增加"基础告警模版"下拉（可选未停用模版）；
- **设备编辑框**：增加"告警模版"下拉，默认项显示"跟随类型（温湿度标准监控）"，选择其他模版即改绑；
- **设备列表**：加"生效模版"列（跟随类型时灰色显示类型模版名，改绑后正常色显示自定义模版名）；
- **点位字典页**：保留现状（点位定义与保留策略），规则配置入口全部收敛到告警模版页。

## 1.4 验收标准

1. 迁移后：现有温湿度规则（35/40/10/离线15min）在"温湿度标准监控"模版中完整可见，告警行为与迁移前一致（用 45℃ 上报验证紧急告警仍触发）；
2. 新建"风口加严版"模版（GT 30→严重），改绑某温湿度设备 → 该设备 32℃ 上报触发严重告警，**其他同类型设备不受影响**；
3. 改绑回"跟随类型" → 规则立即回到基础模版口径；
4. 自动注册新设备 → 生效模版为类型基础模版，零配置；
5. 删除被类型绑定的模版 → 拒绝并提示影响面；
6. `mvn test` 全绿（新增：模版 CRUD/解析链/迁移等价性 ≥12 例）。

---

# 第二部分：前端 UI 重构

## 2.1 平台前端审计结论

**底子尚可**：`src/styles/theme.css` 已有完整 CSS 变量令牌（slate 中性 + `#2563eb` 主色 + Element Plus 变量桥接），日/夜双主题已通。**问题在应用层**：

| 问题 | 证据 | 修法 |
|------|------|------|
| 令牌旁路 | 大量页面硬编码 `#ebeef5` 边框（cloud-room/device/filter-bar 等 15+ 处） | 统一改 `var(--color-border)` |
| 告警级别无语义色带 | 级别仅 tag type，1/2 级同为 danger 视觉不分 | 定义 `--alarm-l1..l5` 五级色带（红→橙→黄→蓝→灰），tag/弹窗/趋势图统一使用 |
| 数值不等宽 | 温度/功率/时间戳用默认字体，列抖动 | `.num` 工具类：tabular-nums + mono 栈，全数据列应用 |
| 状态不完整 | 表格仅 loading 遮罩，无骨架屏；空态是裸 `el-empty` | `DataTableShell` 包装组件（骨架/空态引导/错误重试三态） |
| 页面骨架重复手写 | 每页自写 page-shell/page-header/filter-bar 样式 | 提为全局布局组件 `<PageShell>` `<PageHeader>` `<FilterBar>` |
| 布局头部信息弱 | header 仅主题+铃铛+用户；机房/网关总览无处一屏可见 | 见 2.2 仪表盘重设计 |
| ECharts 主题未接令牌 | 趋势图用默认配色，与主题脱节且暗色模式下刺眼 | 注册 light/dark 双 ECharts 主题，色板从 CSS 变量取值 |

## 2.2 平台页面级重构

### 仪表盘（首页，重设计为驾驶舱）

```
┌─ 机房总览 ──────────────────────────────────────────────┐
│ [机房卡片×N：名称/网关状态点/设备在线率环/活跃告警数]      │
├─ 告警态势 ────────────────────┬─ 数据流速 ──────────────┤
│ 五级告警色带堆叠条 + 24h 趋势  │ 今日采样数/入库速率      │
│ 活跃告警 TOP 设备列表          │ (等宽数字大卡片)         │
└──────────────────────────────┴─────────────────────────┘
```

- 机房卡片三色状态语义：绿=网关在线且无活跃告警 / 黄=有告警 / 红=网关离线——与机房管理页口径一致；
- 全部数字等宽大字（`tabular-nums`），单位小字灰色；
- 空（无机房）态引导"新建机房"直达。

### 机房管理（合一模型的信息层级复核）

- 列表列重排：机房名/编码 → **网关状态（色点+文字）** → AccessKey（等宽、点击复制图标）→ 最近活跃 → 自动注册 → 位置 → 操作；
- 状态点仅此处与仪表盘/监控页使用（真实语义，符合克制原则）；
- 一次性密钥弹窗升级：AccessKey/SecretKey 代码块样式 + 复制按钮 + "三样配置"说明收成一行三个复制目标（平台地址/AK/SK）。

### 数据监控

- 左侧设备列表加状态点与"最后上报"时间（相对时间：3 秒前）；
- 最新值区改**数值卡片网格**（点位名 + 大号等宽值 + 单位 + 相对时间），替代表格——监控语义下卡片密度合理；
- 趋势图接入统一 ECharts 主题，多点位切换；
- 接入网关面板与机房卡片样式统一。

### 告警管理

- 级别筛选与列表均用五级色带；活跃告警行左侧 3px 级别色条（视觉扫读）；
- 详情抽屉的"状态流转"时间线节点按状态着色；
- 已有右上角弹窗保持，卡片加级别色带顶边。

### 全局组件沉淀

```
src/components/
  PageShell.vue        # 页面骨架（max-width/背景/留白规范）
  PageHeader.vue       # 标题+副题+操作区
  FilterBar.vue        # 筛选区容器
  DataTableShell.vue   # 表格三态包装（骨架/空/错误）
  StatValue.vue        # 等宽数值展示（值+单位+相对时间）
  StateDot.vue         # 语义状态点（online/offline/stale 三态）
src/styles/
  theme.css            # 追加 --alarm-l1..l5、--font-mono、.num 工具类
  echarts-theme.js     # light/dark 双主题注册
```

## 2.3 仿真器管理页重构（dcim-simulator）

现状：CDN 单文件 Vue3 + Element Plus，三 Tab（虚拟设备/环境配置/网关与观测），纯手写样式，无主题系统、无暗色、观测仅数值表。

**方向**：与平台前端同一套视觉语言（同色板/同状态语义/同等宽规则），保持单文件形态（独立服务的轻量属性）：

1. **令牌内联化**：单文件内建 `:root` CSS 变量块，色值与平台 theme.css 对齐（含暗色 `prefers-color-scheme` 媒体查询）；
2. **Tab1 虚拟设备**：设备卡片化（编码/名称/类型/机房/Modbus 端口/故障状态/运行状态），故障注入按钮组改为状态分段控件（正常/卡死/失联三段单选，当前态高亮）；
3. **Tab2 环境配置**：通道列表按点位分组显示当前函数曲线缩略（内联 SVG 小图：正弦/斜坡/阶跃形状示意），快捷操作按钮区独立成"演练"卡片；
4. **Tab3 网关与观测**：
   - 网关配置区（三样配置）与连通自检/阈值同步状态整合为一张"北向链路"卡片：平台地址、AK/SK、传输方式 + 自检结果徽标；
   - 观测区从数值表升级为**数值卡片网格**（同平台 StatValue 风格）+ 每 5 秒刷新的轻量迷你趋势（sparkline，原生 canvas 画最近 20 个点，不引图表库）；
   - 离线设备列表带状态点；
5. **反馈统一**：操作成功/失败全部走 ElMessage（已有）， destructive 操作（删除设备）加确认框（已有）——保持，视觉微调对齐。

## 2.4 前端验收标准

1. 两服务同屏对比：主色/状态语义/圆角/等宽数字一致，一眼是同一产品家族；
2. 平台任意数据页：断网时出错误态（重试按钮），空库时出引导空态，加载中出骨架——三态全覆盖；
3. 暗色模式下无不可读区域（重点：ECharts 图表、状态点、五级色带）；
4. 仪表盘一屏回答三问：机房都活着吗（网关状态）/ 有什么告警（级别分布）/ 数据在流动吗（入库速率）；
5. `npm run build` 通过，无控制台警告新增。

---

# 第三部分：README 修订清单

README.md **已过时**，需修订项（核对于 bc2a063）：

| 位置 | 现状描述 | 应改为 |
|------|---------|--------|
| 一、五分钟 | "四台设备（其中一台离线）……预置告警规则" | 设备不再预置（V9 已清空），改为"创建机房即得接入凭证，配合仿真网关走全流程"的演示路径 |
| 一、五分钟 | "Flyway V1–V10" | V1–V14 |
| 三、之后 | "管理员在'接入管理'页为机房签发网关凭证" | 机房与凭证合一：创建机房即初始化凭证（一次性弹窗），无独立接入管理页 |
| 二、能力表 | "仿真推演：模拟场景/步骤编排……（阶段三将重建）" | 已退役：平台内仿真已删除（V12），仿真职责在独立仓库 dcim-simulator（设备/网关双模块 + Modbus TCP） |
| 二、能力表 | 缺告警模版条目 | 本轮重构后补"告警模版：可命名的规则融合模版，类型绑基础版、设备可改绑" |
| 通知描述 | 独立通知页 | 铃铛模态框 + 右上角告警弹窗（WebSocket） |
| 路线图 | 阶段三"进行中" | 完成；补充机房凭证合一、首包自动注册+类型智能匹配、网关离线告警等演进 |
| 目录结构 | task/ 注释、迁移版本号 | 同步 V14/离线任务/幂等清理 |

README 修订随本轮重构**一并执行**（不单独提交）。

---

# 第四部分：实施顺序

| 序 | 内容 | 仓库 | 依赖 |
|----|------|------|------|
| 1 | V15 迁移 + 告警模版实体/服务/解析链切换 + 迁移等价性测试 | platform | — |
| 2 | AlarmTemplateController + 设备/类型绑定字段 + 阈值导出适配 + 测试 | platform | 1 |
| 3 | 全局组件沉淀（PageShell/StatValue/DataTableShell/五级色带/echarts 主题）+ 存量页面令牌旁路清理 | platform | — |
| 4 | 告警模版管理页（列表+编辑器）+ 设备/类型页绑定 UI | platform | 2,3 |
| 5 | 仪表盘驾驶舱重设计 + 数据监控数值卡片 + 告警页色带 + 机房页复核 | platform | 3 |
| 6 | 仿真器管理页重构（令牌对齐/设备卡片/北向链路卡片/sparkline 观测/暗色） | simulator | 3（视觉规范） |
| 7 | README 修订 + E2E（模版改绑告警行为 + 两服务 UI 走查）+ 提交 | 两仓库 | 全部 |

每步独立提交；步骤 3 与 1-2 无依赖可并行。

## 风险与边界

- **迁移等价性**是步骤 1 的硬验收：迁移前后同一设备同一数据必须产出同一告警（自动化对比测试）；
- 告警引擎的依赖单向铁律（T1 不调告警类）在模版解析中保持——模版解析在评估侧，采样侧零改动；
- 旧 `device_point_alarm_rule` 冻结一个版本周期再删（V16 预留），期间双表只读旧不写旧；
- 仿真器 sparkline 用原生 canvas，不引入图表库（保持单文件零构建依赖的轻量属性）。

---

*本文档为执行蓝本；实现与本文偏差时以提交信息说明为准。*
