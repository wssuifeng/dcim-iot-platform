<template>
  <div class="alarms-tab">
    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="状态">
        <el-select v-model="filters.state" clearable class="status-select" @change="fetchAlarms">
          <el-option v-for="item in states" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="级别">
        <el-select v-model="filters.alarmLevel" clearable class="level-select" @change="fetchAlarms">
          <el-option v-for="item in levels" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备">
        <el-select v-model="filters.deviceId" clearable filterable class="device-select" @change="fetchAlarms">
          <el-option v-for="item in devices" :key="item.id" :label="item.deviceName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :icon="Refresh" @click="fetchAlarms">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="alarms" row-key="id" :row-class-name="alarmRowClass">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="状态" width="95">
        <template #default="{ row }"><el-tag :type="stateType(row.state)">{{ stateLabel(row.state) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="级别" width="80">
        <template #default="{ row }">
          <span :class="['level-tag', `alarm-band-l${row.alarmLevel}`]">{{ levelLabel(row.alarmLevel) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="设备" min-width="110">
        <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
      </el-table-column>
      <el-table-column prop="alarmType" label="告警类型" width="100" />
      <el-table-column prop="alarmMessage" label="告警描述" min-width="180">
        <template #default="{ row }">
          <span class="desc-text" :title="row.alarmMessage">{{ row.alarmMessage }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="triggeredTime" label="触发时间" width="165" />
      <el-table-column label="操作" width="250" fixed="right">
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
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getDevicePage } from '@/api/device'
import {
  acknowledgeAlarmEvent,
  closeAlarmEvent,
  getAlarmEventPage,
  recoverAlarmEvent,
  startHandlingAlarmEvent,
} from '@/api/alarmEvent'
import { onSummary } from '@/api/realtime'
import AlarmActionDialog from '@/views/alert/AlarmActionDialog.vue'
import AlarmDetailDrawer from '@/views/alert/AlarmDetailDrawer.vue'

defineOptions({ name: 'RoomAlarmsTab' })

const props = defineProps({
  roomId: { type: Number, required: true },
})
const emit = defineEmits(['room-alarms-loaded'])

const authStore = useAuthStore()
const canHandle = computed(() => ['ADMIN', 'OPERATOR'].includes(authStore.normalizedRole))
const loading = ref(false)
const submitting = ref(false)
const alarms = ref([])
const devices = ref([])
const total = ref(0)
const pagination = reactive({ current: 1, size: 10 })
const filters = reactive({ state: '', alarmLevel: null, deviceId: null })
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
    { label: '处理', value: 'handling', type: 'primary' },
    { label: '恢复', value: 'recover', type: 'success' },
    { label: '关闭', value: 'close', type: 'danger' },
  ],
  HANDLING: [
    { label: '恢复', value: 'recover', type: 'success' },
    { label: '关闭', value: 'close', type: 'danger' },
  ],
  RECOVERED: [{ label: '关闭', value: 'close', type: 'danger' }],
})

async function fetchAlarms() {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      cloudRoomId: props.roomId,
    }
    if (filters.state) params.state = filters.state
    if (filters.alarmLevel != null) params.alarmLevel = filters.alarmLevel
    if (filters.deviceId != null) params.deviceId = filters.deviceId
    const res = await getAlarmEventPage(params)
    alarms.value = res.data?.records || []
    total.value = res.data?.total || 0
    // 活动告警数回传工作台头部徽标（TRIGGERED 口径）
    if (!filters.state && !filters.alarmLevel && !filters.deviceId) {
      const activeRes = await getAlarmEventPage({
        current: 1, size: 1, cloudRoomId: props.roomId, state: 'TRIGGERED',
      }).catch(() => null)
      emit('room-alarms-loaded', Number(activeRes?.data?.total || 0))
    }
  } finally {
    loading.value = false
  }
}

async function loadReferences() {
  const res = await getDevicePage({ current: 1, size: 200, cloudRoomId: props.roomId })
  devices.value = res.data?.records || []
}

function deviceName(id) {
  return devices.value.find((item) => item.id === id)?.deviceName || `设备 #${id}`
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

// 活跃告警行左侧级别色条（theme.css 全局规则挂 td:first-child）
function alarmRowClass({ row }) {
  const active = ['TRIGGERED', 'ACKED', 'HANDLING'].includes(row.state)
  return active ? `alarm-row-band alarm-band-l${row.alarmLevel}` : ''
}

function availableActions(state) {
  return canHandle.value ? actionOptions[state] || [] : []
}

function openDetail(row) {
  currentAlarm.value = row
  detailVisible.value = true
}

function openAction(row, action) {
  currentAlarm.value = row
  currentAction.value = action
  actionVisible.value = true
}

async function submitAction(note) {
  submitting.value = true
  try {
    const id = currentAlarm.value.id
    const actions = {
      ack: acknowledgeAlarmEvent,
      handling: startHandlingAlarmEvent,
      recover: recoverAlarmEvent,
      close: closeAlarmEvent,
    }
    await actions[currentAction.value](id, { note })
    ElMessage.success('操作成功')
    actionVisible.value = false
    await fetchAlarms()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

function handleSummary(summary) {
  if (summary.resourceType === 'ALARM_EVENT') void fetchAlarms()
}

onMounted(async () => {
  await Promise.all([loadReferences(), fetchAlarms()])
  removeSummaryListener = onSummary(handleSummary)
})

onBeforeUnmount(() => removeSummaryListener?.())
</script>

<style scoped>
.filter-bar {
  padding: 14px 16px;
  margin-bottom: 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.status-select,
.level-select {
  width: 120px;
}

.device-select {
  width: 170px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

/* 告警描述受控省略（EP show-overflow-tooltip 列宽压缩失配规避） */
.desc-text {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: default;
}

/* 级别 pill（五级色带令牌） */
.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--alarm-band, var(--color-text-2));
  background: var(--alarm-band-bg, var(--color-surface-2));
  font-weight: 500;
}
</style>
