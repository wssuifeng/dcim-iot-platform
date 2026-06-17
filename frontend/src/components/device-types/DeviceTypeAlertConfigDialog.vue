<template>
  <el-dialog :model-value="visible" title="设备类型告警规则配置" width="860px"
    @update:model-value="$emit('update:visible',$event)" @open="handleOpen">
    <el-table :data="rules" border size="small">
      <el-table-column label="指标" min-width="140"><template #default="{row}"><el-select v-model="row.metricKey" placeholder="选择指标" style="width:100%"><el-option v-for="m in metrics" :key="m.metricKey" :label="m.metricName" :value="m.metricKey" /></el-select></template></el-table-column>
      <el-table-column label="运算符" width="110"><template #default="{row}"><el-select v-model="row.operator" placeholder="运算符" style="width:100%"><el-option v-for="op in OPERATORS" :key="op.value" :label="op.label" :value="op.value" /></el-select></template></el-table-column>
      <el-table-column label="阈值" width="110"><template #default="{row}"><el-input-number v-model="row.threshold" :controls="false" style="width:100%" /></template></el-table-column>
      <el-table-column label="级别" width="110"><template #default="{row}"><el-select v-model="row.level" placeholder="级别" style="width:100%"><el-option v-for="l in ALERT_LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" /></el-select></template></el-table-column>
      <el-table-column label="消息模板" min-width="180"><template #default="{row}"><el-input v-model="row.messageTemplate" placeholder="如：{metricName} 值为 {value}{unit}，超过 {threshold}" /></template></el-table-column>
      <el-table-column label="操作" width="70"><template #default="{$index}"><el-button type="danger" size="small" link @click="removeRule($index)">删除</el-button></template></el-table-column>
    </el-table>
    <el-button type="primary" link style="margin-top:12px" @click="addRule">+ 添加规则</el-button>
    <p class="tip">消息模板占位符：{metricName}/{value}/{unit}/{threshold}</p>
    <template #footer><el-button @click="$emit('update:visible',false)">取消</el-button><el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></template>
  </el-dialog>
</template>
<script setup>
import {ref} from 'vue';import {ALERT_LEVEL} from '@/utils/constants';import {updateDeviceTypeAlertConfig} from '@/api/deviceType';import {ElMessage} from 'element-plus'
const props=defineProps({visible:{type:Boolean,default:false},deviceType:{type:Object,default:null},metrics:{type:Array,default:()=>[]}})
const emit=defineEmits(['update:visible','confirm'])
const saving=ref(false),rules=ref([])
const OPERATORS=[{value:'GT',label:'大于'},{value:'LT',label:'小于'},{value:'GTE',label:'大于等于'},{value:'LTE',label:'小于等于'},{value:'EQ',label:'等于'}]
const ALERT_LEVEL_OPTIONS=Object.entries(ALERT_LEVEL).map(([v,i])=>({value:Number(v),label:i.label}))
function addRule(){rules.value.push({metricKey:'',operator:'GT',threshold:0,level:3,messageTemplate:'{metricName} 值为 {value}{unit}，超过 {threshold}'})}
function removeRule(i){rules.value.splice(i,1)}
function handleOpen(){const existing=props.deviceType?.alertRuleConfig?.rules;if(Array.isArray(existing)&&existing.length>0){rules.value=existing.map(r=>({metricKey:r.metricKey||'',operator:r.operator||'GT',threshold:r.threshold??0,level:r.level??3,messageTemplate:r.messageTemplate||'{metricName} 值为 {value}{unit}，超过 {threshold}'}))}else{rules.value=[];addRule()}}
async function handleSave(){if(rules.value.length===0){ElMessage.warning('告警规则列表不能为空');return}const valid=rules.value.every(r=>r.metricKey&&r.operator&&r.threshold!==null&&r.level&&r.messageTemplate);if(!valid){ElMessage.warning('请完整填写所有规则（含消息模板）');return}saving.value=true;try{await updateDeviceTypeAlertConfig(props.deviceType.id,{rules:rules.value});ElMessage.success('告警规则已保存');emit('update:visible',false);emit('confirm')}finally{saving.value=false}}
</script>
<style scoped>.tip{margin-top:12px;color:#909399;font-size:12px}.tip code{background:#f5f7fa;padding:1px 4px;border-radius:3px;color:#e6a23c}</style>
