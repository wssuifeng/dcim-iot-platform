<template>
  <el-form :model="filter" inline class="filter"><el-form-item label="类型名称"><el-input v-model="filter.typeName" placeholder="模糊搜索" clearable style="width:180px" /></el-form-item><el-form-item label="类型编码"><el-input v-model="filter.typeCode" placeholder="模糊搜索" clearable style="width:160px" /></el-form-item><el-form-item><el-button type="primary" @click="$emit('search')">查询</el-button><el-button @click="$emit('reset')">重置</el-button></el-form-item></el-form>
</template>
<script setup>defineProps({filter:{type:Object,required:true}});defineEmits(['search','reset'])</script>
<style scoped>.filter{margin-bottom:16px}</style>
