<template>
  <el-dialog :model-value="visible" title="清理审计日志" width="480px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <p class="cleanup-desc">将删除所选时间之前的所有审计日志，操作不可撤销。</p>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
      <el-form-item label="清理此时间之前" prop="beforeTime"><el-date-picker v-model="form.beforeTime" type="datetime" placeholder="选择日期时间" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" :disabled-date="d=>d.getTime()>Date.now()" style="width:100%" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="danger" :loading="cleaning" @click="handleClean">确认清理</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref,reactive} from 'vue';import {ElMessage} from 'element-plus';import {cleanupAuditLog} from '@/api/auditLog'
const props=defineProps({visible:{type:Boolean,default:false}})
const emit=defineEmits(['update:visible','confirm'])
const formRef=ref(null),cleaning=ref(false)
const form=reactive({beforeTime:''})
const rules={beforeTime:[{required:true,message:'请选择清理时间点',trigger:'change'}]}
function handleOpen(){form.beforeTime=''}
async function handleClean(){const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;cleaning.value=true;try{const res=await cleanupAuditLog(form.beforeTime);const count=res?.data??0;ElMessage.success(`已清理 ${count} 条日志`);emit('update:visible',false);emit('confirm')}catch{}finally{cleaning.value=false}}
</script>
<style scoped>.cleanup-desc{color:var(--color-danger);font-size:13px;margin:0 0 16px 0}</style>
