<template>
  <el-form :model="filter" inline class="user-filter">
    <el-form-item label="关键字"><el-input v-model="filter.keyword" placeholder="用户名/昵称" clearable style="width:200px" @keyup.enter="$emit('search')" /></el-form-item>
    <el-form-item><el-button type="primary" @click="$emit('search')">查询</el-button><el-button @click="$emit('reset')">重置</el-button></el-form-item>
  </el-form>
</template>
<script setup>defineProps({filter:{type:Object,required:true}});defineEmits(['search','reset'])</script>
<style scoped>.user-filter{margin-bottom:16px}</style>
