<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="brand-area">
        <span class="system-title">IoT-Platform</span>
        <el-tag v-if="scopeStore.isReady" size="small" effect="plain" type="info">
          {{ scopeDisplay }}
        </el-tag>
      </div>

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
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

onMounted(() => {
  void scopeStore.ensureCurrentScope({ silent: true })
})

function handleCommand(command) {
  if (command === 'logout') {
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
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
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
</style>
