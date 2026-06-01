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
import { ALERT_LEVEL } from '@/utils/constants'

defineProps({
  alerts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canHandle: { type: Boolean, default: false },
})

const emit = defineEmits(['selection-change', 'handle'])

function onSelectionChange(selection) {
  emit('selection-change', selection.map(item => item.id))
}

function rowClassName({ row }) {
  if (row.alertLevel === 1) return 'alert-row-emergency'
  if (row.alertLevel === 2) return 'alert-row-critical'
  return ''
}

function levelTagType(level) {
  const map = { 1: 'danger', 2: 'warning', 3: '', 4: 'info', 5: 'info' }
  return map[level] || ''
}
</script>

<style>
.alert-row-emergency { background-color: #FEF0F0 !important; }
.alert-row-critical { background-color: #FDF6EC !important; }
</style>
