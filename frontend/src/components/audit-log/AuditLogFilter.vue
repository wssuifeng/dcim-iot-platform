<template>
  <el-form :model="filter" inline class="audit-log-filter">
    <el-form-item label="操作"><el-input v-model="filter.operation" placeholder="如 LOGIN_SUCCESS" clearable style="width:200px" /></el-form-item>
    <el-form-item label="时间范围"><el-date-picker v-model="filter.timeRange" type="datetimerange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="d=>d.getTime()>Date.now()" style="width:380px" /></el-form-item>
    <el-form-item><el-button type="primary" @click="$emit('search')">查询</el-button><el-button @click="$emit('reset')">重置</el-button></el-form-item>
  </el-form>
</template>
<script setup>defineProps({filter:{type:Object,required:true}});defineEmits(['search','reset'])</script>
<style scoped>.audit-log-filter{margin-bottom:16px}</style>
