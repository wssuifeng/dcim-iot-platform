import { createRouter, createWebHistory } from 'vue-router'
import { ROLES, useAuthStore } from '@/stores/auth'

const { ADMIN, OPERATOR, VIEWER } = ROLES
const ALL_ROLES = [ADMIN, OPERATOR, VIEWER]
const AUDIT_ROLES = [ADMIN, OPERATOR]

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/register/index.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer', navigation: true, navigationOrder: 10, roles: ALL_ROLES },
      },
      {
        path: 'cloud-rooms',
        name: 'cloud-rooms',
        component: () => import('@/views/cloud-room/index.vue'),
        meta: { title: '机房管理', icon: 'House', navigation: true, navigationOrder: 15, roles: ALL_ROLES },
      },
      {
        path: 'alerts',
        name: 'alerts',
        component: () => import('@/views/alert/index.vue'),
        meta: { title: '全局告警流', icon: 'Bell', navigation: true, navigationOrder: 18, roles: ALL_ROLES },
      },
      {
        path: 'search',
        name: 'cross-room-search',
        component: () => import('@/views/cross-room-search/index.vue'),
        meta: { title: '跨机房检索', icon: 'Search', navigation: true, navigationOrder: 20, roles: ALL_ROLES },
      },
      // 机房工作台：机房为一等导航上下文（下钻入口在驾驶舱机房卡片与机房管理页）
      {
        path: 'rooms/:roomId',
        component: () => import('@/views/room-workspace/RoomWorkspace.vue'),
        children: [
          { path: '', redirect: { name: 'room-overview' } },
          {
            path: 'overview',
            name: 'room-overview',
            component: () => import('@/views/room-workspace/tabs/OverviewTab.vue'),
            meta: { title: '机房概览', roles: ALL_ROLES },
          },
          {
            path: 'devices',
            name: 'room-devices',
            component: () => import('@/views/room-workspace/tabs/DevicesTab.vue'),
            meta: { title: '设备与数据', roles: ALL_ROLES },
          },
          {
            path: 'alarms',
            name: 'room-alarms',
            component: () => import('@/views/room-workspace/tabs/AlarmsTab.vue'),
            meta: { title: '机房告警', roles: ALL_ROLES },
          },
        ],
      },
      {
        path: 'alarm-templates',
        name: 'alarm-templates',
        component: () => import('@/views/alarm-template/index.vue'),
        meta: { title: '告警模版', icon: 'AlarmClock', navigation: true, navigationOrder: 45, roles: ALL_ROLES },
      },
      {
        path: 'metric',
        name: 'metric',
        component: () => import('@/views/metric/index.vue'),
        meta: { title: '指标字典', icon: 'DataLine', navigation: true, navigationOrder: 50, roles: ALL_ROLES },
      },
      {
        path: 'ingest-mapping',
        name: 'ingest-mapping',
        component: () => import('@/views/ingest-mapping/index.vue'),
        meta: { title: '零散设备接入', icon: 'Connection', navigation: true, navigationOrder: 55, roles: [ADMIN] },
      },
      {
        path: 'device-types',
        name: 'device-types',
        component: () => import('@/views/device-types/index.vue'),
        meta: { title: '设备类型', icon: 'Grid', navigation: true, navigationOrder: 60, roles: ALL_ROLES },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', navigation: true, navigationOrder: 70, roles: [ADMIN] },
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/audit-log/index.vue'),
        meta: { title: '审计日志', icon: 'Document', navigation: true, navigationOrder: 80, roles: AUDIT_ROLES },
      },
      {
        path: 'webhook-channels',
        name: 'webhook-channels',
        component: () => import('@/views/webhook-channel/index.vue'),
        meta: { title: '告警通道', icon: 'Bell', navigation: true, navigationOrder: 85, roles: [ADMIN] },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/*
 * 函数功能:判断当前角色是否可访问目标路由
 */
function hasRouteRole(route, role) {
  const allowed = route.meta.roles || []
  return allowed.length === 0 || allowed.includes(role)
}

function getFirstAccessibleRouteName(role) {
  const navigationRoutes =
    routes
      .find((route) => route.path === '/')
      ?.children?.filter((route) => route.meta.navigation) || []

  return (
    navigationRoutes
      .filter((route) => hasRouteRole(route, role))
      .sort((left, right) => left.meta.navigationOrder - right.meta.navigationOrder)[0]?.name ||
    'dashboard'
  )
}

router.beforeEach((to) => {
  const authStore = useAuthStore()
  authStore.syncFromStorage()
  const loggedIn = authStore.isLoggedIn()

  if (to.meta.public && loggedIn) {
    return { name: getFirstAccessibleRouteName(authStore.normalizedRole) }
  }

  if (to.meta.requiresAuth && !loggedIn) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? {} : { redirect: to.fullPath },
    }
  }

  if (!loggedIn) return true

  if (!hasRouteRole(to, authStore.normalizedRole)) {
    return { name: getFirstAccessibleRouteName(authStore.normalizedRole) }
  }

  return true
})

export default router
