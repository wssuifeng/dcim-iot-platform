<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="brand-area">
        <span class="system-title">IoT-Platform</span>
      </div>

      <div class="header-actions">
        <el-tooltip :content="themeTooltip">
          <el-button circle :icon="themeIcon" aria-label="切换主题" @click="themeStore.toggle" />
        </el-tooltip>
        <el-tooltip :content="alarmSoundMuted ? '告警声音（已静音）' : '告警声音（开启）'">
          <el-button circle :icon="alarmSoundMuted ? Mute : Bell" aria-label="告警声音开关" @click="toggleAlarmSound" />
        </el-tooltip>
        <el-tooltip :content="realtimeConnected ? '站内通知' : '站内通知（实时连接中断）'">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-button circle :icon="Bell" aria-label="站内通知" @click="notificationVisible = true" />
          </el-badge>
        </el-tooltip>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <span class="role-label">{{ roleLabel }}</span>
            <span>{{ authStore.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <el-aside width="200px">
        <el-menu
          :default-active="activeMenu"
          router
          :background-color="menuBg"
          :text-color="menuText"
          :active-text-color="menuActive"
        >
          <el-menu-item
            v-for="item in navigationItems"
            :key="item.name"
            :index="item.path"
          >
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <notification-modal
      v-model:visible="notificationVisible"
      :unread-count="unreadCount"
      @read="refreshUnreadCount"
    />
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell, Mute, Sunny, Moon } from '@element-plus/icons-vue'
import { ElMessage, ElNotification } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getAlarmEvent } from '@/api/alarmEvent'
import NotificationModal from '@/components/NotificationModal.vue'
import { getUnreadNotificationCount } from '@/api/notification'
import { isAlarmSoundMuted, playAlarmSound, setAlarmSoundMuted } from '@/utils/alarmSound'
import {
  connectSummarySocket,
  disconnectSummarySocket,
  onSummary,
  onSummaryConnectionChange,
  onUnreadNotificationCountChange,
} from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, THEME_LIGHT } from '@/stores/theme'

defineOptions({ name: 'AppLayout' })

const ROLE_LABELS = Object.freeze({
  ADMIN: '管理员',
  OPERATOR: '运维人员',
  VIEWER: '查看者',
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const unreadCount = ref(0)
const realtimeConnected = ref(false)
const notificationVisible = ref(false)
let removeSummaryListener = null
let removeConnectionListener = null
let removeUnreadCountListener = null

// 侧栏高亮：工作台路由（/rooms/...）不在侧栏，无匹配项即不高亮（工作台自带返回入口）
const activeMenu = computed(() =>
  route.path.startsWith('/rooms/') ? '' : route.path,
)
const roleLabel = computed(() => ROLE_LABELS[authStore.normalizedRole] || authStore.normalizedRole)
const themeIcon = computed(() => (themeStore.theme === THEME_LIGHT ? Moon : Sunny))
const themeTooltip = computed(() => (themeStore.theme === THEME_LIGHT ? '切换夜间模式' : '切换日间模式'))
const menuBg = computed(() => (themeStore.theme === THEME_LIGHT ? '#ffffff' : '#0f172a'))
const menuText = computed(() => (themeStore.theme === THEME_LIGHT ? '#475569' : '#cbd5e1'))
const menuActive = computed(() => (themeStore.theme === THEME_LIGHT ? '#2563eb' : '#00d4ff'))
const navigationItems = computed(() =>
  router
    .getRoutes()
    .filter((item) => item.meta.navigation)
    .filter((item) => {
      const allowed = item.meta.roles || []
      return allowed.length === 0 || allowed.includes(authStore.normalizedRole)
    })
    .sort((left, right) => left.meta.navigationOrder - right.meta.navigationOrder),
)

onMounted(async () => {
  await refreshUnreadCount()
  removeSummaryListener = onSummary(handleSummary)
  removeConnectionListener = onSummaryConnectionChange((connected) => {
    realtimeConnected.value = connected
  })
  removeUnreadCountListener = onUnreadNotificationCountChange((count) => {
    unreadCount.value = count
  })
  connectSummarySocket()
})

onBeforeUnmount(() => {
  removeSummaryListener?.()
  removeConnectionListener?.()
  removeUnreadCountListener?.()
})

async function refreshUnreadCount() {
  try {
    const result = await getUnreadNotificationCount({ skipErrorMessage: true })
    unreadCount.value = Number(result.data) || 0
  } catch {
    unreadCount.value = 0
  }
}

async function handleSummary(summary) {
  if (summary.resourceType === 'ALARM_EVENT') {
    await popAlarmNotification(summary)
  }
  await refreshUnreadCount()
}

// 右上角卡片式告警弹窗：摘要自带 title/level 时直接弹，否则回查告警详情
async function popAlarmNotification(summary) {
  const titles = {
    ALARM_CREATED: '告警触发',
    ALARM_UPGRADED: '告警升级',
    ALARM_RECOVERED: '告警恢复',
  }
  let title = titles[summary.eventType] || '告警动态'
  let message = summary.title
  let type = summary.state === 'RECOVERED' ? 'success' : 'warning'
  let level = summary.level ?? 5

  if (!message) {
    const result = await getAlarmEvent(summary.resourceId).catch(() => null)
    const event = result?.data
    if (!event) return
    message = event.alarmMessage || `告警事件 #${summary.resourceId}`
    level = event.alarmLevel ?? 5
    type = event.state === 'RECOVERED' ? 'success' : levelType(level)
    if (level <= 2) title = '紧急告警'
  } else if (summary.level != null) {
    type = summary.state === 'RECOVERED' ? 'success' : levelType(summary.level)
    if (summary.level <= 2 && summary.state !== 'RECOVERED') title = '紧急告警'
  }

  // 告警触发/升级播放提醒音（恢复不响）；用户决策：手动关闭模式
  if (summary.eventType !== 'ALARM_RECOVERED' && summary.state !== 'RECOVERED') {
    playAlarmSound()
  }

  enqueueNotification({
    title,
    message,
    type,
    // 告警类不自动消失（手动关闭队列）；恢复类保持 6s 自动消失
    duration: summary.eventType === 'ALARM_RECOVERED' || summary.state === 'RECOVERED' ? 6000 : 0,
    position: 'top-right',
    showClose: true,
    // 卡片顶部级别色带（theme.css 五级色带令牌）
    customClass: `alarm-notify alarm-notify-l${level}`,
  })
}

// Element Plus 同一 tick 连发通知时，前序通知 offsetHeight 尚为 0（display 未解除），
// 偏移只累加间距导致卡片互相叠压——用小间隔队列错峰派发规避。
// 告警类通知 duration=0 不自动消失，堆叠上限 5 张：第 6 张起顶掉最旧一张。
const notificationQueue = []
const activeAlarmNotifications = []
let notificationFlushing = false
const MAX_ALARM_NOTIFICATIONS = 5

function enqueueNotification(options) {
  notificationQueue.push(options)
  if (notificationFlushing) return
  notificationFlushing = true
  const flush = () => {
    const next = notificationQueue.shift()
    if (next) {
      const manualClose = next.duration === 0
      if (manualClose) {
        // 堆叠超限时顶掉最旧的手动关闭卡片
        while (activeAlarmNotifications.length >= MAX_ALARM_NOTIFICATIONS) {
          activeAlarmNotifications.shift()?.close()
        }
      }
      const handle = ElNotification(next)
      if (manualClose) activeAlarmNotifications.push(handle)
    }
    if (notificationQueue.length) {
      setTimeout(flush, 120)
    } else {
      notificationFlushing = false
    }
  }
  setTimeout(flush, 0)
}

function levelType(level) {
  // 告警级别 1-5（小=严重）：1/2 错误级、3/4 警告级、5 提示级
  if (level <= 2) return 'error'
  if (level <= 4) return 'warning'
  return 'info'
}

function handleCommand(command) {
  if (command === 'logout') {
    void disconnectSummarySocket()
    authStore.logout()
    router.push('/login')
  }
}

// 告警音效开关（持久化 localStorage）
const alarmSoundMuted = ref(isAlarmSoundMuted())

function toggleAlarmSound() {
  alarmSoundMuted.value = !alarmSoundMuted.value
  setAlarmSoundMuted(alarmSoundMuted.value)
  ElMessage.success(alarmSoundMuted.value ? '告警声音已静音' : '告警声音已开启')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-header);
}

.brand-area,
.header-actions,
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions {
  gap: 14px;
}

.system-title {
  color: var(--color-text);
  font-size: 20px;
  font-weight: 700;
}

.user-info {
  cursor: pointer;
  color: var(--color-text);
}

.role-label {
  color: var(--color-text-2);
  font-size: 13px;
}

.layout-body {
  min-height: 0;
}

.el-aside {
  background-color: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.el-aside .el-menu {
  border-right: 0;
  background-color: transparent;
}

.el-main {
  min-width: 0;
  overflow: auto;
  background-color: var(--color-bg);
}

@media (max-width: 768px) {
  .layout-header {
    gap: 12px;
    height: 58px;
    padding: 0 12px;
  }

  .system-title {
    font-size: 16px;
  }

  .brand-area .el-tag,
  .role-label {
    display: none;
  }

  .header-actions {
    gap: 10px;
  }

  .layout-body {
    flex-direction: column;
  }

  .el-aside {
    width: 100% !important;
    height: 54px;
    overflow-x: auto;
    overflow-y: hidden;
  }

  .el-aside .el-menu {
    display: flex;
    width: max-content;
    height: 54px;
  }

  .el-aside :deep(.el-menu-item) {
    height: 54px;
    padding: 0 14px;
    line-height: 54px;
  }

  .el-main {
    width: 100%;
    padding: 14px 12px;
  }
}
</style>
