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
        <el-button v-if="canOperate" type="primary" size="small" link @click="$emit('edit', row)">编辑</el-button>
        <el-button v-if="canOperate" type="warning" size="small" link @click="$emit('alert-config', row)">告警配置</el-button>
        <el-button v-if="isAdmin" type="danger" size="small" link @click="$emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { DEVICE_STATUS } from '@/utils/constants'

defineProps({
  devices: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canOperate: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
})
defineEmits(['edit', 'delete', 'status-change', 'alert-config'])

function statusType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}
</script>
