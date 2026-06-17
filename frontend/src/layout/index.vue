/*
 * 文件功能:全局 Layout 组件，为后台管理页面提供统一的侧边栏 + 顶部栏 + 内容区布局
 *
 * 【路由结构】
 * Layout 作为嵌套路由的父组件，子页面通过 <router-view /> 渲染在内容区：
 *   /                   → Layout
 *     ├── /dashboard    → 仪表盘
 *     ├── /devices      → 设备管理
 *     ├── /data-monitor → 数据监控
 *     └── /alerts       → 告警管理
 *
 * 【侧边栏高亮】
 * 使用 el-menu 的 router 模式 + default-active 绑定 route.path，
 * 确保刷新后当前页面对应的菜单项仍然高亮
 */
<template>
  <el-container class="layout-container">
    <!-- 顶部栏：系统名称 + 用户下拉菜单（退出登录） -->
    <el-header class="layout-header">
      <span class="system-title">IoT-Platform</span>
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          {{ authStore.username }} <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>
    <el-container>
      <!-- 侧边栏：导航菜单（router 模式，点击自动跳转） -->
      <el-aside width="200px">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/devices">
            <el-icon><Monitor /></el-icon>
            <span>设备管理</span>
          </el-menu-item>
          <el-menu-item index="/data-monitor">
            <el-icon><TrendCharts /></el-icon>
            <span>数据监控</span>
          </el-menu-item>
          <el-menu-item index="/alerts">
            <el-icon><Bell /></el-icon>
            <span>告警管理</span>
          </el-menu-item>
          <el-menu-item index="/metric">
            <el-icon><DataLine /></el-icon>
            <span>指标字典</span>
          </el-menu-item>
          <el-menu-item index="/device-types">
            <el-icon><Grid /></el-icon>
            <span>设备类型</span>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="canViewAuditLog" index="/audit-logs">
            <el-icon><Document /></el-icon>
            <span>审计日志</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <!-- 内容区：子页面在此渲染 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 当前路由路径，用于侧边栏菜单高亮
const activeMenu = computed(() => route.path)

// 角色判断
const isAdmin = computed(() => authStore.role === 'ADMIN')
const canViewAuditLog = computed(() => authStore.role !== 'VIEWER')

/*
 * 方法功能:处理顶部栏下拉菜单命令
 * 参数:command - 'logout' 退出登录
 * 返回数据:无
 */
function handleCommand(command) {
  if (command === 'logout') {
    authStore.logout()     // 清除 Token 和角色信息
    router.push('/login')  // 跳转登录页
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e6e6e6;
}

.system-title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 侧边栏背景色与菜单背景统一 */
.el-aside {
  background-color: #304156;
}

/* 去掉 el-menu 默认右侧边框 */
.el-aside .el-menu {
  border-right: none;
}
</style>
