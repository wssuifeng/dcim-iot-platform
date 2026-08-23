<template>
  <el-table v-loading="loading" :data="devices" row-key="id">
    <el-table-column prop="deviceName" label="设备名称" min-width="150" />
    <el-table-column prop="deviceCode" label="设备编码" min-width="130" />
    <el-table-column label="设备类型" min-width="130">
      <template #default="{ row }">{{ typeNames[row.typeId] || `类型 #${row.typeId}` }}</template>
    </el-table-column>
    <el-table-column label="组织 / 机房" min-width="220">
      <template #default="{ row }">
        <div>{{ organizationNames[row.organizationId] || `组织 #${row.organizationId}` }}</div>
        <div class="secondary">{{ roomNames[row.cloudRoomId] || `机房 #${row.cloudRoomId}` }}</div>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="120">
      <template #default="{ row }">
        <el-select
          v-if="canWrite"
          :model-value="row.status"
          size="small"
          @change="(value) => emit('status-change', row, value)"
        >
          <el-option label="离线" :value="0" />
          <el-option label="在线" :value="1" />
          <el-option label="故障" :value="2" />
        </el-select>
        <el-tag v-else :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="location" label="位置" min-width="150" show-overflow-tooltip />
    <el-table-column prop="updateTime" label="更新时间" min-width="170" />
    <el-table-column label="操作" width="310" fixed="right">
      <template #default="{ row }">
        <el-button link type="success" :icon="Connection" @click="emit('points', row)">
          点位
        </el-button>
        <el-button
          v-if="canWrite"
          link
          type="primary"
          :icon="Edit"
          @click="emit('edit', row)"
        >
          编辑
        </el-button>
        <el-button
          v-if="canWrite"
          link
          type="warning"
          :icon="Bell"
          @click="emit('alert-config', row)"
        >
          告警
        </el-button>
        <el-button
          v-if="canDelete"
          link
          type="danger"
          :icon="Delete"
          @click="emit('delete', row)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { Bell, Connection, Delete, Edit } from '@element-plus/icons-vue'

defineOptions({ name: 'DeviceManagementTable' })

defineProps({
  devices: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canWrite: { type: Boolean, default: false },
  canDelete: { type: Boolean, default: false },
  typeNames: { type: Object, default: () => ({}) },
  organizationNames: { type: Object, default: () => ({}) },
  roomNames: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['edit', 'delete', 'status-change', 'alert-config', 'points'])

function statusLabel(status) {
  return { 0: '离线', 1: '在线', 2: '故障' }[status] || status
}

function statusType(status) {
  return { 0: 'info', 1: 'success', 2: 'danger' }[status] || 'info'
}
</script>

<style scoped>
.secondary {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
