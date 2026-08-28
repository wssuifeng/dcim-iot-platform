<template>
  <section class="room-workspace">
    <!-- 机房头：返回 + 名称 + 网关状态 + 未处理告警徽标 -->
    <header class="workspace-header">
      <el-button link :icon="ArrowLeft" class="back-link" @click="router.push('/dashboard')">
        运行驾驶舱
      </el-button>
      <div class="workspace-title">
        <h1>{{ room.roomName || `机房 #${roomId}` }}</h1>
        <span class="workspace-gateway">
          <state-dot :state="gatewayState" />
          <span class="workspace-gateway-text">{{ gatewayLabel }}</span>
        </span>
      </div>
      <el-badge v-if="activeAlarms > 0" :value="activeAlarms" :max="99" type="danger" class="workspace-badge">
        <span class="workspace-badge-label">未处理告警</span>
      </el-badge>
    </header>

    <el-tabs :model-value="activeTab" class="workspace-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="概览" name="overview" />
      <el-tab-pane label="设备与数据" name="devices" />
      <el-tab-pane label="告警" name="alarms" />
    </el-tabs>

    <router-view :room="room" :room-id="roomId" @room-alarms-loaded="handleAlarmsLoaded" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCloudRoomById, getRoomCredential } from '@/api/cloudRoom'
import { getAlarmEventPage } from '@/api/alarmEvent'

defineOptions({ name: 'RoomWorkspace' })

const route = useRoute()
const router = useRouter()

const room = reactive({ id: null, roomName: '', roomCode: '', location: '', status: 0 })
const credential = ref(null)
const activeAlarms = ref(0)

const roomId = computed(() => Number(route.params.roomId))
const activeTab = computed(() => {
  const name = route.name
  if (name?.endsWith('-devices')) return 'devices'
  if (name?.endsWith('-alarms')) return 'alarms'
  return 'overview'
})

// 网关状态口径与仪表盘/机房管理一致：5 分钟内在线
const gatewayState = computed(() => {
  const seen = credential.value?.lastSeenAt
  if (!seen) return 'stale'
  return Date.now() - new Date(seen).getTime() < 5 * 60 * 1000 ? 'online' : 'offline'
})
const gatewayLabel = computed(
  () => ({ online: '网关在线', offline: '网关离线', stale: '从未上线' })[gatewayState.value] || '',
)

function handleTabChange(tab) {
  router.replace({ name: `room-${tab}`, params: { roomId: roomId.value } })
}

function handleAlarmsLoaded(total) {
  activeAlarms.value = total
}

async function loadRoom() {
  try {
    const res = await getCloudRoomById(roomId.value)
    Object.assign(room, res.data || {})
  } catch {
    ElMessage.warning('机房不存在或已删除')
    router.replace('/dashboard')
    return
  }
  const credRes = await getRoomCredential(roomId.value).catch(() => null)
  credential.value = credRes?.data || null
  // 未处理告警数徽标（TRIGGERED/ACKED/HANDLING 合计取自活动口径）
  const alarmRes = await getAlarmEventPage({ current: 1, size: 1, cloudRoomId: roomId.value, state: 'TRIGGERED' })
    .catch(() => null)
  activeAlarms.value = Number(alarmRes?.data?.total || 0)
}

watch(() => route.params.roomId, () => {
  if (route.name?.startsWith('room-')) void loadRoom()
})

onMounted(loadRoom)
</script>

<style scoped>
.room-workspace {
  min-width: 0;
}

.workspace-header {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 10px;
}

.back-link {
  flex-shrink: 0;
}

.workspace-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.workspace-title h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-gateway {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.workspace-gateway-text {
  color: var(--color-text-3);
  font-size: 12px;
}

.workspace-badge {
  flex-shrink: 0;
}

.workspace-badge-label {
  color: var(--color-text-2);
  font-size: 13px;
}

.workspace-tabs {
  margin-bottom: 16px;
}
</style>
