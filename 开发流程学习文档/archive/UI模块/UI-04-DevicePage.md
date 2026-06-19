> [已归档] 本文件已移至 archive/UI模块/。

# 设备管理 — 前端实现方案

> **版本**: V1.0.0 | **创建**: 2026-05-31 | **需求**: REQ-UI-FR-003 / REQ-DEV-FR-001
> **关联基础设施**: UI-00-InfrastructureREADME.md
> **关联规范**: FrontendPageImplementationPrompt.md V3.1.1
> **页面类型**: 后台管理 CRUD 页面（含状态切换 + 告警配置子功能）

---

## §A 实现边界（必须首先声明）

本页面**仅实现**需求文档明确要求的功能。

**禁止主动增加以下内容**（除非需求文档明确要求）：
- 国际化 (i18n)
- 虚拟滚动 / 无限滚动
- 微前端 / SSR / PWA
- WebWorker / Service Worker
- Excel 批量导入（REQ-DEV-FR-003 为 P2，后续单独迭代）
- 设备分组 / 标签管理
- 设备拓扑图 / 地理分布图
- 设备固件升级
- 深色模式

---

## §0 基础设施约定

全部引用 `UI-00-InfrastructureREADME.md`，本章仅补充设备管理特化内容。

### §0.1 技术栈

同 UI-00-InfrastructureREADME §0.1，设备管理额外使用：

| 技术 | 版本 | 用途 |
|------|:---:|------|
| 无额外依赖 | — | Element Plus ElTable/ElDialog/ElSwitch 已足够 |

### §0.2 后端接口

| 接口 | 方法 | 实际路径 | 认证 | 说明 |
|------|:---:|------|:---:|------|
| 设备分页 | GET | `/api/devices/page` | 已认证 | `current`, `size`, `status?`, `name?` |
| 设备详情 | GET | `/api/devices/{id}` | 已认证 | 单条设备详情 |
| 新增设备 | POST | `/api/devices` | ADMIN/OPERATOR | `@Validated(CreateGroup.class)` |
| 更新设备 | PUT | `/api/devices/{id}` | ADMIN/OPERATOR | `@Validated(UpdateGroup.class)` |
| 删除设备 | DELETE | `/api/devices/{id}` | **仅 ADMIN** | 删除前校验设备存在性 |
| 状态切换 | PUT | `/api/devices/{id}/status` | ADMIN/OPERATOR | `status?=0\|1\|2` |
| 告警配置 | PUT | `/api/devices/{id}/alert-config` | ADMIN/OPERATOR | `@Validated @RequestBody AlertRuleConfig` |
| 设备类型列表 | GET | `/api/device-types` | 已认证 | 新增/编辑弹窗的选择器数据源 |

### §0.3 Axios 拦截器

同 UI-00-InfrastructureREADME §0.3。

### §0.4 路由守卫

同 UI-00-InfrastructureREADME §0.4。

### §0.5 按钮级权限

| 操作 | 后端规则 | 前端策略 |
|------|------|------|
| 新增设备 | `POST /devices → hasAnyRole(ADMIN, OPERATOR)` | `v-if="authStore.role !== 'VIEWER'"` |
| 编辑设备 | `PUT /devices/** → hasAnyRole(ADMIN, OPERATOR)` | `v-if="authStore.role !== 'VIEWER'"` |
| 删除设备 | `HttpMethod.DELETE → hasRole(ADMIN)` | `v-if="authStore.role === 'ADMIN'"` |
| 状态切换 | `PUT /devices/{id}/status → hasAnyRole(ADMIN, OPERATOR)` | `v-if="authStore.role !== 'VIEWER'"` |
| 告警配置 | `PUT /devices/{id}/alert-config → hasAnyRole(ADMIN, OPERATOR)` | `v-if="authStore.role !== 'VIEWER'"` |

### §0.6 RBAC 权限矩阵（本页涉及）

| 端点 | ADMIN | OPERATOR | VIEWER |
|:---|:---:|:---:|:---:|
| `GET /api/devices/page` | ✅ | ✅ | ✅ |
| `GET /api/devices/{id}` | ✅ | ✅ | ✅ |
| `GET /api/device-types` | ✅ | ✅ | ✅ |
| `POST /api/devices` | ✅ | ✅ | ❌ |
| `PUT /api/devices/{id}` | ✅ | ✅ | ❌ |
| `PUT /api/devices/{id}/status` | ✅ | ✅ | ❌ |
| `PUT /api/devices/{id}/alert-config` | ✅ | ✅ | ❌ |
| `DELETE /api/devices/{id}` | ✅ | ❌ | ❌ |

### §0.7 枚举映射表

同 UI-00-InfrastructureREADME §0.7，设备管理核心使用 `DEVICE_STATUS`。

### §0.8 输入缺失处理

- ✅ `ControllerAPIREADME.md` — 已读取
- ✅ `IoT-Platform需求规格说明书.md` — 已读取
- ✅ `SecurityConfig.java` — 已读取

---

## §1 页面任务拆解

### P0 — 核心功能（必须完成，缺一则页面不可交付）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P0-1** | 设备列表分页表格（名称/编码/类型/状态/位置/IP/更新时间） | 中 |
| **P0-2** | 筛选栏（状态下拉 + 名称模糊搜索） | 低 |
| **P0-3** | 新增设备弹窗（ElDialog + ElForm + 设备类型选择器） | 中 |
| **P0-4** | 编辑设备弹窗（表单预填充 + 提交更新） | 中 |
| **P0-5** | 删除确认（ElMessageBox 二次确认，仅 ADMIN） | 低 |
| **P0-6** | 设备状态 ElSwitch 切换（仅 ADMIN/OPERATOR 可切换） | 低 |
| **P0-7** | CRUD 操作后列表自动刷新 | 低 |

### P1 — 重要功能（提升用户体验）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P1-1** | 设备告警规则配置弹窗（阈值/运算符/告警级别） | 高 |
| **P1-2** | 设备类型下拉选择器（新增/编辑时加载设备类型） | 低 |
| **P1-3** | 状态列使用 ElTag 颜色区分（在线=绿、离线=灰、故障=红） | 低 |

### P2 — 增量优化（可选）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P2-1** | 表格行点击跳转设备详情（如果是弹窗展示） | 低 |
| **P2-2** | 设备编码生成规则提示 | 低 |

---

## §2 目录结构

```
src/
├── views/
│   └── device/
│       └── index.vue                      # 设备管理入口
├── components/
│   └── device/
│       ├── DeviceFilter.vue               # 筛选组件
│       ├── DeviceTable.vue                # 设备列表表格
│       ├── DeviceFormDialog.vue           # 新增/编辑设备弹窗（复用）
│       └── AlertConfigDialog.vue          # 告警规则配置弹窗
├── api/
│   ├── device.js                          # 设备接口
│   └── deviceType.js                      # 设备类型接口
├── utils/
│   ├── request.js                         # Axios 封装（已存在）
│   └── constants.js                       # 枚举常量（已存在）
└── router/
    └── index.js                           # 路由配置（已存在 /devices 路由）
```

---

## §3 数据流图

```
页面加载 (onMounted)
    ↓
Promise.all([
  fetchDeviceList({ current: 1, size: 10 }),
  fetchDeviceTypes()                        -- 设备类型下拉数据
])
    ↓
渲染 DeviceTable + DeviceFilter
    ↓
用户交互
├─ 筛选/分页
│   ├─ 状态筛选/名称搜索 → fetchDeviceList(新条件, current:1) → 表格更新
│   └─ 分页切换 → fetchDeviceList(同条件, 新页码) → 表格更新
│
├─ 新增设备
│   └─ 点击"新增" → DeviceFormDialog(mode='create')
│       ├─ 选择设备类型（下拉）
│       ├─ 填写设备名称/编码/位置/IP/状态
│       ├─ ElForm.validate() → POST /api/devices
│       └─ 成功 → ElMessage.success → closeDialog → fetchDeviceList
│
├─ 编辑设备
│   └─ 点击"编辑" → DeviceFormDialog(mode='edit', deviceData)
│       ├─ 表单预填充
│       ├─ 编辑后提交 → PUT /api/devices/{id}
│       └─ 成功 → ElMessage.success → closeDialog → fetchDeviceList
│
├─ 删除设备
│   └─ 点击"删除" → ElMessageBox.confirm('确认删除设备 XXX？')
│       └─ 确认 → DELETE /api/devices/{id}
│           ├─ 成功 → ElMessage.success → fetchDeviceList(当前页)
│           └─ 失败 → ElMessage.error
│
├─ 状态切换
│   └─ ElSwitch.change → PUT /api/devices/{id}/status?status=X
│       ├─ 成功 → 本地更新行状态（乐观更新）
│       └─ 失败 → ElMessage.error + 恢复原状态
│
└─ 告警配置
    └─ 点击"告警配置" → AlertConfigDialog(deviceId)
        ├─ 展示当前规则列表
        ├─ 编辑规则（指标/运算符/阈值/告警级别）
        ├─ 提交 → PUT /api/devices/{id}/alert-config
        └─ 成功 → ElMessage.success → closeDialog
```

---

## §4 接口实现清单

### §4.1 接口定义

| 接口 | 方法 | 路径 | 请求体/参数 | 返回体 |
|------|:---:|------|------|------|
| 设备分页 | GET | `/api/devices/page` | `current`, `size`, `status?`, `name?` | `Page<Device>` |
| 设备详情 | GET | `/api/devices/{id}` | `id` (path) | `Device` |
| 新增设备 | POST | `/api/devices` | `Device` (CreateGroup) | `Void` |
| 更新设备 | PUT | `/api/devices/{id}` | `Device` (UpdateGroup) | `Void` |
| 删除设备 | DELETE | `/api/devices/{id}` | `id` (path) | `Void` |
| 状态切换 | PUT | `/api/devices/{id}/status` | `id` + `status` (query) | `Void` |
| 告警配置 | PUT | `/api/devices/{id}/alert-config` | `AlertRuleConfig` | `Void` |
| 设备类型列表 | GET | `/api/device-types` | 无 | `List<DeviceType>` |

### §4.2 API 层封装

```js
// src/api/device.js
import request from '@/utils/request';

export function getDevicePage(params) {
  return request.get('/devices/page', { params });
}

export function getDeviceDetail(id) {
  return request.get(`/devices/${id}`);
}

export function createDevice(data) {
  return request.post('/devices', data);
}

export function updateDevice(id, data) {
  return request.put(`/devices/${id}`, data);
}

export function deleteDevice(id) {
  return request.delete(`/devices/${id}`);
}

export function updateDeviceStatus(id, status) {
  return request.put(`/devices/${id}/status`, null, { params: { status } });
}

export function updateDeviceAlertConfig(id, data) {
  return request.put(`/devices/${id}/alert-config`, data);
}
```

```js
// src/api/deviceType.js
import request from '@/utils/request';

export function getDeviceTypes() {
  return request.get('/device-types');
}
```

### §4.3 接口调用示例

```js
// 分页查询
const res = await getDevicePage({ current: 1, size: 10, status: 1, name: '温度' });

// 新增
await createDevice({
  deviceName: '温度传感器B',
  deviceCode: 'SN-002',
  typeId: 1,
  status: 1,
  location: '3号楼',
  ipAddress: '192.168.1.100',
});

// 状态切换（乐观更新）
try {
  await updateDeviceStatus(row.id, newStatus);
  row.status = newStatus; // 本地即时更新
} catch {
  ElMessage.error('状态更新失败');
  row.status = oldStatus; // 恢复原状态
}
```

### §4.4 接口权限映射

| 接口 | SecurityConfig 规则 | 前端策略 |
|------|------|------|
| `GET /api/devices/page` | `HttpMethod.GET → authenticated()` | 无需角色限制 |
| `GET /api/device-types` | `HttpMethod.GET → authenticated()` | 无需角色限制 |
| `POST /api/devices` | `HttpMethod.POST → hasAnyRole(ADMIN, OPERATOR)` | `v-if="canOperate"` |
| `PUT /api/devices/{id}` | `HttpMethod.PUT → hasAnyRole(ADMIN, OPERATOR)` | `v-if="canOperate"` |
| `PUT /api/devices/{id}/status` | `HttpMethod.PUT → hasAnyRole(ADMIN, OPERATOR)` | `v-if="canOperate"` |
| `PUT /api/devices/{id}/alert-config` | `HttpMethod.PUT → hasAnyRole(ADMIN, OPERATOR)` | `v-if="canOperate"` |
| `DELETE /api/devices/{id}` | `HttpMethod.DELETE → hasRole(ADMIN)` | `v-if="isAdmin"` |

### §4.5 接口实现顺序

```
第一优先级:
  P0: GET /api/devices/page      -- 列表查询
  P0: GET /api/device-types      -- 设备类型选择器数据源
  P0: POST /api/devices          -- 新增
  P0: PUT /api/devices/{id}      -- 编辑
  P0: DELETE /api/devices/{id}   -- 删除（ADMIN only）
  P0: PUT /api/devices/{id}/status -- 状态切换

第二优先级:
  P1: PUT /api/devices/{id}/alert-config -- 告警配置
```

---

## §5 组件树

```
DevicePage (views/device/index.vue)
├── DeviceFilter（筛选栏）
│   ├── ElForm（inline）
│   │   ├── ElFormItem → ElSelect（状态：全部/在线/离线/故障）
│   │   ├── ElFormItem → ElInput（名称模糊搜索）
│   │   └── ElFormItem → ElButton（查询）+ ElButton（重置）
├── 操作栏
│   ├── ElButton（新增设备，v-if="canOperate"，type="primary"）
├── DeviceTable（设备表格）
│   ├── ElTable
│   │   ├── 设备名称
│   │   ├── 设备编码
│   │   ├── 设备类型（typeName）
│   │   ├── 状态（ElSwitch，v-if="canOperate"） / （ElTag，v-else）
│   │   ├── 位置
│   │   ├── IP 地址
│   │   ├── 更新时间
│   │   └── 操作列
│   │       ├── ElButton（编辑，v-if="canOperate"）
│   │       ├── ElButton（告警配置，v-if="canOperate"）
│   │       └── ElButton（删除，v-if="isAdmin"，type="danger"）
│   └── ElPagination
├── DeviceFormDialog（新增/编辑弹窗）
│   ├── ElDialog（title=新增设备/编辑设备）
│   │   ├── ElForm（ref="deviceFormRef"，:rules）
│   │   │   ├── ElFormItem → ElInput（设备名称，必填）
│   │   │   ├── ElFormItem → ElInput（设备编码，必填，新增可编辑/编辑只读）
│   │   │   ├── ElFormItem → ElSelect（设备类型，下拉，必填）
│   │   │   ├── ElFormItem → ElInput（位置）
│   │   │   ├── ElFormItem → ElInput（IP 地址）
│   │   │   └── ElFormItem → ElSelect（状态：0/1/2）
│   │   └── ElButton（确认）+ ElButton（取消）
└── AlertConfigDialog（告警配置弹窗）
    ├── ElDialog（title=设备告警规则配置）
    │   ├── 当前规则列表（ElTable，动态行）
    │   │   ├── 指标选择（ElSelect）
    │   │   ├── 运算符（ElSelect：GT/LT/GTE/LTE/EQ）
    │   │   ├── 阈值（ElInputNumber）
    │   │   └── 告警级别（ElSelect：1-5）
    │   ├── ElButton（添加规则）
    │   └── ElButton（保存）+ ElButton（取消）
```

---

## §6 核心实现

### §6.1 设备管理页面入口

**文件**: `src/views/device/index.vue`

```vue
<template>
  <div class="device-page">
    <device-filter
      :filter="filter"
      @search="handleSearch"
      @reset="handleReset"
    />

    <div class="toolbar">
      <el-button v-if="canOperate" type="primary" @click="handleCreate">
        新增设备
      </el-button>
    </div>

    <device-table
      :devices="deviceList"
      :loading="loading"
      :can-operate="canOperate"
      :is-admin="isAdmin"
      @edit="handleEdit"
      @delete="handleDelete"
      @status-change="handleStatusChange"
      @alert-config="handleAlertConfig"
    />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="fetchDeviceList"
    />

    <device-form-dialog
      v-model:visible="formVisible"
      :mode="formMode"
      :device="currentDevice"
      :device-types="deviceTypes"
      @confirm="handleFormConfirm"
    />

    <alert-config-dialog
      v-model:visible="alertConfigVisible"
      :device-id="currentDeviceId"
      @confirm="handleAlertConfigConfirm"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import {
  getDevicePage,
  createDevice,
  updateDevice,
  deleteDevice,
  updateDeviceStatus,
} from '@/api/device';
import { getDeviceTypes } from '@/api/deviceType';
import DeviceFilter from '@/components/device/DeviceFilter.vue';
import DeviceTable from '@/components/device/DeviceTable.vue';
import DeviceFormDialog from '@/components/device/DeviceFormDialog.vue';
import AlertConfigDialog from '@/components/device/AlertConfigDialog.vue';

const authStore = useAuthStore();
const canOperate = computed(() => authStore.role !== 'VIEWER');
const isAdmin = computed(() => authStore.role === 'ADMIN');

const loading = ref(false);
const deviceList = ref([]);
const total = ref(0);
const deviceTypes = ref([]);

const pagination = reactive({ current: 1, size: 10 });
const filter = reactive({ status: null, name: '' });

const formVisible = ref(false);
const formMode = ref('create');
const currentDevice = ref(null);

const alertConfigVisible = ref(false);
const currentDeviceId = ref(null);

async function fetchDeviceList() {
  loading.value = true;
  try {
    const params = { current: pagination.current, size: pagination.size };
    if (filter.status !== null && filter.status !== '') params.status = filter.status;
    if (filter.name) params.name = filter.name;
    const res = await getDevicePage(params);
    deviceList.value = res.data.records || [];
    total.value = res.data.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.current = 1;
  fetchDeviceList();
}

function handleReset() {
  filter.status = null;
  filter.name = '';
  pagination.current = 1;
  fetchDeviceList();
}

function handleCreate() {
  formMode.value = 'create';
  currentDevice.value = null;
  formVisible.value = true;
}

function handleEdit(device) {
  formMode.value = 'edit';
  currentDevice.value = { ...device };
  formVisible.value = true;
}

async function handleDelete(device) {
  try {
    await ElMessageBox.confirm(
      `确认删除设备 "${device.deviceName}"？`,
      '删除确认',
      { type: 'warning' }
    );
    await deleteDevice(device.id);
    ElMessage.success('设备已删除');
    fetchDeviceList();
  } catch {
    // 取消删除
  }
}

async function handleStatusChange(device, newStatus) {
  const oldStatus = device.status;
  try {
    await updateDeviceStatus(device.id, newStatus);
    ElMessage.success('状态已更新');
  } catch {
    device.status = oldStatus;
  }
}

function handleAlertConfig(device) {
  currentDeviceId.value = device.id;
  alertConfigVisible.value = true;
}

async function handleFormConfirm(data) {
  try {
    if (formMode.value === 'create') {
      await createDevice(data);
      ElMessage.success('设备已创建');
    } else {
      await updateDevice(currentDevice.value.id, data);
      ElMessage.success('设备已更新');
    }
    formVisible.value = false;
    fetchDeviceList();
  } catch {
    // 错误已由拦截器处理
  }
}

function handleAlertConfigConfirm() {
  alertConfigVisible.value = false;
  ElMessage.success('告警规则已更新');
}

onMounted(async () => {
  try {
    const res = await getDeviceTypes();
    deviceTypes.value = res.data || [];
  } catch { /* 非关键 */ }
  fetchDeviceList();
});
</script>
```

### §6.2 DeviceTable 组件

**文件**: `src/components/device/DeviceTable.vue`

```vue
<template>
  <el-table :data="devices" v-loading="loading">
    <el-table-column prop="deviceName" label="设备名称" min-width="120" />
    <el-table-column prop="deviceCode" label="设备编码" min-width="120" />
    <el-table-column prop="typeName" label="设备类型" min-width="100" />
    <el-table-column label="状态" width="100">
      <template #default="{ row }">
        <el-switch
          v-if="canOperate"
          :model-value="row.status"
          :active-value="1"
          :inactive-value="0"
          active-color="#67C23A"
          inactive-color="#909399"
          @change="val => $emit('status-change', row, val)"
        />
        <el-tag v-else :type="statusType(row.status)" size="small">
          {{ DEVICE_STATUS[row.status]?.label || row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="location" label="位置" min-width="100" />
    <el-table-column prop="ipAddress" label="IP地址" min-width="130" />
    <el-table-column prop="updateTime" label="更新时间" min-width="160" />
    <el-table-column v-if="canOperate || isAdmin" label="操作" width="240" fixed="right">
      <template #default="{ row }">
        <el-button v-if="canOperate" type="primary" size="small" link @click="$emit('edit', row)">
          编辑
        </el-button>
        <el-button
          v-if="canOperate"
          type="warning"
          size="small"
          link
          @click="$emit('alert-config', row)"
        >
          告警配置
        </el-button>
        <el-button
          v-if="isAdmin"
          type="danger"
          size="small"
          link
          @click="$emit('delete', row)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { DEVICE_STATUS } from '@/utils/constants';

defineProps({
  devices: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canOperate: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
});

defineEmits(['edit', 'delete', 'status-change', 'alert-config']);

function statusType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger' };
  return map[status] || '';
}
</script>
```

### §6.3 DeviceFormDialog 组件

**文件**: `src/components/device/DeviceFormDialog.vue`

```vue
<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增设备' : '编辑设备'"
    width="550px"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="设备名称" prop="deviceName">
        <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
      </el-form-item>
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input
          v-model="form.deviceCode"
          placeholder="请输入设备编码"
          :disabled="mode === 'edit'"
        />
      </el-form-item>
      <el-form-item label="设备类型" prop="typeId">
        <el-select v-model="form.typeId" placeholder="请选择设备类型" style="width: 100%">
          <el-option
            v-for="item in deviceTypes"
            :key="item.id"
            :label="item.typeName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="位置">
        <el-input v-model="form.location" placeholder="请输入设备位置" />
      </el-form-item>
      <el-form-item label="IP地址">
        <el-input v-model="form.ipAddress" placeholder="请输入IP地址" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status" style="width: 100%">
          <el-option
            v-for="(item, key) in DEVICE_STATUS"
            :key="key"
            :label="item.label"
            :value="Number(key)"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { DEVICE_STATUS } from '@/utils/constants';

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  device: { type: Object, default: null },
  deviceTypes: { type: Array, default: () => [] },
});

const emit = defineEmits(['update:visible', 'confirm']);

const formRef = ref(null);
const loading = ref(false);

const form = reactive({
  deviceName: '',
  deviceCode: '',
  typeId: null,
  location: '',
  ipAddress: '',
  status: 1,
});

const rules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
};

function handleOpen() {
  if (props.mode === 'edit' && props.device) {
    form.deviceName = props.device.deviceName || '';
    form.deviceCode = props.device.deviceCode || '';
    form.typeId = props.device.typeId || null;
    form.location = props.device.location || '';
    form.ipAddress = props.device.ipAddress || '';
    form.status = props.device.status ?? 1;
  } else {
    form.deviceName = '';
    form.deviceCode = '';
    form.typeId = null;
    form.location = '';
    form.ipAddress = '';
    form.status = 1;
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  emit('confirm', { ...form });
}
</script>
```

### §6.4 AlertConfigDialog 组件

**文件**: `src/components/device/AlertConfigDialog.vue`

```vue
<template>
  <el-dialog
    :model-value="visible"
    title="设备告警规则配置"
    width="700px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="config-tip">
      配置此设备的告警规则。未配置规则时将使用设备类型的默认规则。
    </div>
    <el-table :data="rules" border>
      <el-table-column label="指标" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.metricKey" placeholder="如 temperature" />
        </template>
      </el-table-column>
      <el-table-column label="运算符" width="100">
        <template #default="{ row }">
          <el-select v-model="row.operator" style="width: 100%">
            <el-option v-for="op in operators" :key="op.value" :label="op.label" :value="op.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="阈值" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.threshold" :precision="2" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="告警级别" width="110">
        <template #default="{ row }">
          <el-select v-model="row.alertLevel" style="width: 100%">
            <el-option
              v-for="(item, key) in ALERT_LEVEL"
              :key="key"
              :label="item.label"
              :value="Number(key)"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button type="danger" size="small" link @click="removeRule($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button type="primary" link class="add-rule-btn" @click="addRule">+ 添加规则</el-button>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { updateDeviceAlertConfig } from '@/api/device';
import { ALERT_LEVEL } from '@/utils/constants';

const props = defineProps({
  visible: { type: Boolean, default: false },
  deviceId: { type: Number, default: null },
});

const emit = defineEmits(['update:visible', 'confirm']);

const loading = ref(false);
const rules = ref([]);

const operators = [
  { label: '大于 (>)', value: 'GT' },
  { label: '小于 (<)', value: 'LT' },
  { label: '≥', value: 'GTE' },
  { label: '≤', value: 'LTE' },
  { label: '等于 (=)', value: 'EQ' },
];

function addRule() {
  rules.value.push({
    metricKey: '',
    operator: 'GT',
    threshold: 0,
    alertLevel: 1,
  });
}

function removeRule(index) {
  rules.value.splice(index, 1);
}

async function handleSave() {
  if (rules.value.length === 0) {
    ElMessage.warning('请至少添加一条告警规则');
    return;
  }
  for (const rule of rules.value) {
    if (!rule.metricKey || !rule.operator || rule.threshold === null) {
      ElMessage.warning('请完善所有告警规则的字段');
      return;
    }
  }
  loading.value = true;
  try {
    await updateDeviceAlertConfig(props.deviceId, { rules: rules.value });
    emit('confirm');
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.config-tip { margin-bottom: 12px; color: #909399; font-size: 13px; }
.add-rule-btn { margin-top: 12px; }
</style>
```

---

## §7 状态管理

| Store | 用途 | 关键状态 | 页面依赖 |
|-------|------|----------|:---:|
| `useAuthStore` | 角色校验 | `role` | ✅ `canOperate` + `isAdmin` 计算属性 |
| — | — | — | 设备数据 + 设备类型由组件内 `ref` 管理，无需 Store |

---

## §8 页面完成定义 (DoD)

设备管理页"完成"必须满足以下全部条件：

- [x] 设备列表分页展示，支持 10/20/50 条/页切换
- [x] 状态筛选（全部/在线/离线/故障）+ 名称模糊搜索
- [x] 状态列：ADMIN/OPERATOR 显示 ElSwitch 可切换状态；VIEWER 显示 ElTag 只读
- [x] 状态颜色：在线=绿色、离线=灰色、故障=红色
- [x] 新增设备弹窗：表单校验（名称/编码/类型必填），设备类型下拉选择
- [x] 编辑设备弹窗：表单预填充，编码只读不可修改
- [x] 删除确认：ElMessageBox 二次确认，仅 ADMIN 可操作
- [x] 告警配置弹窗：支持添加/删除规则行，动态编辑指标/运算符/阈值/告警级别
- [x] 列表更新：CRUD 操作后自动刷新列表
- [x] VIEWER 角色登录：可查看列表，无"新增/编辑/删除/告警配置"按钮，状态只读
- [x] OPERATOR 角色登录：可新增/编辑/状态切换/告警配置，不可删除
- [x] ADMIN 角色登录：全部功能可用

---

## §9 集成检查清单

| # | 检查项 | 验证方式 |
|:---:|------|------|
| 1 | 进入设备管理页 | 列表正常加载，第一页 10 条 |
| 2 | 筛选"在线"状态 | 列表只显示 status=1 的设备 |
| 3 | 输入设备名称模糊搜索 | 列表按名称过滤 |
| 4 | 点击"新增设备" | 弹窗打开，设备类型下拉已加载 |
| 5 | 新增弹窗不填名称点确认 | 表单校验提示"请输入设备名称" |
| 6 | 填完必填项点确认 | 弹窗关闭，列表刷新，新设备在列表 |
| 7 | 点击"编辑" | 弹窗打开，表单预填充，编码只读 |
| 8 | 修改设备名称点确认 | 设备名称更新，列表刷新 |
| 9 | 切换状态开关 | 状态即时切换，失败时恢复原值 |
| 10 | 点击"删除"（ADMIN） | 弹出确认框，确认后设备消失 |
| 11 | 点击"告警配置" | 弹窗打开，添加/编辑/删除规则 |
| 12 | 保存空规则 | 提示"请至少添加一条告警规则" |
| 13 | VIEWER 登录 | 无操作按钮，状态 ElTag 只读 |
| 14 | OPERATOR 登录 | 有新增/编辑/告警配置，无删除 |
| 15 | 重复设备编码提交 | 后端返回错误，ElMessage 展示 |

---

## §10 学习重点

| # | 知识点 | 文件/位置 | 说明 |
|:---:|------|------|------|
| 1 | 表单弹窗复用 | `DeviceFormDialog.vue` §6.3 | 同一组件支持 create/edit 两种模式 |
| 2 | 设备类型下拉数据源 | `views/device/index.vue` §6.1 | `onMounted` 中预加载 `getDeviceTypes()` |
| 3 | 乐观更新 | `views/device/index.vue` §6.1 | 状态切换先本地改，失败回滚 |
| 4 | 三级权限控制 | 整页 | VIEWER(只读)/OPERATOR(无删除)/ADMIN(全功能) |
| 5 | 动态表格行 | `AlertConfigDialog.vue` §6.4 | 告警规则动态 add/remove 行 |
| 6 | ElMessageBox 二次确认 | `views/device/index.vue` §6.1 | `.confirm()` 异步等待用户确认 |

---

## §11 面试准备

### §11.1 核心交互流程

1. 进入设备管理 → `onMounted` → 并行加载设备列表 + 设备类型 → 表格渲染
2. 新增 → 弹窗 → 选择设备类型 + 填写表单 → POST `/api/devices` → 刷新列表
3. 编辑 → 弹窗预填充 → 修改字段 → PUT `/api/devices/{id}` → 刷新列表
4. 删除 (ADMIN) → ElMessageBox 确认 → DELETE `/api/devices/{id}` → 刷新列表
5. 状态切换 → ElSwitch onChange → PUT `/api/devices/{id}/status?status=X` → 乐观更新
6. 告警配置 → 弹窗编辑规则 → PUT `/api/devices/{id}/alert-config` → 保存

### §11.2 面试模拟问题

**问题 1**: 设备状态切换使用了"乐观更新"，如果后端更新失败，如何保证前端状态一致性？

> 参考答案：在 `handleStatusChange` 中先保存 `oldStatus`，调用接口成功后由后端确认；如果接口失败（catch），使用 `device.status = oldStatus` 恢复原值。这样用户在视觉上瞬间看到状态变化，失败时才回滚，兼顾体验和数据一致性。

**问题 2**: 新增设备和编辑设备使用同一个弹窗组件，如何区分两种模式？

> 参考答案：通过 `mode` prop（`'create' | 'edit'`）区分。`create` 模式下表单为空，`edit` 模式下在 `@open` 事件中预填充 `device` prop 的数据。同时编辑模式下设备编码字段 `disabled`，防止修改唯一编码。

**问题 3**: 为什么删除操作只对 ADMIN 开放？

> 参考答案：SecurityConfig 中配置了 `HttpMethod.DELETE → hasRole("ADMIN")`，所有 DELETE 操作仅 ADMIN 可执行。前端通过 `v-if="authStore.role === 'ADMIN'"` 隐藏删除按钮，避免 OPERATOR 和 VIEWER 看到不可用的按钮，减少困惑。

---

## §12 页面复杂度评估

| 维度 | 评级 | 说明 |
|------|:---:|------|
| 组件数量 | ⭐⭐⭐⭐ | 1 页面 + 4 子组件 + 设备类型 API |
| 接口数量 | ⭐⭐⭐ | 6 个接口（page/create/update/delete/status/alert-config） |
| 状态复杂度 | ⭐⭐⭐⭐ | 筛选 + 分页 + 双弹窗 + 乐观更新 |
| 权限逻辑 | ⭐⭐⭐⭐⭐ | **最复杂**：三级角色区分（VIEWER/OPERATOR/ADMIN） |
| 交互复杂度 | ⭐⭐⭐⭐ | CRUD 全流程 + 弹窗复用 + 动态规则表 |
| **综合** | **⭐⭐⭐⭐** | **最高复杂度页面，作为面试核心代码** |

---

## §13 硬性约束

- **禁止**在编辑模式下允许修改设备编码（`deviceCode` 为唯一标识，新增后不可改）
- **禁止**在删除操作前不弹出二次确认
- **禁止**在 VIEWER 角色渲染任何写操作按钮/组件
- **禁止**在告警规则配置弹窗中提交空规则列表
- **禁止**在状态切换中使用 `v-model` 双向绑定（必须通过 `@change` 事件异步调用后端）
- **必须**在 `onMounted` 中并行加载设备类型数据
- **必须**在新增/编辑/删除/状态切换操作后刷新设备列表

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*