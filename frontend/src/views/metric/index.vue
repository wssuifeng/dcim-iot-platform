<template>
  <div class="metric-page">
    <metric-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar">
      <el-button v-if="canOperate" type="primary" @click="handleCreate">新增指标</el-button>
      <el-button v-if="canOperate" @click="handleRefreshCache" :loading="cacheLoading">刷新缓存</el-button>
    </div>
    <metric-table :list="metricList" :loading="loading" :can-operate="canOperate" :is-admin="isAdmin" @edit="handleEdit" @delete="handleDelete" />
    <el-pagination v-if="total>0" v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="total"
      :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchMetricList" style="margin-top:16px;justify-content:flex-end" />
    <metric-form-dialog v-model:visible="formVisible" :mode="formMode" :current="currentMetric" @confirm="handleFormConfirm" />
  </div>
</template>
<script setup>
import {ref,reactive,computed,onMounted} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {useAuthStore} from '@/stores/auth'
import {getMetricPage,createMetric,updateMetric,deleteMetric,refreshMetricCache} from '@/api/metric'
import MetricFilter from '@/components/metric/MetricFilter.vue'
import MetricTable from '@/components/metric/MetricTable.vue'
import MetricFormDialog from '@/components/metric/MetricFormDialog.vue'

const authStore=useAuthStore()
const canOperate=computed(()=>authStore.role!=='VIEWER')
const isAdmin=computed(()=>authStore.role==='ADMIN')
const loading=ref(false),cacheLoading=ref(false),metricList=ref([]),total=ref(0)
const pagination=reactive({current:1,size:10}),filter=reactive({metricKey:'',metricName:''})
const formVisible=ref(false),formMode=ref('create'),currentMetric=ref(null)

async function fetchMetricList(){loading.value=true;try{const params={current:pagination.current,size:pagination.size};if(filter.metricKey)params.metricKey=filter.metricKey;if(filter.metricName)params.metricName=filter.metricName;const res=await getMetricPage(params);metricList.value=res.data.records||[];total.value=res.data.total||0}finally{loading.value=false}}
function handleSearch(){pagination.current=1;fetchMetricList()}
function handleReset(){filter.metricKey='';filter.metricName='';pagination.current=1;fetchMetricList()}
function handleCreate(){formMode.value='create';currentMetric.value=null;formVisible.value=true}
function handleEdit(row){formMode.value='edit';currentMetric.value={...row};formVisible.value=true}
async function handleDelete(row){try{await ElMessageBox.confirm(`确认删除指标"${row.metricName}"？`,'删除确认',{type:'warning'});await deleteMetric(row.id);ElMessage.success('指标已删除');fetchMetricList()}catch{}}
async function handleRefreshCache(){cacheLoading.value=true;try{await refreshMetricCache();ElMessage.success('指标缓存已刷新')}catch{}finally{cacheLoading.value=false}}
async function handleFormConfirm(data){try{if(formMode.value==='create'){await createMetric(data);ElMessage.success('指标已创建')}else{await updateMetric(data);ElMessage.success('指标已更新')}formVisible.value=false;fetchMetricList()}catch{}}
onMounted(fetchMetricList)
</script>
<style scoped>.metric-page{padding:0}.toolbar{margin-bottom:16px}</style>
