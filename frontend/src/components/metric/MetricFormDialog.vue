<template>
  <el-dialog :model-value="visible" :title="mode==='create'?'新增指标':'编辑指标'" width="520px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
      <el-form-item label="指标标识" prop="metricKey"><el-input v-model="form.metricKey" placeholder="如 temperature" :disabled="mode==='edit'" /></el-form-item>
      <el-form-item label="指标名称" prop="metricName"><el-input v-model="form.metricName" placeholder="如 温度" /></el-form-item>
      <el-form-item label="单位" prop="metricUnit"><el-input v-model="form.metricUnit" placeholder="如 ℃" /></el-form-item>
      <el-form-item label="描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref,reactive} from 'vue'
const props=defineProps({visible:{type:Boolean,default:false},mode:{type:String,default:'create'},current:{type:Object,default:null}})
const emit=defineEmits(['update:visible','confirm'])
const formRef=ref(null),submitting=ref(false)
const form=reactive({id:null,metricKey:'',metricName:'',metricUnit:'',description:''})
const rules={metricKey:[{required:true,message:'请输入指标标识',trigger:'blur'}],metricName:[{required:true,message:'请输入指标名称',trigger:'blur'}]}
function handleOpen(){if(props.mode==='edit'&&props.current){form.id=props.current.id||null;form.metricKey=props.current.metricKey||'';form.metricName=props.current.metricName||'';form.metricUnit=props.current.metricUnit||'';form.description=props.current.description||''}else{form.id=null;form.metricKey='';form.metricName='';form.metricUnit='';form.description=''}}
async function handleSubmit(){const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;submitting.value=true;try{emit('confirm',{...form})}finally{submitting.value=false}}
</script>
