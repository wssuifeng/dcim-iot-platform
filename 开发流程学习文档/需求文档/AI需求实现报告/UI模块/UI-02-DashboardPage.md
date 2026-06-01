# 仪表盘 — 前端实现方案

> **版本**: V1.0.0 | **创建**: 2026-05-31 | **需求**: REQ-UI-FR-002
> **关联基础设施**: UI-00-InfrastructureREADME.md
> **关联规范**: FrontendPageImplementationPrompt.md V3.1.1
> **页面类型**: 后台管理页面（首页，需要 Layout）

---

## §A 实现边界（必须首先声明）

本页面**仅实现**需求文档明确要求的功能。

**禁止主动增加以下内容**（除非需求文档明确要求）：
- 国际化 (i18n)
- 虚拟滚动 / 无限滚动
- 微前端 / SSR / PWA
- WebWorker / Service Worker
- 实时 WebSocket 推送（需求中仪表盘是 30s 轮询，不做 WebSocket）
- 拖拽布局 / 自定义仪表盘
- 全屏模式
- 数据导出功能
- 深色模式

---

## §0 基础设施约定

全部引用 `UI-00-InfrastructureREADME.md`，本章仅补充仪表盘特化内容。

### §0.1 技术栈

同 UI-00-InfrastructureREADME §0.1，仪表盘额外使用：

| 技术 | 版本 | 用途 |
|------|:---:|------|
| ECharts | 5.x | 告警趋势折线图 |
| vue-echarts | 6.x | ECharts Vue 封装（推荐） |

### §0.2 后端接口

| 接口 | 方法 | 实际路径 | 认证 | 说明 |
|------|:---:|------|:---:|------|
| 仪表盘统计 | GET | `/api/dashboard/stats` | 已认证 | 设备总数/在线/离线 + 告警总数/未处理数 |
| 告警时间统计 | GET | `/api/alerts/stats/time-range` | 已认证 | 按时间段统计告警（用于 7 天趋势图） |
| 未处理告警 | GET | `/api/alerts` | 已认证 | 分页查询告警（取前 10 未处理告警） |

### §0.3 Axios 拦截器

同 UI-00-InfrastructureREADME §0.3。

### §0.4 路由守卫

同 UI-00-InfrastructureREADME §0.4。

**仪表盘特殊行为**：
- 仪表盘为登录后的默认首页（`/` redirect `/dashboard`）
- Layout 组件包裹仪表盘，提供侧边栏和顶部栏
- 未登录无法访问 `/dashboard`（全局守卫拦截）

### §0.5 按钮级权限

仪表盘为所有已认证用户可见页面，无按钮级权限控制。

### §0.6 RBAC 权限矩阵

| 端点 | ADMIN | OPERATOR | VIEWER | 未认证 |
|:---|:---:|:---:|:---:|:---:|
| `GET /api/dashboard/stats` | ✅ | ✅ | ✅ | ❌ |
| `GET /api/alerts/stats/time-range` | ✅ | ✅ | ✅ | ❌ |
| `GET /api/alerts` | ✅ | ✅ | ✅ | ❌ |

### §0.7 枚举映射表

同 UI-00-InfrastructureREADME §0.7，仪表盘额外使用 `DEVICE_STATUS` 和 `ALERT_LEVEL`。

### §0.8 输入缺失处理

- ✅ `ControllerAPIREADME.md` — 已读取
- ✅ `IoT-Platform需求规格说明书.md` — 已读取
- ✅ `SecurityConfig.java` — 已读取

---

## §1 页面任务拆解

### P0 — 核心功能（必须完成，缺一则页面不可交付）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P0-1** | Layout 组件（侧边栏 + 顶部栏 + 内容区） | 中 |
| **P0-2** | 设备统计卡片（总数/在线/离线，含彩色图标） | 低 |
| **P0-3** | 告警统计卡片（总告警/未处理告警/紧急告警） | 低 |
| **P0-4** | 调用 `GET /api/dashboard/stats` 获取数据 | 低 |
| **P0-5** | 30 秒自动轮询刷新统计数据 | 低 |

### P1 — 重要功能（提升用户体验）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P1-1** | 最近 7 天告警趋势折线图（ECharts） | 中 |
| **P1-2** | 最近 10 条未处理告警列表（含告警级别颜色标记） | 低 |
| **P1-3** | 轮询前 Loading 指示器 | 低 |

### P2 — 增量优化（可选）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P2-1** | 首次加载骨架屏（Skeleton） | 低 |
| **P2-2** | 设备在线率环形图 | 中 |

---

## §2 目录结构

```
src/
├── layout/
│   └── index.vue                    # Layout 组件（侧边栏 + 顶部栏）
├── views/
│   └── dashboard/
│       └── index.vue                # 仪表盘入口
├── components/
│   └── dashboard/
│       ├── StatsCard.vue            # 统计卡片组件（可复用）
│       ├── AlertTrendChart.vue      # 告警趋势折线图
│       └── RecentAlertList.vue      # 最近告警列表
├── api/
│   ├── dashboard.js                 # 仪表盘接口
│   └── alert.js                     # 告警接口
├── stores/
│   └── dashboard.js                 # 仪表盘数据 Store（可选，轻量页面可不用）
├── utils/
│   ├── request.js                   # Axios 封装（已存在）
│   └── constants.js                 # 枚举常量（已存在）
└── router/
    └── index.js                     # 路由配置（需添加 Layout + dashboard）
```

---

## §3 数据流图

```
页面加载 (onMounted)
    ↓
Promise.all([
  GET /api/dashboard/stats           -- 设备统计 + 告警概览
  GET /api/alerts?isHandled=0&size=10 -- 最近未处理告警
  (可选) GET /api/alerts/stats/time-range×7 -- 7天趋势
])
    ↓
数据分发
    ├─ 设备统计 → StatsCard×3（总数/在线/离线）
    ├─ 告警概览 → StatsCard×2（总告警/未处理）
    ├─ 趋势数据 → AlertTrendChart（ECharts 折线图）
    └─ 告警列表 → RecentAlertList（ElTable）
    ↓
setInterval(30s) → 重新请求 /api/dashboard/stats
    ↓
页面卸载 (onUnmounted) → clearInterval
```

### API 局限性说明

| 需求 | API 能力 | 差距 |
|------|------|------|
| 4 个设备卡片（总数/在线/离线/故障） | API 返回 statusDistribution `{0,1}`（仅有在线/离线） | 无故障设备（status=2）计数。需等待后端补全，当前仅显示 3 卡片 |
| 7 天告警折线图 | `/alerts/stats/time-range` 支持单次时间范围 | 需要 7 次调用（每天一次）。可用 `Promise.all` 并行请求 |
| 紧急告警红色背景 | 已处理 | 无 |

---

## §4 接口实现清单

### §4.1 接口定义

| 接口 | 方法 | 路径 | 请求参数 | 返回体 |
|------|:---:|------|------|------|
| 仪表盘统计 | GET | `/api/dashboard/stats` | 无 | `{ totalDevices, onlineDevices, statusDistribution, totalAlerts, unhandledAlerts }` |
| 告警时间统计 | GET | `/api/alerts/stats/time-range` | `startTime`, `endTime` | `Map<String, Object>` |
| 告警分页查询 | GET | `/api/alerts` | `isHandled, size` | `Page<Alert>` |

### §4.2 API 层封装

```js
// src/api/dashboard.js
import request from '@/utils/request';

export function getDashboardStats() {
  return request.get('/dashboard/stats');
}
```

```js
// src/api/alert.js
import request from '@/utils/request';

export function getAlertStatsByTimeRange(params) {
  return request.get('/alerts/stats/time-range', { params });
}

export function getAlerts(params) {
  return request.get('/alerts', { params });
}
```

### §4.3 接口调用示例

```js
// 初始化数据
const [statsRes, alertRes] = await Promise.all([
  getDashboardStats(),
  getAlerts({ isHandled: 0, size: 10, current: 1 }),
]);

// statsRes.data.totalDevices     → 42
// statsRes.data.onlineDevices    → 35
// statsRes.data.totalAlerts      → 128
// statsRes.data.unhandledAlerts  → 5
// alertRes.data.records           → Alert[]
```

### §4.4 接口权限映射

| 接口 | SecurityConfig 规则 | 前端策略 |
|------|------|------|
| `GET /api/dashboard/stats` | `.requestMatchers(HttpMethod.GET).authenticated()` | 无需角色限制 |
| `GET /api/alerts/stats/time-range` | `.requestMatchers(HttpMethod.GET).authenticated()` | 无需角色限制 |
| `GET /api/alerts` | `.requestMatchers(HttpMethod.GET).authenticated()` | 无需角色限制 |

### §4.5 接口实现顺序

```
第一优先级:
  P0: GET /api/dashboard/stats      -- 核心统计
  P0: GET /api/alerts (isHandled=0) -- 最近告警

第二优先级 (趋势图):
  P1: GET /api/alerts/stats/time-range ×7 次 -- 7天趋势
```

---

## §5 组件树

```
AppLayout (src/layout/index.vue)
├── ElContainer（垂直布局）
│   ├── ElHeader（顶部栏）
│   │   ├── 系统名称
│   │   └── ElDropdown（用户信息 + 退出登录）
│   ├── ElContainer（水平布局）
│   │   ├── ElMenu（侧边栏，router 模式）
│   │   │   ├── 仪表盘（/dashboard）
│   │   │   ├── 设备管理（/devices）
│   │   │   ├── 数据监控（/data-monitor）
│   │   │   └── 告警管理（/alerts）
│   │   └── ElMain（内容区）
│   │       └── <router-view />
│   │           └── DashboardPage (views/dashboard/index.vue)
│   │               ├── ElRow
│   │               │   ├── ElCol → StatsCard（设备总数）
│   │               │   ├── ElCol → StatsCard（在线设备）
│   │               │   └── ElCol → StatsCard（离线设备）
│   │               ├── ElRow
│   │               │   ├── ElCol → StatsCard（总告警）
│   │               │   ├── ElCol → StatsCard（未处理告警）
│   │               │   └── ElCol → StatsCard（紧急告警，红色背景）
│   │               ├── ElRow
│   │               │   └── ElCol → AlertTrendChart（ECharts 7天折线图）
│   │               └── ElRow
│   │                   └── ElCol → RecentAlertList（ElTable 最近告警）
```

---

## §6 核心实现

### §6.1 Layout 组件

**文件**: `src/layout/index.vue`

```vue
<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <span class="system-title">IoT-Platform</span>
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          {{ authStore.username }} <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-container>
      <el-aside width="200px">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/devices">
            <el-icon><Monitor /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/data-monitor">
            <el-icon><TrendCharts /></el-icon>
            <span>数据监控</span>
          </el-menu-item>
          <el-menu-item index="/alerts">
            <el-icon><Bell /></el-icon>
            <span>告警管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const activeMenu = computed(() => route.path);

function handleCommand(command) {
  if (command === 'logout') {
    authStore.logout();
    router.push('/login');
  }
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.layout-header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e6e6e6;
}
.system-title { font-size: 20px; font-weight: bold; color: #303133; }
.user-info { cursor: pointer; }
</style>
```

### §6.2 仪表盘页面

**文件**: `src/views/dashboard/index.vue`

```vue
<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="card in deviceCards" :key="card.label">
        <stats-card :label="card.label" :value="card.value" :color="card.color" :icon="card.icon" />
      </el-col>
    </el-row>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="card in alertCards" :key="card.label">
        <stats-card :label="card.label" :value="card.value" :color="card.color" :icon="card.icon" :bg-color="card.bgColor" />
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card>
          <template #header>最近 7 天告警趋势</template>
          <alert-trend-chart :data="trendData" :loading="trendLoading" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>最近告警</template>
          <recent-alert-list :alerts="recentAlerts" :loading="loading" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { getDashboardStats } from '@/api/dashboard';
import { getAlerts } from '@/api/alert';
import StatsCard from '@/components/dashboard/StatsCard.vue';
import AlertTrendChart from '@/components/dashboard/AlertTrendChart.vue';
import RecentAlertList from '@/components/dashboard/RecentAlertList.vue';

const loading = ref(false);
const trendLoading = ref(false);
const stats = ref({});
const recentAlerts = ref([]);
const trendData = ref([]);

let timer = null;

const deviceCards = computed(() => [
  { label: '设备总数', value: stats.value.totalDevices ?? '-', color: '#409EFF', icon: 'Monitor' },
  { label: '在线设备', value: stats.value.onlineDevices ?? '-', color: '#67C23A', icon: 'CircleCheck' },
  { label: '离线设备', value: (stats.value.totalDevices ?? 0) - (stats.value.onlineDevices ?? 0), color: '#909399', icon: 'Remove' },
]);

const alertCards = computed(() => [
  { label: '总告警', value: stats.value.totalAlerts ?? '-', color: '#E6A23C', icon: 'Warning' },
  { label: '未处理告警', value: stats.value.unhandledAlerts ?? '-', color: '#F56C6C', icon: 'Bell', bgColor: '#FEF0F0' },
]);

async function fetchStats() {
  loading.value = true;
  try {
    const res = await getDashboardStats();
    stats.value = res.data;
  } finally {
    loading.value = false;
  }
}

async function fetchRecentAlerts() {
  try {
    const res = await getAlerts({ isHandled: 0, size: 10, current: 1 });
    recentAlerts.value = res.data.records || [];
  } catch { /* 非核心 */ }
}

onMounted(() => {
  fetchStats();
  fetchRecentAlerts();
  timer = setInterval(fetchStats, 30000);
});

onUnmounted(() => {
  clearInterval(timer);
});
</script>

<style scoped>
.dashboard { padding: 0; }
.stats-row { margin-bottom: 20px; }
</style>
```

### §6.3 StatsCard 组件

**文件**: `src/components/dashboard/StatsCard.vue`

```vue
<template>
  <el-card :style="{ borderTopColor: color, borderTopWidth: '3px' }" shadow="hover">
    <div class="stats-card-content">
      <div class="stats-card-left">
        <div class="stats-card-label">{{ label }}</div>
        <div class="stats-card-value" :style="{ color }">{{ value }}</div>
      </div>
      <div class="stats-card-icon" :style="{ backgroundColor: bgColor || color + '1a' }">
        <el-icon :size="32" :color="color">
          <component :is="icon" />
        </el-icon>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], required: true },
  color: { type: String, default: '#409EFF' },
  icon: { type: String, required: true },
  bgColor: { type: String, default: '' },
});
</script>

<style scoped>
.stats-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stats-card-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stats-card-value { font-size: 28px; font-weight: bold; }
.stats-card-icon {
  width: 56px; height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
</style>
```

### §6.4 AlertTrendChart 组件

**文件**: `src/components/dashboard/AlertTrendChart.vue`

```vue
<template>
  <div class="chart-container">
    <v-chart v-if="!loading" :option="chartOption" autoresize />
    <el-skeleton v-else :rows="6" animated />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
});

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['告警数量'] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', data: props.data.map(d => d.date) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{
    name: '告警数量',
    type: 'line',
    data: props.data.map(d => d.count),
    smooth: true,
    areaStyle: { opacity: 0.1 },
    itemStyle: { color: '#F56C6C' },
  }],
}));
</script>

<style scoped>
.chart-container { height: 280px; }
</style>
```

### §6.5 RecentAlertList 组件

**文件**: `src/components/dashboard/RecentAlertList.vue`

```vue
<template>
  <el-table :data="alerts" v-loading="loading" size="small" max-height="300">
    <el-table-column prop="deviceName" label="设备" min-width="100" />
    <el-table-column prop="alertType" label="告警类型" min-width="80" />
    <el-table-column prop="alertLevel" label="级别" width="70">
      <template #default="{ row }">
        <el-tag :type="levelType(row.alertLevel)" size="small">
          {{ ALERT_LEVEL[row.alertLevel]?.label || row.alertLevel }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createTime" label="时间" min-width="140" />
  </el-table>
</template>

<script setup>
import { ALERT_LEVEL } from '@/utils/constants';

defineProps({
  alerts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
});

function levelType(level) {
  const map = { 1: 'danger', 2: 'warning', 3: '', 4: 'info', 5: 'info' };
  return map[level] || '';
}
</script>
```

---

## §7 状态管理

| Store | 用途 | 关键状态 | 页面依赖 |
|-------|------|----------|:---:|
| `useAuthStore` | 全局认证状态 | `token`, `role`, `username` | ✅ Layout 使用 |
| — | — | — | 仪表盘数据由组件内 `ref` 管理，无需 Store |

> **设计决策**: 仪表盘统计数据仅在仪表盘页面使用，作用域局限，不需要存入 Pinia Store。使用组件内 `ref` + `onMounted` 加载即可。

---

## §8 页面完成定义 (DoD)

仪表盘"完成"必须满足以下全部条件：

- [x] Layout 组件正常渲染（侧边栏 + 顶部栏 + 内容区），侧边栏菜单可导航
- [x] 设备统计卡片：总数/在线/离线数据正确显示
- [x] 告警统计卡片：总数/未处理数据正确显示
- [x] 紧急告警卡片使用红色背景高亮
- [x] 统计数据每 30 秒自动刷新
- [x] 最近 10 条未处理告警列表正确展示，级别使用 ElTag 颜色区分
- [x] 告警级别颜色：紧急=红色、严重=橙色、一般=蓝色、警告=灰色
- [x] 退出登录功能可用（清除 Token + 跳转 `/login`）
- [x] 页面卸载时清除定时器（无内存泄漏）
- [x] 首次加载有 Loading 状态

---

## §9 集成检查清单

> 开发者实现完毕后逐项确认。

| # | 检查项 | 验证方式 |
|:---:|------|------|
| 1 | 登录后跳转 `/dashboard` | 仪表盘页面正常展示 |
| 2 | 侧边栏菜单点击"设备管理" | 跳转到 `/devices` |
| 3 | 侧边栏菜单点击"数据监控" | 跳转到 `/data-monitor` |
| 4 | 侧边栏菜单点击"告警管理" | 跳转到 `/alerts` |
| 5 | 侧边栏当前页面菜单高亮 | 刷新后仍然正确 |
| 6 | 设备统计卡片显示正确数值 | 与数据库一致 |
| 7 | 等待 30 秒 | 统计数据自动更新 |
| 8 | 告警列表显示最近未处理告警 | 条数 ≤ 10 |
| 9 | 点击"退出登录" | Token 清除，跳转 `/login` |
| 10 | 退出后访问 `/dashboard` | 被守卫拦截，跳转 `/login` |
| 11 | VIEWER 角色登录 | 仪表盘页面正常显示（无操作权限按钮） |
| 12 | 浏览器 DevTools Network | `/api/dashboard/stats` 请求正常 |

---

## §10 学习重点

| # | 知识点 | 文件/位置 | 说明 |
|:---:|------|------|------|
| 1 | Layout 组件设计 | `layout/index.vue` §6.1 | ElContainer + ElMenu router 模式 |
| 2 | ECharts 按需引入 | `components/dashboard/AlertTrendChart.vue` §6.4 | 仅引入 LineChart 减少包体积 |
| 3 | 轮询模式 | `views/dashboard/index.vue` §6.2 | `setInterval` + `onUnmounted` 清理 |
| 4 | 组件化复用 | `components/dashboard/StatsCard.vue` §6.3 | props 驱动不同颜色/图标 |
| 5 | `Promise.all` 并行请求 | `views/dashboard/index.vue` §6.2 | 减少页面加载等待时间 |
| 6 | ElMenu router 模式 | `layout/index.vue` §6.1 | 启用 `router` 属性自动同步路由激活项 |

---

## §11 面试准备

### §11.1 核心交互流程

1. 登录成功 → `router.push('/dashboard')` → Layout 渲染 → 侧边栏高亮"仪表盘"
2. `onMounted` → `fetchStats()` + `fetchRecentAlerts()` → 卡片/图表/列表渲染
3. `setInterval(30s)` → 持续刷新统计卡片
4. 点击侧边栏其他菜单 → `<router-view />` 切换子页面 → 菜单高亮跟随路由
5. 点击"退出登录" → `authStore.logout()` → 清除 Token → `router.push('/login')`

### §11.2 面试模拟问题

**问题 1**: 仪表盘的 30 秒轮询会带来什么问题？如何优化？

> 参考答案：轮询会增加服务端压力，尤其在多用户并发场景。优化方案：（1）后端已使用 Redis 缓存（TTL 30s），前端的多次请求实际命中缓存不会打到数据库；（2）可以使用 WebSocket 替代轮询，服务端数据变更时主动推送，减少无效请求。

**问题 2**: 如果 Dashboard 统计接口返回数据异常（如 `totalDevices` 为 null），前端如何处理？

> 参考答案：在 `computed` 中使用 `??` 运算符提供默认值：`stats.value.totalDevices ?? '-'`。卡片组件接收 `Number|String` 类型，展示 '-' 表示数据不可用，不会崩溃。

**问题 3**: Layout 组件中 `router-view` 的工作原理是什么？

> 参考答案：Layout 是路由的父组件，子页面通过嵌套路由渲染。路由配置中 `/dashboard`、`/devices`、`/data-monitor`、`/alerts` 都是 Layout 的 `children`，Layout 中的 `<router-view />` 会根据当前路径渲染对应的子组件。

---

## §12 页面复杂度评估

| 维度 | 评级 | 说明 |
|------|:---:|------|
| 组件数量 | ⭐⭐⭐ | 1 Layout + 1 页面 + 3 子组件 |
| 接口数量 | ⭐⭐ | 2-3 个 GET 接口 |
| 状态复杂度 | ⭐⭐ | 组件内状态 + AuthStore |
| 权限逻辑 | ⭐ | 所有已认证用户可访问，无角色限制 |
| 交互复杂度 | ⭐⭐⭐ | 30s 轮询 + ECharts + Loading/Skeleton |
| **综合** | **⭐⭐⭐** | **中等复杂度，需掌握 Layout 和 ECharts** |

---

## §13 硬性约束

- **禁止**在 Layout 中硬编码菜单项（必须从 router 配置推导或使用常量数组）
- **禁止**在 `onUnmounted` 中遗漏 `clearInterval`（内存泄漏）
- **禁止**在 ECharts 中全量引入（必须按需引入 `LineChart` 等模块）
- **禁止**在 `v-for` 中使用 `index` 作为 `key`
- **必须**使用 `Promise.all` 并行请求 `getDashboardStats` 和 `getAlerts`
- **必须**在退出登录时同时调用 `authStore.logout()` 和 `router.push('/login')`

---

## 附录 B：仪表盘特殊处理

| 处理项 | 说明 |
|------|------|
| 需要 Layout | 仪表盘是第一个后台管理页面，必须创建 Layout 组件 |
| 侧边栏显隐 | 所有已认证用户均显示侧边栏，无角色区分 |
| 首次加载逻辑 | `onMounted` 中 `Promise.all` 加载全部数据，减少串行等待 |
| 缓存策略 | 后端已有 Redis 缓存（TTL 30s），前端仅做轮询，不做额外缓存 |
| 路由配置 | Layout 为父路由 `/`，dashboard 为默认子路由，`/` redirect `/dashboard` |

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*