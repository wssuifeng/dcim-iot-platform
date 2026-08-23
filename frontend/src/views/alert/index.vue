<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>告警管理</h1>
        <p>权威状态流转、处理记录和范围内告警事件</p>
      </div>
      <el-button :icon="Refresh" @click="fetchAlarms">刷新</el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="状态">
        <el-select v-model="filters.state" clearable class="status-select">
          <el-option v-for="item in states" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="级别">
        <el-select v-model="filters.alarmLevel" clearable class="level-select">
          <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备">
        <el-select v-model="filters.deviceId" clearable filterable class="device-select">
          <el-option v-for="item in devices" :key="item.id" :label="item.deviceName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="组织">
        <el-select v-model="filters.organizationId" clearable filterable class="organization-select">
          <el-option v-for="item in organizations" :key="item.id" :label="item.orgName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="机房">
        <el-select v-model="filters.cloudRoomId" clearable filterable class="room-select">
          <el-option v-for="item in rooms" :key="item.id" :label="item.roomName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="alarms" row-key="id">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="状态" width="105">
        <template #default="{ row }"><el-tag :type="stateType(row.state)">{{ stateLabel(row.state) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="级别" width="90">
        <template #default="{ row }"><el-tag :type="levelType(row.alarmLevel)" effect="plain">{{ levelLabel(row.alarmLevel) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="设备" min-width="150">
        <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
      </el-table-column>
      <el-table-column prop="alarmType" label="告警类型" min-width="140" />
      <el-table-column prop="alarmMessage" label="告警描述" min-width="240" show-overflow-tooltip />
      <el-table-column label="组织 / 机房" min-width="200">
        <template #default="{ row }">{{ organizationName(row.organizationId) }} / {{ roomName(row.cloudRoomId) }}</template>
      </el-table-column>
      <el-table-column prop="triggeredTime" label="触发时间" min-width="170" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
          <el-button
            v-for="action in availableActions(row.state)"
            :key="action.value"
            link
            :type="action.type"
            @click="openAction(row, action.value)"
          >
            {{ action.label }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchAlarms"
    />

    <alarm-action-dialog
      v-model:visible="actionVisible"
      :alarm="currentAlarm"
      :action="currentAction"
      :submitting="submitting"
      @confirm="submitAction"
    />
    <alarm-detail-drawer
      ref="detailDrawerRef"
      v-model:visible="detailVisible"
      :alarm-id="currentAlarm?.id"
      :devices="devices"
      :organizations="organizations"
      :rooms="rooms"
    />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import { getOrganizationPage } from '@/api/organization'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getDevicePage } from '@/api/device'
import {
  acknowledgeAlarmEvent,
  closeAlarmEvent,
  getAlarmEventPage,
  recoverAlarmEvent,
  startHandlingAlarmEvent,
} from '@/api/alarmEvent'
import { onSummary } from '@/api/realtime'
import AlarmActionDialog from './AlarmActionDialog.vue'
import AlarmDetailDrawer from './AlarmDetailDrawer.vue'

defineOptions({ name: 'AlarmEventManagement' })

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const scopeStore = useScopeStore()
const role = computed(() => scopeStore.role || authStore.normalizedRole)
const canHandle = computed(() => ['ORG_ADMIN', 'OPERATOR'].includes(role.value) && scopeStore.hasWritePermission)
const loading = ref(false)
const submitting = ref(false)
const alarms = ref([])
const devices = ref([])
const organizations = ref([])
const rooms = ref([])
const total = ref(0)
const pagination = reactive({ current: 1, size: 10 })
const filters = reactive({ state: '', alarmLevel: null, deviceId: null, organizationId: null, cloudRoomId: null })
const currentAlarm = ref(null)
const currentAction = ref('ack')
const actionVisible = ref(false)
const detailVisible = ref(false)
const detailDrawerRef = ref(null)
let removeSummaryListener = null
const states = Object.freeze([
  { label: '已触发', value: 'TRIGGERED' },
  { label: '已确认', value: 'ACKED' },
  { label: '处理中', value: 'HANDLING' },
  { label: '已恢复', value: 'RECOVERED' },
  { label: '已关闭', value: 'CLOSED' },
])
const levels = Object.freeze([
  { label: '紧急', value: 1 },
  { label: '严重', value: 2 },
  { label: '一般', value: 3 },
  { label: '警告', value: 4 },
  { label: '提示', value: 5 },
])
const actionOptions = Object.freeze({
  TRIGGERED: [
    { label: '确认', value: 'ack', type: 'warning' },
    { label: '恢复', value: 'recover', type: 'success' },
    { label: '关闭', value: 'close', type: 'danger' },
  ],
  ACKED: [
    { label: '开始处理', value: 'handling', type: 'primary' },
    { label: '恢复', value: 'recover', type: 'success' },
    { label: '关闭', value: 'close', type: 'danger' },
  ],
  HANDLING: [
    { label: '恢复', value: 'recover', type: 'success' },
    { label: '关闭', value: 'close', type: 'danger' },
  ],
  RECOVERED: [{ label: '关闭', value: 'close', type: 'danger' }],
  CLOSED: [],
})

async function loadReferences() {
  const [deviceResult, organizationResult, roomResult] = await Promise.all([
    getDevicePage({ current: 1, size: 100 }),
    getOrganizationPage({ current: 1, size: 100 }),
    getCloudRoomPage({ current: 1, size: 100 }),
  ])
  devices.value = deviceResult.data?.records || []
  organizations.value = organizationResult.data?.records || []
  rooms.value = roomResult.data?.records || []
}

async function fetchAlarms() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== null && value !== '') params[key] = value
    })
    const result = await getAlarmEventPage(params)
    alarms.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.current = 1
  void fetchAlarms()
}

function resetFilters() {
  Object.assign(filters, { state: '', alarmLevel: null, deviceId: null, organizationId: null, cloudRoomId: null })
  pagination.current = 1
  void fetchAlarms()
}

function availableActions(state) {
  return canHandle.value ? actionOptions[state] || [] : []
}

function openAction(row, action) {
  currentAlarm.value = row
  currentAction.value = action
  actionVisible.value = true
}

function openDetail(row) {
  currentAlarm.value = row
  detailVisible.value = true
  if (route.query.id !== String(row.id)) {
    void router.replace({ query: { ...route.query, id: String(row.id) } })
  }
}

async function submitAction({ action, note }) {
  const handlers = {
    ack: acknowledgeAlarmEvent,
    handling: startHandlingAlarmEvent,
    recover: recoverAlarmEvent,
    close: closeAlarmEvent,
  }
  submitting.value = true
  try {
    await handlers[action](currentAlarm.value.id, note ? { note } : {})
    actionVisible.value = false
    ElMessage.success('告警状态已更新')
    await fetchAlarms()
    if (detailVisible.value) await detailDrawerRef.value?.loadDetail()
  } finally {
    submitting.value = false
  }
}

async function handleSummary(summary) {
  if (summary.resourceType !== 'AlarmEvent') return
  await fetchAlarms()
  if (detailVisible.value && String(currentAlarm.value?.id) === String(summary.resourceId)) {
    await detailDrawerRef.value?.loadDetail()
  }
}

function stateLabel(state) {
  return states.find((item) => item.value === state)?.label || state
}

function stateType(state) {
  return { TRIGGERED: 'danger', ACKED: 'warning', HANDLING: 'primary', RECOVERED: 'success', CLOSED: 'info' }[state]
}

function levelLabel(level) {
  return levels.find((item) => item.value === level)?.label || level
}

function levelType(level) {
  return { 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info', 5: 'info' }[level]
}

function deviceName(id) {
  return devices.value.find((item) => item.id === id)?.deviceName || `设备 #${id}`
}

function organizationName(id) {
  return organizations.value.find((item) => item.id === id)?.orgName || `组织 #${id}`
}

function roomName(id) {
  if (!id) return '全部机房'
  return rooms.value.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

onMounted(async () => {
  await scopeStore.ensureCurrentScope({ silent: true })
  await Promise.all([loadReferences(), fetchAlarms()])
  removeSummaryListener = onSummary(handleSummary)
  if (route.query.id) openDetail({ id: route.query.id })
})

onBeforeUnmount(() => removeSummaryListener?.())
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
}

.filter-bar {
  padding: 14px 16px 0;
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  border-radius: var(--radius-md);
}

.status-select,
.level-select {
  width: 130px;
}

.device-select,
.organization-select,
.room-select {
  width: 190px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
