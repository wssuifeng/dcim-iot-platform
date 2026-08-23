<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>站内通知</h1>
        <p>当前账号的告警通知及读取状态</p>
      </div>
      <el-button
        type="primary"
        plain
        :icon="Select"
        :disabled="unreadCount === 0"
        :loading="markingAll"
        @click="markAllRead"
      >
        全部已读
      </el-button>
    </header>

    <div class="notification-toolbar">
      <el-radio-group v-model="readFilter" @change="handleFilterChange">
        <el-radio-button label="全部" value="ALL" />
        <el-radio-button label="未读" value="UNREAD" />
        <el-radio-button label="已读" value="READ" />
      </el-radio-group>
      <span class="unread-summary">未读 {{ unreadCount }}</span>
    </div>

    <div v-loading="loading" class="notification-list">
      <button
        v-for="item in notifications"
        :key="item.id"
        type="button"
        class="notification-item"
        :class="{ unread: item.isRead === 0 }"
        @click="openNotification(item)"
      >
        <span class="status-dot" aria-hidden="true" />
        <span class="notification-copy">
          <span class="notification-title-row">
            <strong>{{ item.title }}</strong>
            <el-tag size="small" effect="plain">{{ sourceLabel(item.sourceType) }}</el-tag>
          </span>
          <span class="notification-content">{{ item.content }}</span>
          <span class="notification-time">{{ item.createTime }}</span>
        </span>
        <el-icon><ArrowRight /></el-icon>
      </button>
      <el-empty v-if="!loading && notifications.length === 0" description="暂无通知" />
    </div>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchNotifications"
    />
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ArrowRight, Select } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getNotificationPage,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { onSummary, publishUnreadNotificationCount } from '@/api/realtime'

defineOptions({ name: 'InAppNotificationCenter' })

const router = useRouter()
const loading = ref(false)
const markingAll = ref(false)
const notifications = ref([])
const total = ref(0)
const unreadCount = ref(0)
const readFilter = ref('ALL')
const pagination = reactive({ current: 1, size: 10 })
let removeSummaryListener = null

async function fetchNotifications() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (readFilter.value !== 'ALL') params.isRead = readFilter.value === 'UNREAD' ? 0 : 1
    const [pageResult, countResult] = await Promise.all([
      getNotificationPage(params),
      getUnreadNotificationCount(),
    ])
    notifications.value = pageResult.data?.records || []
    total.value = pageResult.data?.total || 0
    unreadCount.value = Number(countResult.data) || 0
    publishUnreadNotificationCount(unreadCount.value)
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  pagination.current = 1
  void fetchNotifications()
}

async function openNotification(item) {
  if (item.isRead === 0) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    publishUnreadNotificationCount(unreadCount.value)
  }
  if (item.sourceType === 'ALARM_EVENT' && item.sourceId) {
    await router.push({ name: 'alerts', query: { id: String(item.sourceId) } })
  }
}

async function markAllRead() {
  markingAll.value = true
  try {
    const result = await markAllNotificationsRead()
    ElMessage.success(`已标记 ${Number(result.data) || 0} 条通知`)
    await fetchNotifications()
  } finally {
    markingAll.value = false
  }
}

function sourceLabel(sourceType) {
  return { ALARM_EVENT: '告警事件', SIMULATION: '模拟运行' }[sourceType] || sourceType || '系统'
}

onMounted(() => {
  void fetchNotifications()
  removeSummaryListener = onSummary(fetchNotifications)
})

onBeforeUnmount(() => removeSummaryListener?.())
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header,
.notification-toolbar,
.notification-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.page-header {
  align-items: flex-start;
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

.notification-toolbar {
  padding-bottom: 14px;
  border-bottom: 1px solid #dcdfe6;
}

.unread-summary {
  color: var(--color-text-2);
  font-size: 13px;
}

.notification-list {
  min-height: 280px;
}

.notification-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) 20px;
  width: 100%;
  gap: 14px;
  align-items: center;
  padding: 16px 4px;
  border: 0;
  border-bottom: 1px solid #ebeef5;
  background: var(--color-surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.notification-item:hover {
  background: var(--color-bg);
}

.notification-item.unread {
  background: #f0f8ff;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: transparent;
}

.unread .status-dot {
  background: var(--color-primary);
}

.notification-copy {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.notification-title-row {
  justify-content: flex-start;
}

.notification-content {
  overflow: hidden;
  color: var(--color-text-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-time {
  color: var(--color-text-3);
  font-size: 12px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
