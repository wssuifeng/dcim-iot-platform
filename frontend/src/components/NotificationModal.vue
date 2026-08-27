<template>
  <el-dialog
    :model-value="visible"
    title="站内通知"
    width="640px"
    align-center
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="toolbar">
      <el-radio-group v-model="filter" size="small" @change="fetchList">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="UNREAD">未读</el-radio-button>
        <el-radio-button value="READ">已读</el-radio-button>
      </el-radio-group>
      <el-button size="small" :disabled="unreadCount === 0" @click="markAllRead">
        全部已读
      </el-button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && items.length === 0" description="暂无通知" :image-size="70" />
      <div
        v-for="item in items"
        :key="item.id"
        :class="['notice-item', { unread: item.isRead === 0 }]"
        @click="openItem(item)"
      >
        <div class="item-main">
          <span class="dot" :class="{ active: item.isRead === 0 }" />
          <span class="title">{{ item.title }}</span>
          <el-tag v-if="item.isRead === 0" size="small" type="danger" effect="plain">未读</el-tag>
        </div>
        <div class="item-sub">
          <span class="content">{{ item.content }}</span>
          <span class="time">{{ item.createTime }}</span>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > pagination.size"
      v-model:current-page="pagination.current"
      :page-size="pagination.size"
      :total="total"
      layout="prev, pager, next"
      small
      class="pager"
      @current-change="fetchList"
    />
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getNotificationPage,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notification'
import { publishUnreadNotificationCount } from '@/api/realtime'

defineOptions({ name: 'NotificationModal' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  unreadCount: { type: Number, default: 0 },
})
const emit = defineEmits(['update:visible', 'read'])

const router = useRouter()
const loading = ref(false)
const items = ref([])
const total = ref(0)
const filter = ref('ALL')
const pagination = reactive({ current: 1, size: 10 })

async function fetchList() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filter.value === 'UNREAD') params.isRead = 0
    if (filter.value === 'READ') params.isRead = 1
    const result = await getNotificationPage(params)
    items.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function openItem(item) {
  if (item.isRead === 0) {
    await markNotificationRead(item.id).catch(() => null)
    item.isRead = 1
    emit('read')
    publishUnreadNotificationCount(props.unreadCount - 1)
  }
  // 告警类通知跳转详情；其余通知阅读即关闭
  if (item.sourceType === 'ALARM_EVENT' && item.sourceId) {
    emit('update:visible', false)
    router.push(`/alerts?id=${item.sourceId}`)
  }
}

async function markAllRead() {
  await markAllNotificationsRead()
  ElMessage.success('已全部标记为已读')
  emit('read')
  publishUnreadNotificationCount(0)
  await fetchList()
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      pagination.current = 1
      filter.value = 'ALL'
      void fetchList()
    }
  },
)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.list {
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
}

.notice-item {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  border-radius: 4px;
}

.notice-item:hover {
  background: var(--el-fill-color-light);
}

.notice-item.unread {
  background: var(--el-color-primary-light-9);
}

.item-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #dcdfe6;
  flex-shrink: 0;
}

.dot.active {
  background: var(--el-color-danger);
}

.title {
  font-weight: 500;
  color: var(--color-text);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-sub {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 4px 0 0 15px;
}

.content {
  color: var(--color-text-3);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  color: var(--color-text-3);
  font-size: 12px;
  flex-shrink: 0;
}

.pager {
  margin-top: 12px;
  justify-content: center;
}
</style>
