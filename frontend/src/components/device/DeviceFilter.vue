<template>
  <el-form :model="filter" inline class="device-filter">
    <el-form-item label="所属机房">
      <el-select v-model="filter.cloudRoomId" placeholder="全部机房" clearable filterable style="width: 170px">
        <el-option v-for="r in rooms" :key="r.id" :label="r.roomName" :value="r.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="设备状态">
      <el-select v-model="filter.status" placeholder="全部" clearable style="width: 120px">
        <el-option
          v-for="item in STATUS_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="设备名称">
      <el-input v-model="filter.name" placeholder="模糊搜索" clearable style="width: 160px" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="$emit('search')">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { DEVICE_STATUS } from '@/utils/constants'

defineProps({
  filter: { type: Object, required: true },
  rooms: { type: Array, default: () => [] },
})
defineEmits(['search', 'reset'])

const STATUS_OPTIONS = Object.entries(DEVICE_STATUS).map(([value, item]) => ({
  value: Number(value),
  label: item.label,
}))
</script>

<style scoped>
.device-filter { margin-bottom: 16px; }
</style>
