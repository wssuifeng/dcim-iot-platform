<template>
  <el-form :model="filter" inline class="metric-filter">
    <el-form-item label="指标标识"><el-input v-model="filter.metricKey" placeholder="如 temperature" clearable style="width:180px" /></el-form-item>
    <el-form-item label="指标名称"><el-input v-model="filter.metricName" placeholder="如 温度" clearable style="width:160px" /></el-form-item>
    <el-form-item><el-button type="primary" @click="$emit('search')">查询</el-button><el-button @click="$emit('reset')">重置</el-button></el-form-item>
  </el-form>
</template>
<script setup>
defineProps({ filter: { type: Object, required: true } })
defineEmits(['search','reset'])
</script>
<style scoped>.metric-filter{margin-bottom:16px}</style>
