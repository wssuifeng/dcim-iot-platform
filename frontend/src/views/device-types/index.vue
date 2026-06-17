<template>
  <div class="device-type-page">
    <device-type-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar"><el-button v-if="canOperate" type="primary" @click="handleCreate">新增设备类型</el-button></div>
    <device-type-table :list="filteredList" :loading="loading" :can-operate="canOperate" :is-admin="isAdmin" @edit="handleEdit" @delete="handleDelete" @alert-config="handleAlertConfig" />
    <device-type-form-dialog v-model:visible="formVisible" :mode="formMode" :current="currentType" @confirm="handleFormConfirm" />
    <device-type-alert-config-dialog v-model:visible="alertConfigVisible" :device-type="currentType" :metrics="metrics" @confirm="handleAlertConfigConfirm" />
  </div>
</template>
<script setup>
import {ref,reactive,computed,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAuthStore} from '@/stores/auth'
import {getDeviceTypes,createDeviceType,updateDeviceType,deleteDeviceType} from '@/api/deviceType'
import {getMetricPage} from '@/api/metric'
import DeviceTypeFilter from '@/components/device-types/DeviceTypeFilter.vue'
import DeviceTypeTable from '@/components/device-types/DeviceTypeTable.vue'
import DeviceTypeFormDialog from '@/components/device-types/DeviceTypeFormDialog.vue'
import DeviceTypeAlertConfigDialog from '@/components/device-types/DeviceTypeAlertConfigDialog.vue'

const authStore=useAuthStore()
const canOperate=computed(()=>authStore.role!=='VIEWER')
const isAdmin=computed(()=>authStore.role==='ADMIN')
const loading=ref(false),typeList=ref([]),metrics=ref([])
const filter=reactive({typeName:'',typeCode:''})
const filteredList=computed(()=>typeList.value.filter(t=>{const m=!filter.typeName||(t.typeName||'').toLowerCase().includes(filter.typeName.toLowerCase());const c=!filter.typeCode||(t.typeCode||'').toLowerCase().includes(filter.typeCode.toLowerCase());return m&&c}))
const formVisible=ref(false),formMode=ref('create'),currentType=ref(null),alertConfigVisible=ref(false)

async function fetchTypeList(){loading.value=true;try{const res=await getDeviceTypes();typeList.value=res.data||[]}finally{loading.value=false}}
async function fetchMetrics(){try{const res=await getMetricPage({current:1,size:999});metrics.value=res.data.records||[]}catch{}}
function handleSearch(){}
function handleReset(){filter.typeName='';filter.typeCode=''}
function handleCreate(){formMode.value='create';currentType.value=null;formVisible.value=true}
function handleEdit(row){formMode.value='edit';currentType.value={...row};formVisible.value=true}
async function handleDelete(row){try{await ElMessageBox.confirm(`确认删除设备类型"${row.typeName}"？`,'删除确认',{type:'warning'});await deleteDeviceType(row.id);ElMessage.success('设备类型已删除');fetchTypeList()}catch{}}
function handleAlertConfig(row){currentType.value={...row};alertConfigVisible.value=true}
async function handleFormConfirm(data){try{if(formMode.value==='create'){await createDeviceType(data);ElMessage.success('设备类型已创建')}else{await updateDeviceType(currentType.value.id,data);ElMessage.success('设备类型已更新')}formVisible.value=false;fetchTypeList()}catch{}}
function handleAlertConfigConfirm(){alertConfigVisible.value=false;ElMessage.success('告警规则已更新');fetchTypeList()}
onMounted(()=>{fetchTypeList();fetchMetrics()})
</script>
<style scoped>.device-type-page{padding:0}.toolbar{margin-bottom:16px}</style>
