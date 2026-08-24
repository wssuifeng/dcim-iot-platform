<template>
  <el-table :data="list" v-loading="loading">
    <el-table-column prop="typeName" label="类型名称" min-width="140" />
    <el-table-column prop="typeCode" label="类型编码" min-width="140" />
    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
    <el-table-column prop="updateTime" label="更新时间" min-width="160" />
    <el-table-column v-if="canOperate||isAdmin" label="操作" width="160" fixed="right">
      <template #default="{row}"><el-button v-if="canOperate" type="primary" size="small" link @click="$emit('edit',row)">编辑</el-button><el-button v-if="isAdmin" type="danger" size="small" link @click="$emit('delete',row)">删除</el-button></template>
    </el-table-column>
  </el-table>
</template>
<script setup>
defineProps({list:{type:Array,default:()=>[]},loading:{type:Boolean,default:false},canOperate:{type:Boolean,default:false},isAdmin:{type:Boolean,default:false}})
defineEmits(['edit','delete'])
</script>
