<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="brand-area">
        <span class="system-title">IoT-Platform</span>
        <el-tag v-if="scopeStore.isReady" size="small" effect="plain" type="info">
          {{ scopeDisplay }}
        </el-tag>
      </div>

      <div class="header-actions">
        <OrganizationSwitcher />
        <el-tooltip :content="themeTooltip">
          <el-button circle :icon="themeIcon" aria-label="切换主题" @click="themeStore.toggle" />
        </el-tooltip>
        <el-tooltip :content="realtimeConnected ? '站内通知' : '站内通知（实时连接中断）'">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
            <el-button circle :icon="Bell" aria-label="站内通知" @click="router.push('/notifications')" />
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
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bell, Sunny, Moon } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { getAlarmEvent } from '@/api/alarmEvent'
import { getUnreadNotificationCount } from '@/api/notification'
import {
  connectSummarySocket,
  disconnectSummarySocket,
  onSummary,
  onSummaryConnectionChange,
  onUnreadNotificationCountChange,
} from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import { useOrganizationStore } from '@/stores/organization'
import { useThemeStore, THEME_LIGHT } from '@/stores/theme'
import OrganizationSwitcher from '@/components/OrganizationSwitcher.vue'

defineOptions({ name: 'AppLayout' })

const ROLE_LABELS = Object.freeze({
  PLATFORM_ADMIN: '平台管理员',
  ORG_ADMIN: '组织管理员',
  OPERATOR: '运维人员',
  VIEWER: '查看者',
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const scopeStore = useScopeStore()
const organizationStore = useOrganizationStore()
const themeStore = useThemeStore()
const unreadCount = ref(0)
const realtimeConnected = ref(false)
let removeSummaryListener = null
let removeConnectionListener = null
let removeUnreadCountListener = null

const activeMenu = computed(() => route.path)
const effectiveRole = computed(() => scopeStore.role)
const roleLabel = computed(() => {
  if (authStore.isPlatformAdmin) return ROLE_LABELS.PLATFORM_ADMIN
  return ROLE_LABELS[effectiveRole.value] || effectiveRole.value
})
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
      const platformRoles = item.meta.platformRoles || []
      const scopeRoles = item.meta.scopeRoles || []
      if (platformRoles.length === 0 && scopeRoles.length === 0) return true
      return (
        platformRoles.includes(authStore.platformRole) ||
        (scopeStore.isReady && scopeRoles.includes(effectiveRole.value))
      )
    })
    .sort((left, right) => left.meta.navigationOrder - right.meta.navigationOrder),
)
const scopeDisplay = computed(() => {
  if (scopeStore.scopeType === 'ROOMS') {
    return `${scopeStore.scopeLabel} · ${scopeStore.cloudRoomIds.length}`
  }
  if (scopeStore.scopeType === 'ORG_ALL') {
    return `${scopeStore.scopeLabel} · ${scopeStore.organizationIds.length}`
  }
  return scopeStore.scopeLabel
})

onMounted(async () => {
  await organizationStore.loadMemberships().catch(() => [])
  void scopeStore.ensureCurrentScope({ silent: true })
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
    await getAlarmEvent(summary.resourceId).catch(() => null)
  }
  await refreshUnreadCount()
}

function handleCommand(command) {
  if (command === 'logout') {
    void disconnectSummarySocket()
    scopeStore.resetCurrentScope()
    authStore.logout()
    organizationStore.reset()
    router.push('/login')
  }
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
