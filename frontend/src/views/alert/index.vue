<template>
  <div class="alert-page">
    <alert-stats-panel :stats="stats" />

    <alert-filter
      :filter="filter"
      @search="handleSearch"
      @reset="handleReset"
    />

    <div class="toolbar">
      <el-button
        v-if="canHandle"
        type="warning"
        :disabled="selectedIds.length === 0"
        @click="handleBatchHandle"
      >
        批量处理 ({{ selectedIds.length }})
      </el-button>
    </div>

    <alert-table
      :alerts="alertList"
      :loading="loading"
      :can-handle="canHandle"
      @selection-change="handleSelectionChange"
      @handle="handleSingleHandle"
    />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="fetchAlertList"
      style="margin-top: 16px; justify-content: flex-end"
    />

    <alert-handle-dialog
      v-model:visible="dialogVisible"
      :alert-ids="dialogIds"
      :mode="dialogMode"
      :username="username"
      @confirm="handleConfirmHandle"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getAlerts, batchHandleAlerts } from '@/api/alert'
import { ElMessage } from 'element-plus'
import AlertFilter from '@/components/alert/AlertFilter.vue'
import AlertTable from '@/components/alert/AlertTable.vue'
import AlertStatsPanel from '@/components/alert/AlertStatsPanel.vue'
import AlertHandleDialog from '@/components/alert/AlertHandleDialog.vue'

const authStore = useAuthStore()
const username = computed(() => authStore.username)
const canHandle = computed(() => authStore.role !== 'VIEWER')

const loading = ref(false)
const alertList = ref([])
const total = ref(0)
const selectedIds = ref([])

const stats = ref({})

const pagination = reactive({ current: 1, size: 10 })

const filter = reactive({
  deviceId: '',
  isHandled: null,
  alertLevel: null,
  timeRange: [],
})

const dialogVisible = ref(false)
const dialogIds = ref([])
const dialogMode = ref('single')

async function fetchAlertList() {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
    }
    if (filter.deviceId) params.deviceId = filter.deviceId
    if (filter.isHandled !== null && filter.isHandled !== '') params.isHandled = filter.isHandled
    if (filter.alertLevel) params.alertLevel = filter.alertLevel
    if (filter.timeRange?.length === 2) {
      params.startTime = filter.timeRange[0]
      params.endTime = filter.timeRange[1]
    }
    const res = await getAlerts(params)
    alertList.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const [allRes, unhandledRes] = await Promise.all([
      getAlerts({ current: 1, size: 1 }),
      getAlerts({ current: 1, size: 1, isHandled: 0 }),
    ])
    stats.value = {
      total: allRes.data.total || 0,
      unhandled: unhandledRes.data.total || 0,
      handled: (allRes.data.total || 0) - (unhandledRes.data.total || 0),
      emergency: 0,
    }
  } catch {
    // 非核心功能，静默失败
  }
}

function handleSearch() {
  pagination.current = 1
  fetchAlertList()
}

function handleReset() {
  filter.deviceId = ''
  filter.isHandled = null
  filter.alertLevel = null
  filter.timeRange = []
  pagination.current = 1
  fetchAlertList()
}

function handleSelectionChange(ids) {
  selectedIds.value = ids
}

function handleSingleHandle(alert) {
  dialogIds.value = [alert.id]
  dialogMode.value = 'single'
  dialogVisible.value = true
}

function handleBatchHandle() {
  dialogIds.value = [...selectedIds.value]
  dialogMode.value = 'batch'
  dialogVisible.value = true
}

async function handleConfirmHandle(handleUser) {
  try {
    await batchHandleAlerts({ ids: dialogIds.value, handleUser })
    ElMessage.success(`成功处理 ${dialogIds.value.length} 条告警`)
    selectedIds.value = []
    dialogVisible.value = false
    fetchAlertList()
    fetchStats()
  } catch {
    // 错误已由拦截器处理
  }
}

onMounted(() => {
  fetchAlertList()
  fetchStats()
})
</script>

<style scoped>
.alert-page {
  padding: 0;
}

.toolbar {
  margin-bottom: 16px;
}
</style>
