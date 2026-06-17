<template>
  <el-dialog :model-value="visible" title="重置密码" width="460px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
      <el-form-item label="用户名"><el-input :model-value="user?.username" disabled /></el-form-item>
      <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password placeholder="8-32 位含大小写字母和数字" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref,reactive} from 'vue'
const props=defineProps({visible:{type:Boolean,default:false},user:{type:Object,default:null}})
const emit=defineEmits(['update:visible','confirm'])
const formRef=ref(null),submitting=ref(false)
const form=reactive({newPassword:''})
const rules={newPassword:[{required:true,message:'请输入新密码',trigger:'blur'},{pattern:/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/,message:'8-32 位须含大小写字母和数字',trigger:'blur'}]}
function handleOpen(){form.newPassword=''}
async function handleSubmit(){const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;submitting.value=true;try{emit('confirm',{newPassword:form.newPassword})}finally{submitting.value=false}}
</script>
