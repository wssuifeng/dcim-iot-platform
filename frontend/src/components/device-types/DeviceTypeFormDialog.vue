<template>
  <el-dialog :model-value="visible" :title="mode==='create'?'新增设备类型':'编辑设备类型'" width="520px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
      <el-form-item label="类型名称" prop="typeName"><el-input v-model="form.typeName" placeholder="如 温度传感器" /></el-form-item>
      <el-form-item label="类型编码" prop="typeCode"><el-input v-model="form.typeCode" placeholder="如 TEMP_SENSOR" :disabled="mode==='edit'" /></el-form-item>
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
const form=reactive({id:null,typeName:'',typeCode:'',description:''})
const rules={typeName:[{required:true,message:'请输入类型名称',trigger:'blur'}],typeCode:[{required:true,message:'请输入类型编码',trigger:'blur'}]}
function handleOpen(){if(props.mode==='edit'&&props.current){form.id=props.current.id||null;form.typeName=props.current.typeName||'';form.typeCode=props.current.typeCode||'';form.description=props.current.description||''}else{form.id=null;form.typeName='';form.typeCode='';form.description=''}}
async function handleSubmit(){const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;submitting.value=true;try{emit('confirm',{...form})}finally{submitting.value=false}}
</script>
