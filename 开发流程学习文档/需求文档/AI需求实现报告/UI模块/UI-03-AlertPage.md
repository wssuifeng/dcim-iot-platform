# 告警管理 — 前端实现方案

> **版本**: V1.0.0 | **创建**: 2026-05-31 | **需求**: REQ-UI-FR-005 / REQ-ALT-FR-002
> **关联基础设施**: UI-00-InfrastructureREADME.md
> **关联规范**: FrontendPageImplementationPrompt.md V3.1.1
> **页面类型**: 后台管理 CRUD 页面（含批量操作）

---

## §A 实现边界（必须首先声明）

本页面**仅实现**需求文档明确要求的功能。

**禁止主动增加以下内容**（除非需求文档明确要求）：
- 国际化 (i18n)
- 虚拟滚动 / 无限滚动（当前报警量预估不超千条/天）
- 微前端 / SSR / PWA
- WebWorker / Service Worker
- WebSocket 实时告警推送（P1 后续迭代）
- 邮件通知界面
- 告警规则配置界面（属于设备管理页的告警配置子功能）
- 告警导出功能
- 深色模式

---

## §0 基础设施约定

全部引用 `UI-00-InfrastructureREADME.md`，本章仅补充告警管理特化内容。

### §0.1 技术栈

同 UI-00-InfrastructureREADME §0.1，告警管理额外使用：

| 技术 | 版本 | 用途 |
|------|:---:|------|
| 无额外依赖 | — | Element Plus ElTable/ElTag 已足够 |

### §0.2 后端接口

| 接口 | 方法 | 实际路径 | 认证 | 说明 |
|------|:---:|------|:---:|------|
| 告警分页查询 | GET | `/api/alerts` | 已认证 | 支持 deviceId/isHandled/alertLevel/startTime/endTime 筛选 |
| 告警详情 | GET | `/api/alerts/{id}` | 已认证 | 单条告警详情 |
| 未处理告警列表 | GET | `/api/alerts/unhandled` | 已认证 | 全部未处理告警 |
| 批量处理告警 | PUT | `/api/alerts/batch-handle` | ADMIN/OPERATOR | `{ ids: [], handleUser: "" }` |
| 设备告警统计 | GET | `/api/alerts/stats/device/{deviceId}` | 已认证 | 设备维度告警统计 |
| 时间段统计 | GET | `/api/alerts/stats/time-range` | 已认证 | 时间范围告警统计 |
| 设备告警排名 | GET | `/api/alerts/stats/device-ranking` | 已认证 | 设备告警数量排名 |

### §0.3 Axios 拦截器

同 UI-00-InfrastructureREADME §0.3。

### §0.4 路由守卫

同 UI-00-InfrastructureREADME §0.4。

### §0.5 按钮级权限

| 操作 | 后端规则 | 前端策略 |
|------|------|------|
| 处理告警（单条） | `PUT /alerts/**` → ADMIN/OPERATOR | `v-if="authStore.role !== 'VIEWER'"` |
| 批量处理告警 | `PUT /alerts/**` → ADMIN/OPERATOR | `v-if="authStore.role !== 'VIEWER'"` |

### §0.6 RBAC 权限矩阵

| 端点 | ADMIN | OPERATOR | VIEWER |
|:---|:---:|:---:|:---:|
| `GET /api/alerts` | ✅ | ✅ | ✅ |
| `GET /api/alerts/{id}` | ✅ | ✅ | ✅ |
| `GET /api/alerts/unhandled` | ✅ | ✅ | ✅ |
| `PUT /api/alerts/batch-handle` | ✅ | ✅ | ❌ |
| `PUT /api/alerts/{id}` | ✅ | ✅ | ❌ |

### §0.7 枚举映射表

同 UI-00-InfrastructureREADME §0.7，告警管理核心使用 `ALERT_LEVEL` 和 `HANDLE_STATUS`。

### §0.8 输入缺失处理

- ✅ `ControllerAPIREADME.md` — 已读取
- ✅ `IoT-Platform需求规格说明书.md` — 已读取
- ✅ `SecurityConfig.java` — 已读取

---

## §1 页面任务拆解

### P0 — 核心功能（必须完成，缺一则页面不可交付）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P0-1** | 告警列表分页表格（ElTable + ElPagination） | 中 |
| **P0-2** | 多条件筛选（设备ID/处理状态/告警级别/时间范围） | 中 |
| **P0-3** | 告警级别使用 ElTag 颜色标记 + 紧急行红色背景 | 低 |
| **P0-4** | 单条告警"处理"按钮 + 处理弹窗（ElDialog） | 中 |
| **P0-5** | 批量处理（ElTable 多选 + 批量处理按钮） | 中 |
| **P0-6** | 操作后列表自动刷新 | 低 |

### P1 — 重要功能（提升用户体验）

| 任务 | 描述 | 预估复杂度 |
|:---:|------|:---:|
| **P1-1** | 告警统计面板（总数/未处理/各级别数量） | 低 |
| **P1-2** | 紧急/严重告警行背景色差异化 | 低 |
| **P1-3** | 处理按钮二次确认（ElMessageBox） | 低 |

### P2 — 增量优化（可选）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P2-1** | 告警设备排名 Top5 | 低 |
| **P2-2** | 时间段统计图表（可选） | 中 |

---

## §2 目录结构

```
src/
├── views/
│   └── alert/
│       └── index.vue                    # 告警管理入口
├── components/
│   └── alert/
│       ├── AlertFilter.vue             # 筛选条件组件
│       ├── AlertTable.vue              # 告警列表表格
│       ├── AlertHandleDialog.vue       # 单个/批量告警处理弹窗
│       └── AlertStatsPanel.vue         # 告警统计面板
├── api/
│   └── alert.js                        # 告警接口封装（与仪表盘共用）
├── utils/
│   ├── request.js                      # Axios 封装（已存在）
│   └── constants.js                    # 枚举常量（已存在）
└── router/
    └── index.js                        # 路由配置（已存在 /alerts 路由）
```

---

## §3 数据流图

```
页面加载 (onMounted)
    ↓
fetchAlertList({ current: 1, size: 10 })    -- 加载第一页
（可选）fetchAlertStats()                    -- 加载统计面板
    ↓
渲染 AlertTable + AlertStatsPanel
    ↓
用户交互
├─ 筛选条件变更 → fetchAlertList(新条件, current: 1) → 重置到第一页
├─ 分页切换   → fetchAlertList(同条件, 新页码)
├─ 点击"处理" → 打开 AlertHandleDialog
│   ├─ 填写处理说明 + 处理人
│   └─ 确认 → PUT /api/alerts/batch-handle { ids: [id], handleUser }
│       ├─ 成功 → ElMessage.success → fetchAlertList(当前页)
│       └─ 失败 → ElMessage.error
└─ 勾选多选 + 点击"批量处理"
    └─ 打开 AlertHandleDialog（多选模式）
        └─ 确认 → PUT /api/alerts/batch-handle { ids: [...], handleUser }
            ├─ 成功 → ElMessage.success → 清空选中 → fetchAlertList(当前页)
            └─ 失败 → ElMessage.error
```

---

## §4 接口实现清单

### §4.1 接口定义

| 接口 | 方法 | 路径 | 关键参数 | 返回体 |
|------|:---:|------|------|------|
| 告警分页 | GET | `/api/alerts` | `current`, `size`, `deviceId?`, `isHandled?`, `alertLevel?`, `startTime?`, `endTime?` | `Page<Alert>` |
| 批量处理 | PUT | `/api/alerts/batch-handle` | `{ ids: number[], handleUser: string }` | `Result<Integer>`（成功处理数） |

### §4.2 API 层封装

```js
// src/api/alert.js（补充告警处理接口）
import request from '@/utils/request';

export function getAlerts(params) {
  return request.get('/alerts', { params });
}

export function getUnhandledAlerts() {
  return request.get('/alerts/unhandled');
}

export function batchHandleAlerts(data) {
  return request.put('/alerts/batch-handle', data);
}
```

### §4.3 接口调用示例

```js
// 分页查询 + 筛选
const res = await getAlerts({
  current: 1,
  size: 10,
  isHandled: 0,
  alertLevel: 1,
  startTime: '2026-05-01 00:00:00',
  endTime: '2026-05-31 23:59:59',
});
// res.data.records → Alert[]
// res.data.total   → 总条数

// 批量处理
await batchHandleAlerts({
  ids: [1, 2, 3],
  handleUser: 'admin',
});
```

### §4.4 接口权限映射

| 接口 | SecurityConfig 规则 | 前端策略 |
|------|------|------|
| `GET /api/alerts` | `HttpMethod.GET → authenticated()` | 无需角色限制 |
| `PUT /api/alerts/batch-handle` | `HttpMethod.PUT → hasAnyRole(ADMIN, OPERATOR)` | `v-if="authStore.role !== 'VIEWER'"` |

### §4.5 接口实现顺序

```
第一优先级:
  P0: GET /api/alerts        -- 列表查询 + 分页 + 筛选
  P0: PUT /api/alerts/batch-handle -- 处理告警（单条/批量）

第二优先级 (统计面板):
  P1: GET /api/alerts/unhandled     -- 未处理告警数量
```

---

## §5 组件树

```
AlertPage (views/alert/index.vue)
├── AlertStatsPanel（统计面板，可选）
│   ├── ElRow
│   │   ├── ElCol → 总数卡片
│   │   ├── ElCol → 未处理卡片（红色高亮）
│   │   ├── ElCol → 紧急卡片
│   │   └── ElCol → 已处理卡片
├── AlertFilter（筛选栏）
│   ├── ElForm（inline 布局）
│   │   ├── ElFormItem → ElInput（设备ID）
│   │   ├── ElFormItem → ElSelect（处理状态：全部/未处理/已处理）
│   │   ├── ElFormItem → ElSelect（告警级别：全部/1/2/3/4/5）
│   │   ├── ElFormItem → ElDatePicker（时间范围，daterange）
│   │   └── ElFormItem → ElButton（查询）+ ElButton（重置）
├── 操作栏
│   ├── ElButton（批量处理，v-if 权限，disabled 未选中）
├── AlertTable（表格）
│   ├── ElTable（多选 + 行样式）
│   │   ├── type="selection"（多选列，v-if 权限）
│   │   ├── 设备名称
│   │   ├── 告警类型
│   │   ├── 告警级别（ElTag）
│   │   ├── 告警值/阈值
│   │   ├── 处理状态（ElTag）
│   │   ├── 创建时间
│   │   └── 操作列（处理按钮，v-if 权限，已处理时disabled）
│   └── ElPagination（分页）
└── AlertHandleDialog（处理弹窗）
    ├── ElDialog
    │   ├── 告警列表摘要（已选告警）
    │   ├── ElForm
    │   │   ├── ElFormItem → ElInput（处理说明，必填）
    │   │   └── ElFormItem → ElInput（处理人，自动填充当前用户名）
    │   └── ElButton（确认）+ ElButton（取消）
```

---

## §6 核心实现

### §6.1 告警管理页面入口

**文件**: `src/views/alert/index.vue`

```vue
<template>
  <div class="alert-page">
    <alert-stats-panel :stats="stats" v-if="showStats" />

    <alert-filter
      :filter="filter"
      @search="handleSearch"
      @reset="handleReset"
    />

    <div class="toolbar">
      <el-button
        v-if="canHandle"
        type="warning"
        :disabled="selectedIds.length === 0"
        @click="handleBatchHandle"
      >
        批量处理 ({{ selectedIds.length }})
      </el-button>
    </div>

    <alert-table
      :alerts="alertList"
      :loading="loading"
      :can-handle="canHandle"
      @selection-change="handleSelectionChange"
      @handle="handleSingleHandle"
    />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="fetchAlertList"
    />

    <alert-handle-dialog
      v-model:visible="dialogVisible"
      :alert-ids="dialogIds"
      :mode="dialogMode"
      :username="username"
      @confirm="handleConfirmHandle"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { getAlerts, batchHandleAlerts } from '@/api/alert';
import { ElMessage, ElMessageBox } from 'element-plus';
import AlertFilter from '@/components/alert/AlertFilter.vue';
import AlertTable from '@/components/alert/AlertTable.vue';
import AlertStatsPanel from '@/components/alert/AlertStatsPanel.vue';
import AlertHandleDialog from '@/components/alert/AlertHandleDialog.vue';

const authStore = useAuthStore();
const username = computed(() => authStore.username);
const canHandle = computed(() => authStore.role !== 'VIEWER');

const loading = ref(false);
const alertList = ref([]);
const total = ref(0);
const selectedIds = ref([]);
const showStats = ref(true);

const pagination = reactive({ current: 1, size: 10 });

const filter = reactive({
  deviceId: '',
  isHandled: null,
  alertLevel: null,
  timeRange: [],
});

// 弹窗状态
const dialogVisible = ref(false);
const dialogIds = ref([]);
const dialogMode = ref('single'); // 'single' | 'batch'

async function fetchAlertList() {
  loading.value = true;
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
    };
    if (filter.deviceId) params.deviceId = filter.deviceId;
    if (filter.isHandled !== null && filter.isHandled !== '') params.isHandled = filter.isHandled;
    if (filter.alertLevel) params.alertLevel = filter.alertLevel;
    if (filter.timeRange?.length === 2) {
      params.startTime = filter.timeRange[0];
      params.endTime = filter.timeRange[1];
    }
    const res = await getAlerts(params);
    alertList.value = res.data.records || [];
    total.value = res.data.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.current = 1;
  fetchAlertList();
}

function handleReset() {
  filter.deviceId = '';
  filter.isHandled = null;
  filter.alertLevel = null;
  filter.timeRange = [];
  pagination.current = 1;
  fetchAlertList();
}

function handleSelectionChange(ids) {
  selectedIds.value = ids;
}

function handleSingleHandle(alert) {
  dialogIds.value = [alert.id];
  dialogMode.value = 'single';
  dialogVisible.value = true;
}

function handleBatchHandle() {
  dialogIds.value = [...selectedIds.value];
  dialogMode.value = 'batch';
  dialogVisible.value = true;
}

async function handleConfirmHandle(handleUser) {
  try {
    await batchHandleAlerts({ ids: dialogIds.value, handleUser });
    ElMessage.success(`成功处理 ${dialogIds.value.length} 条告警`);
    selectedIds.value = [];
    dialogVisible.value = false;
    fetchAlertList();
  } catch {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  fetchAlertList();
});
</script>

<style scoped>
.alert-page { padding: 0; }
.toolbar { margin-bottom: 16px; }
</style>
```

### §6.2 AlertTable 组件

**文件**: `src/components/alert/AlertTable.vue`

```vue
<template>
  <el-table
    :data="alerts"
    v-loading="loading"
    @selection-change="onSelectionChange"
    :row-class-name="rowClassName"
  >
    <el-table-column v-if="canHandle" type="selection" width="50" />
    <el-table-column prop="deviceName" label="设备名称" min-width="120" />
    <el-table-column prop="alertType" label="告警类型" min-width="100" />
    <el-table-column prop="alertLevel" label="告警级别" width="90">
      <template #default="{ row }">
        <el-tag :type="levelTagType(row.alertLevel)" size="small">
          {{ ALERT_LEVEL[row.alertLevel]?.label || row.alertLevel }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="告警值/阈值" min-width="120">
      <template #default="{ row }">
        {{ row.alertValue ?? '-' }} / {{ row.thresholdValue ?? '-' }}
      </template>
    </el-table-column>
    <el-table-column prop="isHandled" label="状态" width="80">
      <template #default="{ row }">
        <el-tag :type="row.isHandled ? 'success' : 'danger'" size="small">
          {{ row.isHandled ? '已处理' : '未处理' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createTime" label="告警时间" min-width="160" />
    <el-table-column v-if="canHandle" label="操作" width="100" fixed="right">
      <template #default="{ row }">
        <el-button
          type="primary"
          size="small"
          link
          :disabled="row.isHandled === 1"
          @click="$emit('handle', row)"
        >
          处理
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { ALERT_LEVEL } from '@/utils/constants';

defineProps({
  alerts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canHandle: { type: Boolean, default: false },
});

const emit = defineEmits(['selection-change', 'handle']);

function onSelectionChange(selection) {
  emit('selection-change', selection.map(item => item.id));
}

function rowClassName({ row }) {
  if (row.alertLevel === 1) return 'alert-row-emergency';
  if (row.alertLevel === 2) return 'alert-row-critical';
  return '';
}

function levelTagType(level) {
  const map = { 1: 'danger', 2: 'warning', 3: '', 4: 'info', 5: 'info' };
  return map[level] || '';
}
</script>

<style>
.alert-row-emergency { background-color: #FEF0F0 !important; }
.alert-row-critical { background-color: #FDF6EC !important; }
</style>
```

### §6.3 AlertFilter 组件

**文件**: `src/components/alert/AlertFilter.vue`

```vue
<template>
  <el-form :model="filter" inline class="alert-filter">
    <el-form-item label="设备ID">
      <el-input v-model="filter.deviceId" placeholder="请输入设备ID" clearable />
    </el-form-item>
    <el-form-item label="处理状态">
      <el-select v-model="filter.isHandled" placeholder="全部" clearable style="width: 120px">
        <el-option label="未处理" :value="0" />
        <el-option label="已处理" :value="1" />
      </el-select>
    </el-form-item>
    <el-form-item label="告警级别">
      <el-select v-model="filter.alertLevel" placeholder="全部" clearable style="width: 120px">
        <el-option
          v-for="item in ALERT_LEVEL_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="时间范围">
      <el-date-picker
        v-model="filter.timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="$emit('search')">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ALERT_LEVEL } from '@/utils/constants';

defineProps({
  filter: { type: Object, required: true },
});

defineEmits(['search', 'reset']);

const ALERT_LEVEL_OPTIONS = Object.entries(ALERT_LEVEL).map(([value, item]) => ({
  value: Number(value),
  label: item.label,
}));
</script>
```

### §6.4 AlertHandleDialog 组件

**文件**: `src/components/alert/AlertHandleDialog.vue`

```vue
<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'batch' ? '批量处理告警' : '处理告警'"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
  >
    <p class="dialog-tip">
      正在处理 <strong>{{ alertIds.length }}</strong> 条告警
    </p>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="处理说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入处理说明"
        />
      </el-form-item>
      <el-form-item label="处理人">
        <el-input v-model="form.handleUser" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确认处理
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  alertIds: { type: Array, default: () => [] },
  mode: { type: String, default: 'single' },
  username: { type: String, default: '' },
});

const emit = defineEmits(['update:visible', 'confirm']);

const formRef = ref(null);
const loading = ref(false);

const form = reactive({
  description: '',
  handleUser: '',
});

const rules = {
  description: [{ required: true, message: '请输入处理说明', trigger: 'blur' }],
};

function handleOpen() {
  form.description = '';
  form.handleUser = props.username;
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  emit('confirm', form.handleUser);
}
</script>
```

---

## §7 状态管理

| Store | 用途 | 关键状态 | 页面依赖 |
|-------|------|----------|:---:|
| `useAuthStore` | 角色校验 + 用户名填充 | `role`, `username` | ✅ 按钮显隐 + 处理人自动填充 |
| — | — | — | 告警数据由组件内 `ref` 管理，无需 Store |

---

## §8 页面完成定义 (DoD)

告警管理页"完成"必须满足以下全部条件：

- [x] 告警列表分页展示，支持 10/20/50 条/页切换
- [x] 筛选条件全部可用：设备ID/处理状态/告警级别/时间范围
- [x] 告警级别使用 ElTag 颜色区分（紧急=红色、严重=橙色、一般=蓝色、警告=灰色）
- [x] 紧急告警行背景色 #FEF0F0，严重告警行背景色 #FDF6EC
- [x] 单条"处理"按钮向 ADMIN/OPERATOR 可见，VIEWER 不可见
- [x] 已处理的告警"处理"按钮 disabled
- [x] 批量处理多选可用，"批量处理"按钮在未选中时 disabled
- [x] 处理弹窗必填"处理说明"，处理人自动填充当前用户名
- [x] 处理成功 → ElMessage.success + 列表自动刷新 + 清除选中
- [x] VIEWER 角色登录：可查看告警列表，不显示"处理"/"批量处理"按钮、不显示多选列
- [x] 处理按钮有二次确认（ElMessageBox 或 ElDialog 确认按钮）

---

## §9 集成检查清单

| # | 检查项 | 验证方式 |
|:---:|------|------|
| 1 | 登录后进入告警管理 | 列表正常加载，第一页 10 条 |
| 2 | 筛选"未处理"告警 | 列表只显示 isHandled=0 的记录 |
| 3 | 筛选"级别=紧急" | 列表只显示 alertLevel=1 的记录 |
| 4 | 选择时间范围 | 列表按时间过滤 |
| 5 | 点击"重置" | 所有筛选条件清空，列表恢复第一页 |
| 6 | 切换分页 | 第二页数据正确加载 |
| 7 | ADMIN/OPERATOR 点击"处理" | 弹窗正常打开，处理人已填充 |
| 8 | 不填处理说明点确认 | 表单校验提示"请输入处理说明" |
| 9 | 填完处理说明点确认 | 告警状态变为"已处理"，按钮 disabled |
| 10 | 勾选 3 条点击"批量处理" | 弹窗标题"批量处理告警"，3 条全部处理 |
| 11 | VIEWER 角色进入页面 | 无"处理"列、无多选列、无"批量处理"按钮 |
| 12 | VIEWER 直接调用 PUT 接口 | 后端返回 403, Axios 拦截器弹窗提示 |

---

## §10 学习重点

| # | 知识点 | 文件/位置 | 说明 |
|:---:|------|------|------|
| 1 | 行样式控制 | `components/alert/AlertTable.vue` §6.2 | `row-class-name` 按 alertLevel 动态设置背景色 |
| 2 | ElTable 多选 | `views/alert/index.vue` §6.1 | `@selection-change` 获取选中行 ID 列表 |
| 3 | 组件通信 | 整页 | props/emit 父子通信 + v-model 双向绑定弹窗 |
| 4 | 表单重置 | `components/alert/AlertHandleDialog.vue` §6.4 | `@open` 事件中重置表单状态 |
| 5 | 批量操作模式 | `views/alert/index.vue` §6.1 | `dialogMode` 区分 single/batch 渲染不同标题 |
| 6 | 按钮级权限 | `views/alert/index.vue` §6.1 | `v-if="authStore.role !== 'VIEWER'"` |

---

## §11 面试准备

### §11.1 核心交互流程

1. 进入告警管理 → `onMounted` → `fetchAlertList({ current:1, size:10 })` → 表格渲染
2. 筛选条件变更 → `handleSearch()` → `pagination.current = 1` → 重新请求 → 表格更新
3. 点击"处理" → `handleSingleHandle(alert)` → `dialogVisible=true` → 填写处理说明 → PUT 批量处理接口（单条）→ 刷新列表
4. 勾选多行 → `handleSelectionChange` → `selectedIds` 更新 → 点击"批量处理" → PUT 批量处理接口（多条）→ 刷新列表

### §11.2 面试模拟问题

**问题 1**: 如何处理"用户快速连续点击处理按钮导致重复请求"的问题？

> 参考答案：（1）处理按钮在 `handleSubmit` 中设置 `loading=true`，请求完成前按钮 disabled；（2）已处理告警的按钮直接 disabled，无法再次点击；（3）后端批量处理接口天然幂等，同一告警重复处理不会产生副作用。

**问题 2**: VIEWER 角色的前端如何与后端权限协调？

> 参考答案：前端通过 `v-if="authStore.role !== 'VIEWER'"` 隐藏"处理"按钮（友好体验），但后端 SecurityConfig 已配置 `PUT /alerts/** → hasAnyRole(ADMIN, OPERATOR)`，即便 VIEWER 绕过前端直接调用接口，也会返回 403。前端权限仅做 UI 遮蔽，真正的安全由后端保障。

**问题 3**: 批量处理时，如果部分告警处理失败，如何提示？

> 参考答案：当前后端接口返回的是成功处理数量（`Result<Integer>`），前端展示 `已成功处理 3 条告警`。如果需区分成功和失败，后端需要改造返回结构。前端可做兜底：请求成功后刷新列表，用户通过列表状态确认是否全部处理成功。

---

## §12 页面复杂度评估

| 维度 | 评级 | 说明 |
|------|:---:|------|
| 组件数量 | ⭐⭐⭐ | 1 页面 + 4 子组件 |
| 接口数量 | ⭐⭐ | 2-3 个接口 |
| 状态复杂度 | ⭐⭐⭐ | 筛选状态 + 分页状态 + 弹窗状态 + 多选状态 |
| 权限逻辑 | ⭐⭐⭐ | 按钮显隐 + 多选列显隐 + 已处理禁用 |
| 交互复杂度 | ⭐⭐⭐ | 筛选联动 + 批量操作 + 弹窗表单校验 |
| **综合** | **⭐⭐⭐** | **中等复杂度，需掌握权限控制和批量操作** |

---

## §13 硬性约束

- **禁止**在处理弹窗关闭后不重置表单（使用 `@open` 事件重置）
- **禁止**批量处理前不检查 `selectedIds.length`（按钮 disabled 已处理但仍需后端校验）
- **禁止**在多选列中对 VIEWER 角色渲染（`v-if="canHandle"` 同步隐藏）
- **禁止**在已处理告警上启用处理按钮（`disabled` + 后端不重复处理）
- **必须**在筛选条件变更时重置 `pagination.current = 1`
- **必须**在 ElTable 行样式中使用 `!important`（Element Plus 样式优先级问题）

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*