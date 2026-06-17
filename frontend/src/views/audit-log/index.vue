<template>
  <div class="audit-log-page">
    <audit-log-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar"><el-button v-if="isAdmin" type="danger" @click="cleanupVisible=true">清理日志</el-button></div>
    <audit-log-table :list="logList" :loading="loading" />
    <el-pagination v-if="total>0" v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchLogList" style="margin-top:16px;justify-content:flex-end" />
    <cleanup-dialog v-model:visible="cleanupVisible" @confirm="handleCleanupConfirm" />
  </div>
</template>
<script setup>
import {ref,reactive,computed,onMounted} from 'vue';import {ElMessage} from 'element-plus'
import {useAuthStore} from '@/stores/auth';import {getAuditLogPage} from '@/api/auditLog'
import AuditLogFilter from '@/components/audit-log/AuditLogFilter.vue';import AuditLogTable from '@/components/audit-log/AuditLogTable.vue';import CleanupDialog from '@/components/audit-log/CleanupDialog.vue'

const authStore=useAuthStore();const isAdmin=computed(()=>authStore.role==='ADMIN')
const loading=ref(false),logList=ref([]),total=ref(0)
const pagination=reactive({current:1,size:10}),filter=reactive({operation:'',timeRange:null}),cleanupVisible=ref(false)

async function fetchLogList(){loading.value=true;try{const params={current:pagination.current,size:pagination.size};if(filter.operation)params.operation=filter.operation;if(filter.timeRange&&filter.timeRange.length===2){params.startTime=filter.timeRange[0];params.endTime=filter.timeRange[1]}const res=await getAuditLogPage(params);logList.value=res.data.records||[];total.value=res.data.total||0}finally{loading.value=false}}
function handleSearch(){pagination.current=1;fetchLogList()}
function handleReset(){filter.operation='';filter.timeRange=null;pagination.current=1;fetchLogList()}
function handleCleanupConfirm(){cleanupVisible.value=false;ElMessage.success('日志已清理');fetchLogList()}
onMounted(fetchLogList)
</script>
<style scoped>.audit-log-page{padding:0}.toolbar{margin-bottom:16px}</style>
