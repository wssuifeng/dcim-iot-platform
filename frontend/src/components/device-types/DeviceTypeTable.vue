<template>
  <el-table :data="list" v-loading="loading">
    <el-table-column prop="typeName" label="类型名称" min-width="140" />
    <el-table-column prop="typeCode" label="类型编码" min-width="140" />
    <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
    <el-table-column label="告警规则" width="120">
      <template #default="{row}"><el-tag v-if="ruleCount(row)" type="warning" size="small">{{ruleCount(row)}}条</el-tag><span v-else class="no-rule">未配置</span></template>
    </el-table-column>
    <el-table-column prop="updateTime" label="更新时间" min-width="160" />
    <el-table-column v-if="canOperate||isAdmin" label="操作" width="220" fixed="right">
      <template #default="{row}"><el-button v-if="canOperate" type="primary" size="small" link @click="$emit('edit',row)">编辑</el-button><el-button v-if="canOperate" type="warning" size="small" link @click="$emit('alert-config',row)">告警配置</el-button><el-button v-if="isAdmin" type="danger" size="small" link @click="$emit('delete',row)">删除</el-button></template>
    </el-table-column>
  </el-table>
</template>
<script setup>
defineProps({list:{type:Array,default:()=>[]},loading:{type:Boolean,default:false},canOperate:{type:Boolean,default:false},isAdmin:{type:Boolean,default:false}})
defineEmits(['edit','delete','alert-config'])
function ruleCount(row){const rules=row?.alertRuleConfig?.rules;return Array.isArray(rules)?rules.length:0}
</script>
<style scoped>.no-rule{color:var(--color-text-3)}</style>
