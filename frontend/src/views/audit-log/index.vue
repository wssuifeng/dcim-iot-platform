<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>审计日志</h1>
        <p>操作主体、授权范围、目标对象和执行结果</p>
      </div>
      <el-button v-if="canCleanup" type="danger" plain :icon="Delete" @click="cleanupVisible = true">
        清理日志
      </el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="用户ID">
        <el-input-number v-model="filters.userId" :min="1" controls-position="right" class="user-input" />
      </el-form-item>
      <el-form-item label="操作类型">
        <el-input v-model="filters.operation" placeholder="如 ACK_ALARM_EVENT" clearable class="operation-input" />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          :disabled-date="(date) => date.getTime() > Date.now()"
          class="time-picker"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="logs" row-key="id">
      <el-table-column label="操作主体" min-width="180">
        <template #default="{ row }">
          <div class="primary-cell">{{ row.username || `用户 #${row.userId}` }}</div>
          <div class="secondary-cell">{{ roleLabel(row.role) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="机房" min-width="110">
        <template #default="{ row }">{{ row.cloudRoomId ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <div class="primary-cell">{{ row.operation || row.action || '-' }}</div>
          <div class="secondary-cell">{{ row.action || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="目标对象" min-width="160">
        <template #default="{ row }">
          <div class="primary-cell">{{ row.targetType || '-' }}</div>
          <div class="secondary-cell">{{ row.targetId ? `#${row.targetId}` : '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="130">
        <template #default="{ row }">
          <el-tag :type="resultType(row.result)" effect="plain">{{ resultLabel(row.result) }}</el-tag>
          <div v-if="row.errorCode" class="error-code">{{ row.errorCode }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="230" show-overflow-tooltip />
      <el-table-column label="链路" min-width="170">
        <template #default="{ row }">
          <div class="primary-cell">{{ row.ip || '-' }}</div>
          <div class="secondary-cell trace-cell">{{ row.traceId || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="操作时间" min-width="170" />
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchLogs"
    />

    <cleanup-dialog v-model:visible="cleanupVisible" @confirm="handleCleanup" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getAuditLogPage } from '@/api/auditLog'
import CleanupDialog from '@/components/audit-log/CleanupDialog.vue'

defineOptions({ name: 'StructuredAuditLog' })

const authStore = useAuthStore()
const canCleanup = computed(() => authStore.isAdmin)
const loading = ref(false)
const logs = ref([])
const total = ref(0)
const pagination = reactive({ current: 1, size: 10 })
const filters = reactive({ userId: null, operation: '', timeRange: null })
const cleanupVisible = ref(false)

async function fetchLogs() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filters.userId) params.userId = filters.userId
    if (filters.operation.trim()) params.operation = filters.operation.trim()
    if (filters.timeRange?.length === 2) {
      params.startTime = filters.timeRange[0]
      params.endTime = filters.timeRange[1]
    }
    const result = await getAuditLogPage(params)
    logs.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.current = 1
  void fetchLogs()
}

function resetFilters() {
  Object.assign(filters, { userId: null, operation: '', timeRange: null })
  pagination.current = 1
  void fetchLogs()
}

function handleCleanup() {
  cleanupVisible.value = false
  void fetchLogs()
}

function roleLabel(value) {
  return { ADMIN: '管理员', OPERATOR: '运维人员', VIEWER: '查看者' }[value] || value || '-'
}

function resultLabel(result) {
  return { SUCCESS: '成功', FAILURE: '失败', REJECTED: '已拒绝' }[result] || result || '未记录'
}

function resultType(result) {
  return { SUCCESS: 'success', FAILURE: 'danger', REJECTED: 'warning' }[result] || 'info'
}

onMounted(() => {
  void fetchLogs()
})
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.filter-bar {
  padding: 14px 16px 0;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  background: var(--color-surface);
}

.user-input {
  width: 130px;
}

.operation-input {
  width: 220px;
}

.time-picker {
  width: 380px;
}

.primary-cell {
  color: var(--color-text);
}

.secondary-cell,
.error-code {
  margin-top: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.trace-cell {
  overflow: hidden;
  max-width: 180px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-code {
  color: var(--color-danger);
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
