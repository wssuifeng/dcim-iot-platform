<template>
  <section class="monitor-page">
    <header class="page-header">
      <div>
        <h1>指标监控</h1>
        <p>设备点位最新值、历史采样与趋势</p>
      </div>
    </header>

    <el-card v-if="gateways.length" class="gateway-panel" shadow="never">
      <template #header>
        <span class="gateway-panel-title">接入网关</span>
      </template>
      <div class="gateway-list">
        <div v-for="gw in gateways" :key="gw.id" class="gateway-item">
          <span class="gw-dot" :class="gatewayState(gw)" />
          <span class="gw-name">{{ gw.gatewayName }}</span>
          <span class="gw-room">{{ roomLabel(gw.cloudRoomId) }}</span>
          <span class="gw-seen">{{ gatewaySeen(gw) }}</span>
        </div>
      </div>
    </el-card>

    <div class="monitor-layout">
      <aside class="device-panel">
        <device-sidebar v-model="selectedDeviceId" :devices="devices" />
      </aside>

      <main class="monitor-content">
        <el-empty v-if="!selectedDeviceId" description="请选择设备" />
        <template v-else>
          <div class="monitor-toolbar">
            <el-select
              v-model="selectedPointId"
              filterable
              placeholder="选择点位"
              class="point-select"
              @change="handlePointChange"
            >
              <el-option
                v-for="item in activePoints"
                :key="item.id"
                :label="`${item.pointName} (${item.pointCode})`"
                :value="item.id"
              />
            </el-select>
            <el-button :icon="Refresh" :loading="loadingDevice" @click="loadSelectedDevice">
              刷新
            </el-button>
            <el-button
              v-if="canWriteSample"
              type="primary"
              :icon="Plus"
              :disabled="numericPoints.length === 0"
              @click="openSampleDialog"
            >
              写入采样
            </el-button>
          </div>

          <section class="latest-section">
            <div class="section-heading">
              <h2>最新值</h2>
              <span>{{ latestValues.length }} 个点位</span>
            </div>
            <el-table v-loading="loadingDevice" :data="latestValues" row-key="pointId" size="small">
              <el-table-column prop="pointName" label="点位" min-width="180" />
              <el-table-column prop="pointCode" label="编码" min-width="150" />
              <el-table-column label="最新值" min-width="150">
                <template #default="{ row }">
                  <strong>{{ row.latestValue }}</strong>
                  <span v-if="row.unit" class="unit">{{ row.unit }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="latestTime" label="采样时间" min-width="180" />
            </el-table>
          </section>

          <template v-if="selectedPointId">
            <data-trend-chart :data="chartData" @range-change="handleRangeChange" />

            <section class="history-section">
              <div class="section-heading">
                <h2>历史采样</h2>
                <span>{{ selectedPointLabel }}</span>
              </div>
              <el-table v-loading="historyLoading" :data="history" row-key="id">
                <el-table-column prop="pointName" label="点位" min-width="170" />
                <el-table-column label="采样值" min-width="150">
                  <template #default="{ row }">
                    {{ row.sampleValue }}<span v-if="row.unit" class="unit">{{ row.unit }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="sampleTime" label="采样时间" min-width="180" />
              </el-table>
              <el-pagination
                v-if="historyTotal > 0"
                v-model:current-page="historyPagination.current"
                v-model:page-size="historyPagination.size"
                :total="historyTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                class="pagination"
                @change="loadHistory"
              />
            </section>
          </template>
        </template>
      </main>
    </div>

    <el-dialog v-model="sampleDialogVisible" title="写入指标采样" width="500px">
      <el-form ref="sampleFormRef" :model="sampleForm" :rules="sampleRules" label-width="90px">
        <el-form-item label="点位" prop="pointId">
          <el-select v-model="sampleForm.pointId" filterable class="full-width">
            <el-option
              v-for="item in numericPoints"
              :key="item.id"
              :label="`${item.pointName} (${item.pointCode})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="采样值" prop="sampleValue">
          <el-input-number
            v-model="sampleForm.sampleValue"
            :precision="3"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sampleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sampleSubmitting" @click="submitSample">
          写入
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getCredentialPage } from '@/api/ingestCredential'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getAllDevices } from '@/api/device'
import { getDevicePointBindings } from '@/api/devicePoint'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import { getDeviceMetricLatest } from '@/api/metricLatest'
import { createMetricSample, getMetricSamplePage } from '@/api/metricSample'
import DeviceSidebar from '@/components/data-monitor/DeviceSidebar.vue'
import DataTrendChart from '@/components/data-monitor/DataTrendChart.vue'

defineOptions({ name: 'ScopedMetricMonitor' })

const RANGE_MILLISECONDS = Object.freeze({
  '1h': 60 * 60 * 1000,
  '6h': 6 * 60 * 60 * 1000,
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
})
const authStore = useAuthStore()
const canWriteSample = computed(
  () =>
    ['ADMIN', 'OPERATOR'].includes(authStore.normalizedRole),
)
const devices = ref([])
const pointDefinitions = ref([])
const bindings = ref([])
const latestValues = ref([])
const selectedDeviceId = ref(null)
const selectedPointId = ref(null)
const loadingDevice = ref(false)
const historyLoading = ref(false)
const history = ref([])
const historyTotal = ref(0)
const historyPagination = reactive({ current: 1, size: 20 })
const currentRange = ref('24h')
const sampleDialogVisible = ref(false)
const sampleSubmitting = ref(false)
const sampleFormRef = ref(null)
const sampleForm = reactive({ pointId: null, sampleValue: null })
const sampleRules = {
  pointId: [{ required: true, message: '请选择点位', trigger: 'change' }],
  sampleValue: [{ required: true, message: '请输入采样值', trigger: 'change' }],
}
const pointMap = computed(() =>
  Object.fromEntries(pointDefinitions.value.map((item) => [item.id, item])),
)
const activePoints = computed(() =>
  bindings.value
    .filter((item) => item.status === 0)
    .map((item) => pointMap.value[item.pointId])
    .filter(Boolean),
)
const numericPoints = computed(() =>
  activePoints.value.filter((item) => item.dataType === 'NUMBER'),
)
const selectedPointLabel = computed(() => {
  const point = pointMap.value[selectedPointId.value]
  return point ? `${point.pointName} (${point.pointCode})` : ''
})
const chartData = computed(() =>
  history.value.map((item) => ({
    createTime: item.sampleTime,
    value: Number(item.sampleValue),
  })),
)

async function loadReferenceData() {
  const [deviceResult, pointResult] = await Promise.all([
    getAllDevices(),
    getPointDefinitionPage({ current: 1, size: 100 }),
  ])
  devices.value = deviceResult.data || []
  pointDefinitions.value = pointResult.data?.records || []
}

async function loadSelectedDevice() {
  if (!selectedDeviceId.value) return
  loadingDevice.value = true
  try {
    const [bindingResult, latestResult] = await Promise.all([
      getDevicePointBindings(selectedDeviceId.value),
      getDeviceMetricLatest(selectedDeviceId.value),
    ])
    bindings.value = bindingResult.data || []
    latestValues.value = latestResult.data || []
    if (!activePoints.value.some((item) => item.id === selectedPointId.value)) {
      selectedPointId.value = numericPoints.value[0]?.id || activePoints.value[0]?.id || null
    }
    historyPagination.current = 1
    await loadHistory()
  } finally {
    loadingDevice.value = false
  }
}

function timeRange() {
  const end = new Date()
  const start = new Date(end.getTime() - RANGE_MILLISECONDS[currentRange.value])
  return { startTime: toLocalDateTime(start), endTime: toLocalDateTime(end) }
}

function toLocalDateTime(date) {
  const offset = date.getTimezoneOffset() * 60 * 1000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

async function loadHistory() {
  if (!selectedDeviceId.value || !selectedPointId.value) {
    history.value = []
    historyTotal.value = 0
    return
  }
  historyLoading.value = true
  try {
    const result = await getMetricSamplePage({
      current: historyPagination.current,
      size: historyPagination.size,
      deviceId: selectedDeviceId.value,
      pointId: selectedPointId.value,
      ...timeRange(),
    })
    history.value = result.data?.records || []
    historyTotal.value = result.data?.total || 0
  } finally {
    historyLoading.value = false
  }
}

function handlePointChange() {
  historyPagination.current = 1
  void loadHistory()
}

function handleRangeChange(range) {
  currentRange.value = range
  historyPagination.current = 1
  void loadHistory()
}

function openSampleDialog() {
  sampleForm.pointId =
    numericPoints.value.find((item) => item.id === selectedPointId.value)?.id ||
    numericPoints.value[0]?.id ||
    null
  sampleForm.sampleValue = null
  sampleDialogVisible.value = true
}

async function submitSample() {
  const valid = await sampleFormRef.value?.validate().catch(() => false)
  if (!valid) return
  sampleSubmitting.value = true
  try {
    await createMetricSample({
      deviceId: selectedDeviceId.value,
      pointId: sampleForm.pointId,
      sampleValue: sampleForm.sampleValue,
    })
    ElMessage.success('指标采样已写入')
    sampleDialogVisible.value = false
    selectedPointId.value = sampleForm.pointId
    await loadSelectedDevice()
  } finally {
    sampleSubmitting.value = false
  }
}

watch(selectedDeviceId, () => {
  selectedPointId.value = null
  void loadSelectedDevice()
})

const gateways = ref([])
const rooms = ref([])

function gatewayState(gw) {
  if (gw.status !== 0) return 'off'
  if (!gw.lastSeenAt) return 'idle'
  return new Date(gw.lastSeenAt).getTime() > Date.now() - 5 * 60 * 1000 ? 'online' : 'offline'
}

function gatewaySeen(gw) {
  if (gw.status !== 0) return '已停用'
  if (!gw.lastSeenAt) return '从未上报'
  return `最近活跃 ${gw.lastSeenAt}`
}

function roomLabel(id) {
  return rooms.value.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

async function loadGateways() {
  const [credResult, roomResult] = await Promise.all([
    getCredentialPage({ current: 1, size: 100 }),
    import('@/api/cloudRoom').then((m) => m.getCloudRoomPage({ current: 1, size: 100 })),
  ])
  gateways.value = credResult.data?.records || []
  rooms.value = roomResult.data?.records || []
}

onMounted(loadReferenceData)
</script>

<style scoped>
.monitor-page {
  min-width: 0;
}

.page-header {
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.monitor-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 620px;
  border: 1px solid #ebeef5;
  background: var(--color-surface);
}

.device-panel {
  overflow: hidden;
  border-right: 1px solid #ebeef5;
}

.monitor-content {
  min-width: 0;
  padding: 18px;
}

.monitor-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.point-select {
  width: 300px;
}

.latest-section,
.history-section {
  margin-bottom: 18px;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
}

.section-heading span {
  color: var(--color-text-3);
  font-size: 12px;
}

.unit {
  margin-left: 4px;
  color: var(--color-text-3);
}

.pagination {
  justify-content: flex-end;
  margin-top: 14px;
}

.full-width {
  width: 100%;
}

@media (max-width: 960px) {
  .monitor-layout {
    grid-template-columns: 1fr;
  }

  .device-panel {
    max-height: 240px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border-light);
  }
}

.gateway-panel {
  margin-bottom: 16px;
}

.gateway-panel-title {
  font-weight: 600;
}

.gateway-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
}

.gateway-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.gw-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.gw-dot.online {
  background: var(--el-color-success);
}

.gw-dot.offline {
  background: var(--el-color-danger);
}

.gw-dot.idle {
  background: var(--el-color-warning);
}

.gw-dot.off {
  background: var(--el-color-info);
}

.gw-room {
  color: var(--color-text-3);
}

.gw-seen {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
