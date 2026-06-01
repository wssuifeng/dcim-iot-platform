<template>
  <el-form :model="filter" inline class="alert-filter">
    <el-form-item label="设备ID">
      <el-input v-model="filter.deviceId" placeholder="请输入设备ID" clearable style="width: 140px" />
    </el-form-item>
    <el-form-item label="处理状态">
      <el-select v-model="filter.isHandled" placeholder="全部" clearable style="width: 120px">
        <el-option label="未处理" :value="0" />
        <el-option label="已处理" :value="1" />
      </el-select>
    </el-form-item>
    <el-form-item label="告警级别">
      <el-select v-model="filter.alertLevel" placeholder="全部" clearable style="width: 120px">
        <el-option
          v-for="item in ALERT_LEVEL_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="时间范围">
      <el-date-picker
        v-model="filter.timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DD HH:mm:ss"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="$emit('search')">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ALERT_LEVEL } from '@/utils/constants'

defineProps({
  filter: { type: Object, required: true },
})

defineEmits(['search', 'reset'])

const ALERT_LEVEL_OPTIONS = Object.entries(ALERT_LEVEL).map(([value, item]) => ({
  value: Number(value),
  label: item.label,
}))
</script>

<style scoped>
.alert-filter {
  margin-bottom: 16px;
}
</style>
