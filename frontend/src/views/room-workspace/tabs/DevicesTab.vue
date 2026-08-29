<template>
  <div class="devices-tab">
    <filter-bar>
      <el-select v-model="filter.typeId" placeholder="全部类型" clearable style="width: 140px" @change="fetchDevices">
        <el-option v-for="t in deviceTypes" :key="t.id" :label="t.typeName" :value="t.id" />
      </el-select>
      <el-input
        v-model="filter.name"
        placeholder="设备名称搜索"
        clearable
        style="width: 200px"
        @keyup.enter="fetchDevices"
        @clear="fetchDevices"
      />
      <div style="flex: 1" />
      <el-button v-if="canWrite" type="primary" :icon="Plus" @click="openCreate">新增设备</el-button>
    </filter-bar>

    <div v-loading="loading" class="device-grid">
      <div
        v-for="d in devices"
        :key="d.id"
        :class="['device-card', { 'device-card--selected': d.id === selectedDeviceId }]"
        @click="selectDevice(d)"
      >
        <div class="device-card-head">
          <span class="device-card-name">{{ d.deviceName }}</span>
          <state-dot :state="dotState(d.status)" />
        </div>
        <div class="device-card-code num">{{ d.deviceCode }}</div>
        <!-- 最新值迷你条：指标名=值 单位（换行显示，不截断） -->
        <div class="device-card-latest">
          <div v-for="v in latestOf(d.id)" :key="v.pointId" class="latest-line">
            <span class="latest-point">{{ v.pointName }}</span>
            <span class="latest-value num">{{ v.latestValue }}<span v-if="v.unit" class="latest-unit">{{ v.unit }}</span></span>
          </div>
          <div v-if="!latestOf(d.id).length" class="latest-empty">暂无上报数据</div>
        </div>
        <div class="device-card-meta">
          <span class="meta-line">{{ typeLabel(d.typeId) }}</span>
          <span class="meta-line meta-wrap">{{ d.location || '未设置位置' }}</span>
        </div>
        <div class="device-card-actions" @click.stop>
          <el-button link type="primary" size="small" @click="selectDevice(d)">数据</el-button>
          <el-button v-if="canWrite" link type="primary" size="small" @click="openEdit(d)">编辑</el-button>
          <el-button link type="success" size="small" @click="openPoints(d)">指标</el-button>
          <el-button v-if="canDelete" link type="danger" size="small" @click="handleDelete(d)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && devices.length === 0" description="本机房暂无设备" class="grid-empty" />
    </div>

    <!-- 设备数据抽屉：最新值卡片 + 趋势（数据监控功能并入） -->
    <el-drawer
      :model-value="dataDrawerVisible"
      :title="selectedDevice ? `设备数据 · ${selectedDevice.deviceName}` : '设备数据'"
      size="min(560px, 100%)"
      destroy-on-close
      @update:model-value="dataDrawerVisible = $event"
    >
      <template v-if="selectedDevice">
        <div class="drawer-latest">
          <div class="section-heading"><h3>最新值</h3></div>
          <div v-loading="drawerLoading" class="latest-grid">
            <div v-for="v in drawerLatest" :key="v.pointId" class="latest-card">
              <div class="latest-card-head">
                <span class="latest-card-name">{{ v.pointName }}</span>
                <span class="latest-card-code num">{{ v.pointCode }}</span>
              </div>
              <stat-value :value="v.latestValue" :unit="v.unit" size="lg" />
              <span class="latest-card-time">{{ relativeTime(v.latestTime) }}</span>
            </div>
            <el-empty v-if="!drawerLoading && drawerLatest.length === 0" :image-size="64" description="暂无上报数据" />
          </div>
        </div>
        <div class="drawer-trend">
          <div class="section-heading">
            <h3>趋势</h3>
            <div class="trend-controls">
              <el-select v-model="trendPointId" size="small" style="width: 150px" @change="loadAllTrendData">
                <el-option v-for="p in numericPoints" :key="p.id" :label="p.pointName" :value="p.id" />
              </el-select>
              <el-radio-group v-model="currentRange" size="small" @change="loadAllTrendData">
                <el-radio-button value="1h">1小时</el-radio-button>
                <el-radio-button value="6h">6小时</el-radio-button>
                <el-radio-button value="24h">24小时</el-radio-button>
                <el-radio-button value="7d">7天</el-radio-button>
                <el-radio-button value="30d">30天</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <data-trend-chart :data="trendData" :range="currentRange" :unit="trendUnit" />
          <el-table v-loading="historyLoading" :data="history" size="small" max-height="300">
            <el-table-column label="采样值" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ row.sampleValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="sampleTime" label="采样时间" min-width="160" />
          </el-table>
        </div>
      </template>
    </el-drawer>

    <!-- 编辑/新增设备（复用现有对话框，机房锁定本工作台） -->
    <device-editor-dialog
      v-model:visible="editorVisible"
      :mode="editorMode"
      :device="editingDevice"
      :device-types="deviceTypes"
      :rooms="editorRooms"
      :alarm-templates="alarmTemplates"
      @confirm="handleEditorConfirm"
    />
    <device-point-drawer v-model:visible="pointDrawerVisible" :device="pointDevice" :can-manage="canDelete" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createDevice, deleteDevice, getDevicePage, updateDevice } from '@/api/device'
import { getDeviceTypes } from '@/api/deviceType'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import { getDeviceMetricLatest } from '@/api/metricLatest'
import { getDevicePointBindings } from '@/api/devicePoint'
import { getMetricSamplePage, getMetricSampleTrend } from '@/api/metricSample'
import { bindDeviceTemplate, listEnabledTemplates } from '@/api/alarmTemplate'
import DeviceEditorDialog from '@/views/device/DeviceEditorDialog.vue'
import DevicePointDrawer from '@/views/device/DevicePointDrawer.vue'
import DataTrendChart from '@/components/data-monitor/DataTrendChart.vue'

defineOptions({ name: 'RoomDevicesTab' })

const props = defineProps({
  roomId: { type: Number, required: true },
  room: { type: Object, default: null },
})
const emit = defineEmits(['refresh-room'])

const authStore = useAuthStore()
const canWrite = computed(() => ['ADMIN', 'OPERATOR'].includes(authStore.normalizedRole))
const canDelete = computed(() => authStore.isAdmin)

const filter = reactive({ typeId: null, name: '' })
const loading = ref(false)
const devices = ref([])
const deviceTypes = ref([])
const alarmTemplates = ref([])
const latestByDevice = ref({})

const dataDrawerVisible = ref(false)
const drawerLoading = ref(false)
const selectedDevice = ref(null)
const selectedDeviceId = computed(() => selectedDevice.value?.id || null)
const drawerLatest = ref([])
const pointDefinitions = ref([])
const bindings = ref([])
const trendPointId = ref(null)
const historyLoading = ref(false)
const history = ref([])
const currentRange = ref('24h')
const trendData = ref([])

const editorVisible = ref(false)
const editorMode = ref('create')
const editingDevice = ref(null)
const pointDrawerVisible = ref(false)
const pointDevice = ref(null)

// 编辑对话框机房锁定为本工作台机房（下拉只有一项）
const editorRooms = computed(() =>
  props.room ? [{ id: props.roomId, roomName: props.room.roomName, status: 0 }] : [],
)

const TYPE_LABELS = { 1: 'UPS', 2: '电表', 3: '精密空调', 4: '温湿度', 5: '漏水检测' }
function typeLabel(id) {
  return deviceTypes.value.find((t) => t.id === id)?.typeName || TYPE_LABELS[id] || `类型 ${id}`
}
function dotState(status) {
  return { 0: 'offline', 1: 'online', 2: 'fault' }[status] || 'offline'
}
function latestOf(deviceId) {
  return latestByDevice.value[deviceId] || []
}
function relativeTime(time) {
  if (!time) return '从未上报'
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 0) return '刚刚'
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${seconds} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

const numericPoints = computed(() => {
  const pointMap = Object.fromEntries(pointDefinitions.value.map((p) => [p.id, p]))
  return bindings.value
    .filter((b) => b.status === 0)
    .map((b) => pointMap[b.pointId])
    .filter((p) => p && p.dataType === 'NUMBER')
})
const trendUnit = computed(() =>
  numericPoints.value.find((p) => p.id === trendPointId.value)?.unit || '',
)

async function fetchDevices() {
  loading.value = true
  try {
    const params = { current: 1, size: 200, cloudRoomId: props.roomId }
    const res = await getDevicePage(params)
    let records = res.data?.records || []
    if (filter.typeId != null) records = records.filter((d) => d.typeId === filter.typeId)
    if (filter.name.trim()) {
      const kw = filter.name.trim().toLowerCase()
      records = records.filter((d) => (d.deviceName || '').toLowerCase().includes(kw))
    }
    devices.value = records
    // 各设备最新值（指标名+值+单位）
    const entries = await Promise.all(
      records.map(async (d) => {
        const r = await getDeviceMetricLatest(d.id).catch(() => null)
        return [d.id, r?.data || []]
      }),
    )
    latestByDevice.value = Object.fromEntries(entries)
  } finally {
    loading.value = false
  }
}

async function fetchReferences() {
  const [typeRes, pointRes, tplRes] = await Promise.all([
    getDeviceTypes(),
    getPointDefinitionPage({ current: 1, size: 200 }),
    listEnabledTemplates().catch(() => ({ data: [] })),
  ])
  deviceTypes.value = typeRes.data || []
  pointDefinitions.value = pointRes.data?.records || []
  alarmTemplates.value = tplRes.data || []
}

async function selectDevice(device) {
  selectedDevice.value = device
  dataDrawerVisible.value = true
  drawerLoading.value = true
  try {
    const [latestRes, bindingRes] = await Promise.all([
      getDeviceMetricLatest(device.id),
      getDevicePointBindings(device.id),
    ])
    drawerLatest.value = latestRes.data || []
    bindings.value = bindingRes.data || []
    if (!numericPoints.value.some((p) => p.id === trendPointId.value)) {
      trendPointId.value = numericPoints.value[0]?.id || null
    }
    await loadAllTrendData()
  } finally {
    drawerLoading.value = false
  }
}

const RANGE_MS = { '1h': 3600000, '6h': 21600000, '24h': 86400000, '7d': 604800000, '30d': 2592000000 }

// 趋势图：走分桶聚合端点，返回 ≤200 桶（均值/最小/最大）
async function loadTrend() {
  if (!selectedDevice.value || !trendPointId.value) {
    trendData.value = []
    return
  }
  const end = new Date()
  const start = new Date(end.getTime() - RANGE_MS[currentRange.value])
  try {
    const res = await getMetricSampleTrend({
      deviceId: selectedDevice.value.id,
      pointId: trendPointId.value,
      startTime: toLocal(start),
      endTime: toLocal(end),
    })
    trendData.value = res.data || []
  } catch {
    trendData.value = []
  }
}

// 采样明细表：保留分页查询（最新 50 条），与趋势图数据源解耦
async function loadHistory() {
  if (!selectedDevice.value || !trendPointId.value) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    const end = new Date()
    const start = new Date(end.getTime() - RANGE_MS[currentRange.value])
    const res = await getMetricSamplePage({
      current: 1,
      size: 50,
      deviceId: selectedDevice.value.id,
      pointId: trendPointId.value,
      startTime: toLocal(start),
      endTime: toLocal(end),
    })
    history.value = res.data?.records || []
  } finally {
    historyLoading.value = false
  }
}

async function loadAllTrendData() {
  await Promise.all([loadTrend(), loadHistory()])
}

function toLocal(date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

function openCreate() {
  editorMode.value = 'create'
  editingDevice.value = { cloudRoomId: props.roomId }
  editorVisible.value = true
}

function openEdit(device) {
  editorMode.value = 'edit'
  editingDevice.value = { ...device }
  editorVisible.value = true
}

async function handleEditorConfirm(data) {
  try {
    if (editorMode.value === 'create') {
      await createDevice({ ...data, cloudRoomId: props.roomId })
      ElMessage.success('设备已创建')
    } else {
      const { alarmTemplateId, ...deviceData } = data
      await updateDevice(editingDevice.value.id, { ...deviceData, cloudRoomId: props.roomId })
      const targetId = alarmTemplateId || null
      if ((editingDevice.value.alarmTemplateId || null) !== targetId) {
        await bindDeviceTemplate(editingDevice.value.id, targetId)
      }
      ElMessage.success('设备已更新')
    }
    editorVisible.value = false
    emit('refresh-room')
    await fetchDevices()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

function openPoints(device) {
  pointDevice.value = device
  pointDrawerVisible.value = true
}

async function handleDelete(device) {
  try {
    await ElMessageBox.confirm(`删除设备「${device.deviceName}」？`, '删除确认', { type: 'warning' })
    await deleteDevice(device.id)
    ElMessage.success('设备已删除')
    emit('refresh-room')
    await fetchDevices()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

watch(() => props.roomId, () => {
  dataDrawerVisible.value = false
  void fetchDevices()
})

onMounted(() => {
  void fetchDevices()
  void fetchReferences()
})
</script>

<style scoped>
.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
  min-height: 120px;
}

.grid-empty {
  grid-column: 1 / -1;
}

.device-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.device-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.device-card--selected {
  border-left-color: var(--color-primary);
}

.device-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.device-card-name {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-card-code {
  color: var(--color-text-3);
  font-size: 11px;
}

.device-card-latest {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  background: var(--color-surface-2);
  border-radius: var(--radius-sm);
}

.latest-line {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.latest-point {
  color: var(--color-text-2);
}

.latest-value {
  color: var(--color-text);
  font-weight: 600;
}

.latest-unit {
  color: var(--color-text-3);
  font-weight: 400;
  margin-left: 3px;
  font-size: 11px;
}

.latest-empty {
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
  padding: 4px 0;
}

.device-card-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* 长文本换行显示（用户决策：卡片不截断） */
.meta-line {
  color: var(--color-text-2);
  font-size: 12px;
  word-break: break-word;
  white-space: normal;
  line-height: 1.5;
}

.meta-wrap {
  color: var(--color-text-3);
}

.device-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  border-top: 1px solid var(--color-border-light);
  padding-top: 8px;
  margin-top: 2px;
}

/* 抽屉内最新值卡片 */
.latest-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
  margin-bottom: 18px;
  min-height: 70px;
}

.latest-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.latest-card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.latest-card-name {
  color: var(--color-text-2);
  font-size: 13px;
}

.latest-card-code {
  color: var(--color-text-3);
  font-size: 11px;
}

.latest-card-time {
  color: var(--color-text-3);
  font-size: 12px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.section-heading h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 15px;
}

.trend-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>
