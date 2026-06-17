<template>
  <el-dialog :model-value="visible" :title="mode==='create'?'新增用户':'编辑用户'" width="520px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
      <el-form-item label="用户名" prop="username"><el-input v-model="form.username" placeholder="4-20 位字母数字下划线" :disabled="mode==='edit'" /></el-form-item>
      <el-form-item v-if="mode==='create'" label="密码" prop="password"><el-input v-model="form.password" type="password" show-password placeholder="8-32 位含大小写字母和数字" /></el-form-item>
      <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" placeholder="展示用昵称（可选）" /></el-form-item>
      <el-form-item label="角色" prop="role"><el-select v-model="form.role" placeholder="请选择角色" style="width:100%"><el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
    </el-form>
    <p v-if="mode==='create'" class="tip">⚠️ 后端 SysUser.password 当前标注 @JsonIgnore，创建用户可能失败，待后端改为 @JsonProperty(access=WRITE_ONLY) 后生效。</p>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref,reactive} from 'vue';import {USER_ROLE} from '@/utils/constants'
const props=defineProps({visible:{type:Boolean,default:false},mode:{type:String,default:'create'},current:{type:Object,default:null}})
const emit=defineEmits(['update:visible','confirm'])
const formRef=ref(null),submitting=ref(false)
const form=reactive({username:'',password:'',nickname:'',role:''})
const rules={username:[{required:true,message:'请输入用户名',trigger:'blur'},{pattern:/^[a-zA-Z0-9_]{4,20}$/,message:'4-20 位字母数字下划线',trigger:'blur'}],password:[{required:true,message:'请输入密码',trigger:'blur'},{pattern:/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/,message:'8-32 位须含大小写字母和数字',trigger:'blur'}],role:[{required:true,message:'请选择角色',trigger:'change'}]}
const ROLE_OPTIONS=Object.entries(USER_ROLE).map(([v,i])=>({value:v,label:i.label}))
function handleOpen(){if(props.mode==='edit'&&props.current){form.username=props.current.username||'';form.password='';form.nickname=props.current.nickname||'';form.role=props.current.role||''}else{form.username='';form.password='';form.nickname='';form.role=''}}
async function handleSubmit(){const valid=await formRef.value.validate().catch(()=>false);if(!valid)return;submitting.value=true;try{const payload={...form};if(props.mode==='edit')delete payload.password;emit('confirm',payload)}finally{submitting.value=false}}
</script>
<style scoped>.tip{margin:0;color:#e6a23c;font-size:12px}</style>
