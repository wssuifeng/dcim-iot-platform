<template>
  <el-table :data="list" v-loading="loading">
    <el-table-column prop="username" label="操作人" min-width="120" />
    <el-table-column prop="operation" label="操作类型" min-width="160" />
    <el-table-column prop="ip" label="IP 地址" width="140" />
    <el-table-column prop="detail" label="操作详情" min-width="220" show-overflow-tooltip />
    <el-table-column prop="createTime" label="操作时间" min-width="160" />
  </el-table>
</template>
<script setup>defineProps({list:{type:Array,default:()=>[]},loading:{type:Boolean,default:false}})</script>
