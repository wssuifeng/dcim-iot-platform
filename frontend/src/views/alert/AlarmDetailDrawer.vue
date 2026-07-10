<template>
  <el-drawer
    :model-value="visible"
    :title="`告警详情 · #${alarmId || ''}`"
    size="min(760px, 100%)"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadDetail"
  >
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border class="detail-grid">
        <el-descriptions-item label="当前状态">
          <el-tag :type="stateType(detail.state)">{{ stateLabel(detail.state) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="告警级别">{{ levelLabel(detail.alarmLevel) }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ deviceLabel(detail.deviceId) }}</el-descriptions-item>
        <el-descriptions-item label="点位ID">{{ detail.pointId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="告警类型">{{ detail.alarmType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="组织 / 机房">
          {{ organizationLabel(detail.organizationId) }} / {{ roomLabel(detail.cloudRoomId) }}
        </el-descriptions-item>
        <el-descriptions-item label="告警描述" :span="2">{{ detail.alarmMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发时间">{{ detail.triggeredTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="恢复时间">{{ detail.recoveredTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ detail.closedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <section class="history-section">
        <h3>状态流转</h3>
        <el-timeline v-if="transitions.length">
          <el-timeline-item v-for="item in transitions" :key="item.id" :timestamp="item.createTime" placement="top">
            <strong>{{ stateLabel(item.fromState) }} → {{ stateLabel(item.toState) }}</strong>
            <p>{{ item.remark || '无备注' }} · 用户 #{{ item.operatorUserId }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无流转记录" :image-size="70" />
      </section>

      <section class="history-section">
        <h3>处理记录</h3>
        <el-table :data="records" size="small" row-key="id">
          <el-table-column prop="createTime" label="处理时间" min-width="170" />
          <el-table-column prop="handlingAction" label="动作" width="120" />
          <el-table-column label="处理人" width="100">
            <template #default="{ row }">#{{ row.handlerUserId }}</template>
          </el-table-column>
          <el-table-column prop="handlingNote" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import {
  getAlarmEvent,
  getAlarmEventHandlingRecords,
  getAlarmEventTransitions,
} from '@/api/alarmEvent'

defineOptions({ name: 'AlarmDetailDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  alarmId: { type: [Number, String], default: null },
  devices: { type: Array, default: () => [] },
  organizations: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const detail = ref(null)
const transitions = ref([])
const records = ref([])

async function loadDetail() {
  if (!props.alarmId) return
  loading.value = true
  try {
    const [detailResult, transitionResult, recordResult] = await Promise.all([
      getAlarmEvent(props.alarmId),
      getAlarmEventTransitions(props.alarmId),
      getAlarmEventHandlingRecords(props.alarmId),
    ])
    detail.value = detailResult.data || null
    transitions.value = transitionResult.data || []
    records.value = recordResult.data || []
  } finally {
    loading.value = false
  }
}

function stateLabel(state) {
  if (!state) return '初始触发'
  return { TRIGGERED: '已触发', ACKED: '已确认', HANDLING: '处理中', RECOVERED: '已恢复', CLOSED: '已关闭' }[state] || state
}

function stateType(state) {
  return { TRIGGERED: 'danger', ACKED: 'warning', HANDLING: 'primary', RECOVERED: 'success', CLOSED: 'info' }[state]
}

function levelLabel(level) {
  return ({ 1: '紧急', 2: '严重', 3: '一般', 4: '警告', 5: '提示' })[level] || level
}

function deviceLabel(id) {
  return props.devices.find((item) => item.id === id)?.deviceName || `设备 #${id}`
}

function organizationLabel(id) {
  return props.organizations.find((item) => item.id === id)?.orgName || `组织 #${id}`
}

function roomLabel(id) {
  if (!id) return '全部机房'
  return props.rooms.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

defineExpose({ loadDetail })
</script>

<style scoped>
.detail-grid {
  margin-bottom: 24px;
}

.history-section {
  padding-top: 18px;
  border-top: 1px solid #ebeef5;
}

.history-section + .history-section {
  margin-top: 18px;
}

.history-section h3 {
  margin: 0 0 14px;
  color: #303133;
  font-size: 16px;
}

.history-section p {
  margin: 5px 0 0;
  color: #606266;
}
</style>
