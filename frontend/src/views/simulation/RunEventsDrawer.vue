<template>
  <el-drawer
    :model-value="visible"
    :title="`生成数据追溯 · 运行 #${run?.id || ''}`"
    size="min(760px, 100%)"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadEvents"
  >
    <el-descriptions v-if="run" :column="3" border class="run-summary">
      <el-descriptions-item label="状态">{{ statusLabel(run.status) }}</el-descriptions-item>
      <el-descriptions-item label="步骤">{{ run.successSteps || 0 }} / {{ run.totalSteps || 0 }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ run.startTime || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="run.errorMessage" label="失败原因" :span="3">{{ run.errorMessage }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-loading="loading" :data="events" row-key="id">
      <el-table-column prop="generatedAt" label="生成时间" min-width="170" />
      <el-table-column prop="deviceId" label="设备ID" width="90" />
      <el-table-column label="点位" min-width="180">
        <template #default="{ row }">{{ row.pointName || row.pointCode || `点位 #${row.pointId}` }}</template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="120" />
      <el-table-column label="采样值" width="130">
        <template #default="{ row }">{{ row.sampleValue ?? '-' }} {{ row.unit || '' }}</template>
      </el-table-column>
      <el-table-column prop="metricSampleId" label="采样记录ID" width="130" />
    </el-table>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { getSimulationRunEvents } from '@/api/simulation'

defineOptions({ name: 'SimulationRunEventsDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  run: { type: Object, default: null },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const events = ref([])

async function loadEvents() {
  if (!props.run?.id) return
  loading.value = true
  try {
    const result = await getSimulationRunEvents(props.run.id)
    events.value = result.data || []
  } finally {
    loading.value = false
  }
}

function statusLabel(status) {
  return {
    PENDING: '等待执行',
    RUNNING: '运行中',
    SUCCESS: '成功',
    FAILED: '失败',
    CANCELLED: '已取消',
  }[status] || status
}
</script>

<style scoped>
.run-summary {
  margin-bottom: 18px;
}
</style>
