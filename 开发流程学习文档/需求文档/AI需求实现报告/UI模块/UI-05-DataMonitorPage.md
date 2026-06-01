# 数据监控 — 前端实现方案

> **版本**: V1.0.0 | **创建**: 2026-05-31 | **需求**: REQ-UI-FR-004 / REQ-DAT-FR-002
> **关联基础设施**: UI-00-InfrastructureREADME.md
> **关联规范**: FrontendPageImplementationPrompt.md V3.1.1
> **页面类型**: 后台管理数据展示页面（含 ECharts + 设备选择器）

---

## §A 实现边界（必须首先声明）

本页面**仅实现**需求文档明确要求的功能。

**禁止主动增加以下内容**（除非需求文档明确要求）：
- 国际化 (i18n)
- 虚拟滚动 / 无限滚动
- 微前端 / SSR / PWA
- WebWorker / Service Worker
- WebSocket 实时数据推送（当前使用 API 查询）
- 数据对比功能（多设备同屏对比）
- 数据导出（REQ-DAT-FR-003 为 P2，后续单独迭代）
- 自定义仪表盘
- 深色模式

---

## §0 基础设施约定

全部引用 `UI-00-InfrastructureREADME.md`，本章仅补充数据监控特化内容。

### §0.1 技术栈

同 UI-00-InfrastructureREADME §0.1，数据监控额外使用：

| 技术 | 版本 | 用途 |
|------|:---:|------|
| ECharts | 5.x | 趋势折线图 |
| vue-echarts | 6.x | ECharts Vue 封装 |

### §0.2 后端接口

| 接口 | 方法 | 实际路径 | 认证 | 说明 |
|------|:---:|------|:---:|------|
| 设备列表 | GET | `/api/devices` | 已认证 | 左侧设备选择器数据源 |
| 设备历史数据 | GET | `/api/device-data/device/{deviceId}` | 已认证 | 分页查询，`current`, `size`(默认20) |
| 设备最新数据 | GET | `/api/device-data/device/{deviceId}/latest` | 已认证 | 最新一条采集数据 |
| 时间段数据 | GET | `/api/device-data/device/{deviceId}/range` | 已认证 | `startTime`, `endTime` 必填，时间跨度 ≤ 30天 |
| 统计数据 | GET | `/api/device-data/device/{deviceId}/statistics` | 已认证 | `startTime`, `endTime` → `{minVal, maxVal, avgVal, count}` |

### §0.3 Axios 拦截器

同 UI-00-InfrastructureREADME §0.3。

### §0.4 路由守卫

同 UI-00-InfrastructureREADME §0.4。

### §0.5 按钮级权限

数据监控页面为所有已认证用户可见的只读页面，无按钮级权限控制。

### §0.6 RBAC 权限矩阵

| 端点 | ADMIN | OPERATOR | VIEWER |
|:---|:---:|:---:|:---:|
| `GET /api/devices` | ✅ | ✅ | ✅ |
| `GET /api/device-data/device/{deviceId}` | ✅ | ✅ | ✅ |
| `GET /api/device-data/device/{deviceId}/latest` | ✅ | ✅ | ✅ |
| `GET /api/device-data/device/{deviceId}/range` | ✅ | ✅ | ✅ |
| `GET /api/device-data/device/{deviceId}/statistics` | ✅ | ✅ | ✅ |

### §0.7 枚举映射表

同 UI-00-InfrastructureREADME §0.7，数据监控使用 `DEVICE_STATUS` 标记设备在线状态。

### §0.8 输入缺失处理

- ✅ `ControllerAPIREADME.md` — 已读取
- ✅ `IoT-Platform需求规格说明书.md` — 已读取
- ✅ `SecurityConfig.java` — 已读取

---

## §1 页面任务拆解

### P0 — 核心功能（必须完成，缺一则页面不可交付）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P0-1** | 左侧设备列表（搜索 + 选择 + 状态标记） | 中 |
| **P0-2** | 选中设备后加载最新数据 + 历史数据列表（分页 20 条/页） | 中 |
| **P0-3** | 历史数据分页表格（值/采集时间） | 低 |
| **P0-4** | 时间段快捷选择（1h/6h/24h/7d/30d） | 低 |

### P1 — 重要功能（提升用户体验）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P1-1** | ECharts 趋势折线图（按时间段渲染） | 中 |
| **P1-2** | 统计面板（min/max/avg/count） | 低 |
| **P1-3** | 最新数据高亮展示 | 低 |

### P2 — 增量优化（可选）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P2-1** | 趋势图自动刷新（30s） | 低 |
| **P2-2** | 多指标数据展示（按 metricKey 分类） | 中 |

---

## §2 目录结构

```
src/
├── views/
│   └── data-monitor/
│       └── index.vue                      # 数据监控入口
├── components/
│   └── data-monitor/
│       ├── DeviceSidebar.vue              # 左侧设备选择面板
│       ├── DataTrendChart.vue             # ECharts 趋势折线图
│       ├── DataStatsPanel.vue             # 统计面板（min/max/avg）
│       └── DataTable.vue                  # 历史数据分页表格
├── api/
│   ├── device.js                          # 设备接口（已存在）
│   └── deviceData.js                      # 设备数据接口
├── utils/
│   ├── request.js                         # Axios 封装（已存在）
│   └── constants.js                       # 枚举常量（已存在）
└── router/
    └── index.js                           # 路由配置（已存在 /data-monitor）
```

---

## §3 数据流图

```
页面加载 (onMounted)
    ↓
fetchDeviceList() → 渲染左侧 DeviceSidebar
    ↓
用户选择设备 (click)
    ↓
selectedDeviceId 变化 (watch)
    ↓
Promise.all([
  GET /device-data/device/{id}/latest          -- 最新数据
  GET /device-data/device/{id}?current=1&size=20 -- 历史数据第一页
])
    ↓
渲染 DataTable + 最新数据面板
    ↓
用户选择时间范围 (1h/6h/24h/7d/30d)
    ↓
Promise.all([
  GET /device-data/device/{id}/range?startTime=&endTime=  -- 趋势图数据
  GET /device-data/device/{id}/statistics?startTime=&endTime= -- 统计
])
    ↓
渲染 DataTrendChart + DataStatsPanel
    ↓
分页切换
    ↓
GET /device-data/device/{id}?current=X&size=20
    ↓
DataTable 更新
```

---

## §4 接口实现清单

### §4.1 接口定义

| 接口 | 方法 | 路径 | 主要参数 | 返回体 |
|------|:---:|------|------|------|
| 设备列表 | GET | `/api/devices` | 无 | `List<Device>` |
| 最新数据 | GET | `/api/device-data/device/{deviceId}/latest` | `deviceId` (path) | `DeviceData` |
| 历史数据 | GET | `/api/device-data/device/{deviceId}` | `current`, `size`(默认20) | `Page<DeviceData>` |
| 时间段数据 | GET | `/api/device-data/device/{deviceId}/range` | `startTime`, `endTime` | `List<DeviceData>` |
| 统计数据 | GET | `/api/device-data/device/{deviceId}/statistics` | `startTime`, `endTime` | `{minVal, maxVal, avgVal, count}` |

### §4.2 API 层封装

```js
// src/api/deviceData.js
import request from '@/utils/request';

export function getDeviceData(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}`, { params });
}

export function getDeviceLatestData(deviceId) {
  return request.get(`/device-data/device/${deviceId}/latest`);
}

export function getDeviceDataByRange(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}/range`, { params });
}

export function getDeviceDataStatistics(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}/statistics`, { params });
}
```

```js
// src/api/device.js（补充）
import request from '@/utils/request';

export function getAllDevices() {
  return request.get('/devices');
}
```

### §4.3 接口调用示例

```js
// 选中设备后加载
const [latestRes, historyRes] = await Promise.all([
  getDeviceLatestData(selectedDeviceId),
  getDeviceData(selectedDeviceId, { current: 1, size: 20 }),
]);

// 时间范围选择后
const now = new Date();
const rangeMap = {
  '1h': 60 * 60 * 1000,
  '6h': 6 * 60 * 60 * 1000,
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
};
const startTime = formatDate(new Date(now - rangeMap[selectedRange]));
const endTime = formatDate(now);

const [rangeRes, statsRes] = await Promise.all([
  getDeviceDataByRange(selectedDeviceId, { startTime, endTime }),
  getDeviceDataStatistics(selectedDeviceId, { startTime, endTime }),
]);
```

### §4.4 接口权限映射

| 接口 | SecurityConfig 规则 | 前端策略 |
|------|------|------|
| `GET /api/devices` | `HttpMethod.GET → authenticated()` | 无需角色限制 |
| `GET /api/device-data/device/{id}/**` | `HttpMethod.GET → authenticated()` | 无需角色限制 |

### §4.5 接口实现顺序

```
第一优先级:
  P0: GET /api/devices                                 -- 设备列表
  P0: GET /api/device-data/device/{id}/latest           -- 最新数据
  P0: GET /api/device-data/device/{id}?current=&size=20 -- 历史数据

第二优先级:
  P1: GET /api/device-data/device/{id}/range            -- 趋势图
  P1: GET /api/device-data/device/{id}/statistics       -- 统计面板
```

---

## §5 组件树

```
DataMonitorPage (views/data-monitor/index.vue)
├── ElContainer（水平布局）
│   ├── ElAside（左侧：width=260px）
│   │   └── DeviceSidebar（设备选择面板）
│   │       ├── ElInput（设备搜索）
│   │       └── ElMenu / ElList（设备列表，含在线状态小圆点）
│   └── ElMain（右侧：数据展示区）
│       ├── 最新数据卡片（ElCard）
│       │   ├── 数据值（大字号高亮）
│       │   └── 采集时间
│       ├── 时间范围选择器
│       │   └── ElRadioGroup（1h/6h/24h/7d/30d）
│       ├── DataStatsPanel（统计面板）
│       │   ├── ElRow → ElCol × 4（min/max/avg/count）
│       ├── DataTrendChart（ECharts 折线图）
│       │   └── v-chart
│       └── DataTable（历史数据表格）
│           ├── ElTable
│           │   ├── 数据值
│           │   ├── 采集时间
│           └── ElPagination
```

---

## §6 核心实现

### §6.1 数据监控页面入口

**文件**: `src/views/data-monitor/index.vue`

```vue
<template>
  <div class="data-monitor-page">
    <el-container>
      <el-aside width="260px" class="device-aside">
        <device-sidebar
          :devices="deviceList"
          :selected-id="selectedDeviceId"
          :loading="deviceLoading"
          @select="handleDeviceSelect"
        />
      </el-aside>
      <el-main class="data-main">
        <template v-if="selectedDeviceId">
          <el-card class="latest-card">
            <template #header>最新数据</template>
            <div class="latest-content">
              <span class="latest-value" :style="{ color: latestColor }">
                {{ latestData?.dataValue ?? '--' }}
              </span>
              <span class="latest-time">
                采集时间: {{ latestData?.collectTime || '--' }}
              </span>
            </div>
          </el-card>

          <div class="range-selector">
            <span class="range-label">时间范围：</span>
            <el-radio-group v-model="selectedRange" @change="handleRangeChange">
              <el-radio-button value="1h">1小时</el-radio-button>
              <el-radio-button value="6h">6小时</el-radio-button>
              <el-radio-button value="24h">24小时</el-radio-button>
              <el-radio-button value="7d">7天</el-radio-button>
              <el-radio-button value="30d">30天</el-radio-button>
            </el-radio-group>
          </div>

          <data-stats-panel :stats="stats" :loading="statsLoading" />

          <el-card>
            <data-trend-chart :data="trendData" :loading="trendLoading" />
          </el-card>

          <el-card>
            <template #header>历史数据</template>
            <data-table
              :data="historyData"
              :loading="historyLoading"
              :total="historyTotal"
              :current-page="historyPage.current"
              :page-size="historyPage.size"
              @page-change="handleHistoryPageChange"
            />
          </el-card>
        </template>
        <el-empty v-else description="请在左侧选择设备" />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { getAllDevices } from '@/api/device';
import {
  getDeviceLatestData,
  getDeviceData,
  getDeviceDataByRange,
  getDeviceDataStatistics,
} from '@/api/deviceData';
import DeviceSidebar from '@/components/data-monitor/DeviceSidebar.vue';
import DataTrendChart from '@/components/data-monitor/DataTrendChart.vue';
import DataStatsPanel from '@/components/data-monitor/DataStatsPanel.vue';
import DataTable from '@/components/data-monitor/DataTable.vue';

const deviceList = ref([]);
const deviceLoading = ref(false);
const selectedDeviceId = ref(null);

const latestData = ref(null);
const latestColor = ref('#67C23A');

const selectedRange = ref('1h');

const trendData = ref([]);
const trendLoading = ref(false);

const stats = ref({});
const statsLoading = ref(false);

const historyData = ref([]);
const historyLoading = ref(false);
const historyTotal = ref(0);
const historyPage = ref({ current: 1, size: 20 });

function handleDeviceSelect(deviceId) {
  selectedDeviceId.value = deviceId;
}

watch(selectedDeviceId, async (id) => {
  if (!id) return;
  historyPage.value.current = 1;
  await Promise.all([
    fetchLatestData(id),
    fetchHistoryData(id),
    fetchTrendAndStats(id),
  ]);
});

async function fetchLatestData(deviceId) {
  try {
    const res = await getDeviceLatestData(deviceId);
    latestData.value = res.data;
  } catch { /* 设备可能无数据 */ }
}

async function fetchHistoryData(deviceId) {
  historyLoading.value = true;
  try {
    const res = await getDeviceData(deviceId, {
      current: historyPage.value.current,
      size: historyPage.value.size,
    });
    historyData.value = res.data.records || [];
    historyTotal.value = res.data.total || 0;
  } finally {
    historyLoading.value = false;
  }
}

function getRangeMs() {
  const map = { '1h': 3600000, '6h': 21600000, '24h': 86400000, '7d': 604800000, '30d': 2592000000 };
  return map[selectedRange.value] || 3600000;
}

function formatDateTime(date) {
  const pad = n => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

async function fetchTrendAndStats(deviceId) {
  if (!deviceId) return;

  const endTime = new Date();
  const startTime = new Date(endTime - getRangeMs());

  const params = {
    startTime: formatDateTime(startTime),
    endTime: formatDateTime(endTime),
  };

  trendLoading.value = true;
  statsLoading.value = true;

  try {
    const [rangeRes, statsRes] = await Promise.all([
      getDeviceDataByRange(deviceId, params),
      getDeviceDataStatistics(deviceId, params),
    ]);
    trendData.value = rangeRes.data || [];
    stats.value = statsRes.data || {};
  } catch {
    trendData.value = [];
    stats.value = {};
  } finally {
    trendLoading.value = false;
    statsLoading.value = false;
  }
}

async function handleRangeChange() {
  await fetchTrendAndStats(selectedDeviceId.value);
}

async function handleHistoryPageChange(page) {
  historyPage.value.current = page;
  await fetchHistoryData(selectedDeviceId.value);
}

onMounted(async () => {
  deviceLoading.value = true;
  try {
    const res = await getAllDevices();
    deviceList.value = res.data || [];
  } finally {
    deviceLoading.value = false;
  }
});
</script>

<style scoped>
.data-monitor-page { height: calc(100vh - 120px); }
.device-aside {
  border-right: 1px solid #e6e6e6;
  background: #fafafa;
  overflow-y: auto;
}
.data-main { padding-top: 0; }
.latest-card { margin-bottom: 16px; }
.latest-content {
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.latest-value { font-size: 36px; font-weight: bold; }
.latest-time { color: #909399; font-size: 14px; }
.range-selector {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
.range-label { margin-right: 12px; color: #606266; }
</style>
```

### §6.2 DeviceSidebar 组件

**文件**: `src/components/data-monitor/DeviceSidebar.vue`

```vue
<template>
  <div class="device-sidebar">
    <el-input
      v-model="searchKeyword"
      placeholder="搜索设备"
      clearable
      class="search-input"
    />
    <div class="device-list" v-loading="loading">
      <div
        v-for="device in filteredDevices"
        :key="device.id"
        class="device-item"
        :class="{ active: device.id === selectedId }"
        @click="$emit('select', device.id)"
      >
        <span
          class="device-status-dot"
          :style="{ backgroundColor: statusColor(device.status) }"
        />
        <span class="device-name">{{ device.deviceName }}</span>
      </div>
      <el-empty v-if="filteredDevices.length === 0 && !loading" description="无设备" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { DEVICE_STATUS } from '@/utils/constants';

const props = defineProps({
  devices: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  loading: { type: Boolean, default: false },
});

defineEmits(['select']);

const searchKeyword = ref('');

const filteredDevices = computed(() => {
  if (!searchKeyword.value) return props.devices;
  const kw = searchKeyword.value.toLowerCase();
  return props.devices.filter(d => d.deviceName?.toLowerCase().includes(kw));
});

function statusColor(status) {
  const map = { 0: '#909399', 1: '#67C23A', 2: '#F56C6C' };
  return map[status] || '#909399';
}
</script>

<style scoped>
.device-sidebar { padding: 12px; }
.search-input { margin-bottom: 12px; }
.device-list { max-height: calc(100vh - 200px); overflow-y: auto; }
.device-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s;
}
.device-item:hover { background: #ecf5ff; }
.device-item.active { background: #409EFF; color: #fff; }
.device-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  flex-shrink: 0;
}
.device-name { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
```

### §6.3 DataTrendChart 组件

**文件**: `src/components/data-monitor/DataTrendChart.vue`

```vue
<template>
  <div class="chart-wrapper">
    <v-chart v-if="!loading && data.length > 0" :option="chartOption" autoresize />
    <el-empty v-else-if="!loading" description="暂无趋势数据" />
    <el-skeleton v-else :rows="8" animated />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
});

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    data: props.data.map(d => d.collectTime?.slice(5, 16) || ''),
  },
  yAxis: { type: 'value' },
  series: [{
    type: 'line',
    data: props.data.map(d => d.dataValue),
    smooth: true,
    symbol: 'none',
    lineStyle: { color: '#409EFF', width: 2 },
    areaStyle: { color: 'rgba(64, 158, 255, 0.1)' },
  }],
}));
</script>

<style scoped>
.chart-wrapper { height: 320px; }
</style>
```

### §6.4 DataStatsPanel 组件

**文件**: `src/components/data-monitor/DataStatsPanel.vue`

```vue
<template>
  <el-row :gutter="16" class="stats-panel" v-loading="loading">
    <el-col :span="6" v-for="item in statItems" :key="item.label">
      <el-card shadow="hover">
        <div class="stat-item">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value" :style="{ color: item.color }">{{ item.value }}</div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  stats: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
});

const statItems = computed(() => [
  { label: '最小值', value: props.stats.minVal ?? '--', color: '#67C23A' },
  { label: '最大值', value: props.stats.maxVal ?? '--', color: '#F56C6C' },
  { label: '平均值', value: props.stats.avgVal != null ? Number(props.stats.avgVal).toFixed(2) : '--', color: '#409EFF' },
  { label: '数据条数', value: props.stats.count ?? '--', color: '#909399' },
]);
</script>

<style scoped>
.stats-panel { margin-bottom: 16px; }
.stat-item { text-align: center; }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: bold; }
</style>
```

### §6.5 DataTable 组件

**文件**: `src/components/data-monitor/DataTable.vue`

```vue
<template>
  <div>
    <el-table :data="data" v-loading="loading" size="small">
      <el-table-column prop="dataValue" label="数据值" min-width="120">
        <template #default="{ row }">
          <span class="data-value-cell">{{ row.dataValue }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="collectTime" label="采集时间" min-width="180" />
    </el-table>
    <el-pagination
      v-if="total > 0"
      v-model:current-page="current"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[20]"
      layout="total, prev, pager, next"
      class="data-pagination"
      @current-change="page => $emit('page-change', page)"
    />
  </div>
</template>

<script setup>
defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
});

defineEmits(['page-change']);

const current = defineModel('currentPage');
</script>

<style scoped>
.data-value-cell { font-weight: bold; color: #303133; }
.data-pagination { margin-top: 16px; justify-content: flex-end; }
</style>
```

---

## §7 状态管理

| Store | 用途 | 关键状态 | 页面依赖 |
|-------|------|----------|:---:|
| `useAuthStore` | 侧边栏用户信息（Layout 使用） | `username` | ✅ Layout 中显示 |
| — | — | — | 设备数据 + 选择状态由组件内 `ref`/`watch` 管理，无需 Store |

> **设计决策**: 数据监控页面状态全部局限于当前页面（设备选择、时间范围、数据结果），不存在跨页面共享需求，使用 Vue 组件内响应式状态即可。`watch(selectedDeviceId)` 是核心状态驱动模式。

---

## §8 页面完成定义 (DoD)

数据监控页"完成"必须满足以下全部条件：

- [x] 左侧设备列表正常加载，支持搜索过滤
- [x] 设备状态圆点颜色正确（在线=绿、离线=灰、故障=红）
- [x] 点击设备后右侧展示最新数据值（大字号）+ 采集时间
- [x] 历史数据分页表格展示，每页 20 条，按采集时间倒序
- [x] 时间范围 1h/6h/24h/7d/30d 快捷切换
- [x] 切换时间范围后趋势图和统计面板同步更新
- [x] 统计面板展示 min/max/avg/count
- [x] 趋势折线图 ECharts 正确渲染
- [x] 无设备选中时显示 `el-empty` 占位
- [x] 设备无数据时各面板显示占位（'--' 或空状态）

---

## §9 集成检查清单

| # | 检查项 | 验证方式 |
|:---:|------|------|
| 1 | 进入数据监控页 | 左侧设备列表正常加载 |
| 2 | 在搜索框输入设备名 | 列表过滤显示匹配设备 |
| 3 | 点击某个设备 | 右侧展示最新数据 + 历史数据表格 |
| 4 | 切换分页 | 历史数据表格刷新第二页 |
| 5 | 切换时间范围为"24小时" | 趋势图更新，统计面板更新 |
| 6 | 切换时间范围为"7天" | 趋势图 X 轴跨度变化 |
| 7 | 选择一个无数据的设备 | 各面板显示 "--" 或空状态 |
| 8 | 统计数据对比 | min/max/avg/count 与数据库一致 |
| 9 | 趋势图 ECharts 渲染 | 折线图流畅，X 轴时间标签不重叠 |
| 10 | 窗口缩放 | ECharts `autoresize` 自动适配 |
| 11 | VIEWER 角色登录 | 页面正常展示（无操作按钮） |

---

## §10 学习重点

| # | 知识点 | 文件/位置 | 说明 |
|:---:|------|------|------|
| 1 | 左右分栏布局 | `views/data-monitor/index.vue` §6.1 | ElContainer + ElAside + ElMain |
| 2 | Watch 驱动数据加载 | `views/data-monitor/index.vue` §6.1 | `watch(selectedDeviceId)` 自动触发多 API 并行请求 |
| 3 | 时间范围快捷切换 | `views/data-monitor/index.vue` §6.1 | ElRadioGroup + 毫秒映射表 |
| 4 | ECharts 按需引入 | `DataTrendChart.vue` §6.3 | 仅引入 LineChart/Grid/Tooltip |
| 5 | 空状态处理 | 整页 | 无设备 → 空占位 / 无数据 → "--" |
| 6 | 组件拆分粒度 | `components/data-monitor/` | DeviceSidebar/DataTrendChart/DataStatsPanel/DataTable 各司其职 |

---

## §11 面试准备

### §11.1 核心交互流程

1. 进入数据监控 → `onMounted` → 加载设备列表 → 左侧渲染
2. 点击设备 → `selectedDeviceId` 变化 → `watch` 触发 → 并行加载最新数据 + 历史数据 + 趋势+统计
3. 切换时间范围 → `handleRangeChange` → 重新计算 startTime → 并行加载趋势图 + 统计面板
4. 历史数据分页 → `handleHistoryPageChange` → 请求指定页 → 表格更新

### §11.2 面试模拟问题

**问题 1**: 为什么使用 `watch(selectedDeviceId)` 而不是在 `@click` 事件中直接调用加载方法？

> 参考答案：使用 `watch` 可以统一管理"选中设备变化"这个单一触发源，无论设备是通过点击列表、搜索后点击、还是通过 URL 参数恢复选中状态，都能自动触发数据加载。这是"声明式"而非"命令式"的编程风格。

**问题 2**: 时间范围选择后如何计算 `startTime` 和 `endTime`？如何处理时区问题？

> 参考答案：使用 JavaScript `new Date()` 获取当前时间作为 `endTime`，`startTime = endTime - rangeMs`。格式化时使用 `pad` 补零确保两位数。由于前后端在同一时区，使用本地时间 `formatDateTime` 输出 `YYYY-MM-DD HH:mm:ss` 格式即可。不建议使用 `toISOString()` 避免 UTC 时区偏差。

**问题 3**: 趋势图和统计面板的 API 调用为什么要用 `Promise.all` 而不是串行？

> 参考答案：两个接口互不依赖（`/range` 返回数据用于图表，`/statistics` 返回聚合用于统计面板），并行调用可将页面加载时间减半。如果串行则是 T(range) + T(stats)，并行是 max(T(range), T(stats))。

---

## §12 页面复杂度评估

| 维度 | 评级 | 说明 |
|------|:---:|------|
| 组件数量 | ⭐⭐⭐ | 1 页面 + 4 子组件 |
| 接口数量 | ⭐⭐⭐ | 4 个接口（devices/latest/history/range+stats） |
| 状态复杂度 | ⭐⭐⭐⭐ | 设备选择 + 时间范围 + 双数据面板状态 |
| 权限逻辑 | ⭐ | 所有已认证用户可访问，无角色限制 |
| 交互复杂度 | ⭐⭐⭐ | 设备切换联动 + 时间范围联动 + ECharts |
| **综合** | **⭐⭐⭐** | **中等复杂度，核心技术点为 Watch 驱动 + ECharts** |

---

## §13 硬性约束

- **禁止**在未选中设备时调用任何设备数据接口
- **禁止**在趋势图中全量引入 ECharts（必须 Tree Shaking 按需引入）
- **禁止**在 `formatDateTime` 中使用 `toISOString()`（会导致 UTC 时区偏移）
- **禁止**在时间范围切换时忘记同步更新统计面板
- **必须**在设备切换时重置分页到第一页
- **必须**在 `watch` 中使用 `async` + `await Promise.all` 减少加载时间
- **必须**为数据为 null/undefined 的情况提供 '--' 占位符

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*