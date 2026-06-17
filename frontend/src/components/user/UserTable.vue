<template>
  <el-table :data="list" v-loading="loading">
    <el-table-column prop="username" label="用户名" min-width="140" />
    <el-table-column prop="nickname" label="昵称" min-width="140" />
    <el-table-column label="角色" width="120">
      <template #default="{row}"><el-tag :color="USER_ROLE[row.role]?.color" effect="dark" size="small">{{ USER_ROLE[row.role]?.label||row.role }}</el-tag></template>
    </el-table-column>
    <el-table-column prop="createTime" label="创建时间" min-width="160" />
    <el-table-column label="操作" width="320" fixed="right">
      <template #default="{row}">
        <el-button type="primary" size="small" link @click="$emit('edit',row)">编辑</el-button>
        <el-button type="warning" size="small" link @click="$emit('reset-password',row)">重置密码</el-button>
        <el-button type="success" size="small" link @click="$emit('enable',row)">启用</el-button>
        <el-button type="info" size="small" link @click="$emit('disable',row)">停用</el-button>
        <el-button type="danger" size="small" link @click="$emit('delete',row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
<script setup>
import {USER_ROLE} from '@/utils/constants'
defineProps({list:{type:Array,default:()=>[]},loading:{type:Boolean,default:false}})
defineEmits(['edit','reset-password','enable','disable','delete'])
</script>
