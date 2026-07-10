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
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
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
import { Bell } from '@element-plus/icons-vue'
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
const unreadCount = ref(0)
const realtimeConnected = ref(false)
let removeSummaryListener = null
let removeConnectionListener = null
let removeUnreadCountListener = null

const activeMenu = computed(() => route.path)
const effectiveRole = computed(() => scopeStore.role || authStore.normalizedRole)
const roleLabel = computed(() => ROLE_LABELS[effectiveRole.value] || effectiveRole.value)
const navigationItems = computed(() =>
  router
    .getRoutes()
    .filter((item) => item.meta.navigation)
    .filter((item) => {
      const roles = item.meta.roles || []
      return roles.length === 0 || roles.includes(effectiveRole.value)
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
  if (summary.resourceType === 'AlarmEvent') {
    await getAlarmEvent(summary.resourceId).catch(() => null)
  }
  await refreshUnreadCount()
}

function handleCommand(command) {
  if (command === 'logout') {
    void disconnectSummarySocket()
    scopeStore.resetCurrentScope()
    authStore.logout()
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
  border-bottom: 1px solid #e6e6e6;
  background: #ffffff;
}

.brand-area,
.header-actions,
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions {
  gap: 18px;
}

.system-title {
  color: #303133;
  font-size: 20px;
  font-weight: 700;
}

.user-info {
  cursor: pointer;
  color: #303133;
}

.role-label {
  color: #909399;
  font-size: 13px;
}

.layout-body {
  min-height: 0;
}

.el-aside {
  background-color: #304156;
}

.el-aside .el-menu {
  border-right: 0;
}

.el-main {
  min-width: 0;
  overflow: auto;
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
