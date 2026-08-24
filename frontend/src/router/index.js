import { createRouter, createWebHistory } from 'vue-router'
import { PLATFORM_ROLES, useAuthStore } from '@/stores/auth'
import { useOrganizationStore } from '@/stores/organization'
import { useScopeStore } from '@/stores/scope'

const { PLATFORM_ADMIN, ORG_ADMIN, OPERATOR, VIEWER } = PLATFORM_ROLES
const BUSINESS_ROLES = [ORG_ADMIN, OPERATOR, VIEWER]
const AUDIT_ROLES = [ORG_ADMIN, OPERATOR]

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
    path: '/setup',
    name: 'setup',
    component: () => import('@/views/setup/index.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'account/organizations',
        name: 'account-organizations',
        component: () => import('@/views/account/organizations/index.vue'),
        meta: { title: '我的组织', icon: 'OfficeBuilding', navigation: true, navigationOrder: 5 },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '仪表盘',
          icon: 'Odometer',
          navigation: true,
          navigationOrder: 10,
          scopeRoles: BUSINESS_ROLES,
        },
      },
      {
        path: 'organizations',
        name: 'organizations',
        component: () => import('@/views/organization/index.vue'),
        meta: {
          title: '组织管理',
          icon: 'OfficeBuilding',
          navigation: true,
          navigationOrder: 12,
          platformRoles: [PLATFORM_ADMIN],
          scopeRoles: [ORG_ADMIN],
        },
      },
      {
        path: 'cloud-rooms',
        name: 'cloud-rooms',
        component: () => import('@/views/cloud-room/index.vue'),
        meta: { title: '机房管理', icon: 'House', navigation: true, navigationOrder: 15, scopeRoles: BUSINESS_ROLES },
      },
      {
        path: 'devices',
        name: 'devices',
        component: () => import('@/views/device/index.vue'),
        meta: { title: '设备管理', icon: 'Monitor', navigation: true, navigationOrder: 20, scopeRoles: BUSINESS_ROLES },
      },
      {
        path: 'data-monitor',
        name: 'data-monitor',
        component: () => import('@/views/data-monitor/index.vue'),
        meta: { title: '数据监控', icon: 'TrendCharts', navigation: true, navigationOrder: 30, scopeRoles: BUSINESS_ROLES },
      },
      {
        path: 'simulation',
        name: 'simulation',
        component: () => import('@/views/simulation/index.vue'),
        meta: { title: '模拟运行', icon: 'SetUp', navigation: true, navigationOrder: 35, scopeRoles: BUSINESS_ROLES },
      },
      {
        path: 'alerts',
        name: 'alerts',
        component: () => import('@/views/alert/index.vue'),
        meta: { title: '告警管理', icon: 'Bell', navigation: true, navigationOrder: 40, scopeRoles: BUSINESS_ROLES },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/notification/index.vue'),
        meta: {
          title: '站内通知',
          icon: 'Message',
          navigation: true,
          navigationOrder: 45,
          platformRoles: [PLATFORM_ADMIN],
          scopeRoles: BUSINESS_ROLES,
        },
      },
      {
        path: 'metric',
        name: 'metric',
        component: () => import('@/views/metric/index.vue'),
        meta: {
          title: '指标字典',
          icon: 'DataLine',
          navigation: true,
          navigationOrder: 50,
          platformRoles: [PLATFORM_ADMIN],
          scopeRoles: BUSINESS_ROLES,
        },
      },
      {
        path: 'device-types',
        name: 'device-types',
        component: () => import('@/views/device-types/index.vue'),
        meta: {
          title: '设备类型',
          icon: 'Grid',
          navigation: true,
          navigationOrder: 60,
          platformRoles: [PLATFORM_ADMIN],
          scopeRoles: BUSINESS_ROLES,
        },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', navigation: true, navigationOrder: 70, platformRoles: [PLATFORM_ADMIN] },
      },
      {
        path: 'platform/registration',
        name: 'platform-registration',
        component: () => import('@/views/platform/registration.vue'),
        meta: { title: '注册审核', icon: 'Setting', navigation: true, navigationOrder: 72, platformRoles: [PLATFORM_ADMIN] },
      },
      {
        path: 'organization/members',
        name: 'organization-members',
        component: () => import('@/views/organization/members.vue'),
        meta: { title: '组织成员', icon: 'UserFilled', navigation: true, navigationOrder: 74, scopeRoles: [ORG_ADMIN] },
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/audit-log/index.vue'),
        meta: {
          title: '审计日志',
          icon: 'Document',
          navigation: true,
          navigationOrder: 80,
          platformRoles: [PLATFORM_ADMIN],
          scopeRoles: AUDIT_ROLES,
        },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

function hasRouteRole(route, authStore, scopeStore) {
  const platformRoles = route.meta.platformRoles || []
  const scopeRoles = route.meta.scopeRoles || []
  if (platformRoles.length === 0 && scopeRoles.length === 0) return true
  if (platformRoles.includes(authStore.platformRole)) return true
  return scopeStore.isReady && scopeRoles.includes(scopeStore.role)
}

function needsScope(route) {
  return (route.meta.scopeRoles || []).length > 0
}

function getFirstAccessibleRouteName(authStore, scopeStore) {
  const navigationRoutes =
    routes
      .find((route) => route.path === '/')
      ?.children?.filter((route) => route.meta.navigation) || []

  if (authStore.isPlatformAdmin) {
    return (
      navigationRoutes
        .filter((route) => route.meta.platformRoles?.includes(PLATFORM_ADMIN))
        .sort((left, right) => left.meta.navigationOrder - right.meta.navigationOrder)[0]?.name ||
      'platform-registration'
    )
  }

  if (scopeStore.isReady && scopeStore.role) {
    return (
      navigationRoutes
        .filter((route) => route.meta.scopeRoles?.includes(scopeStore.role))
        .sort((left, right) => left.meta.navigationOrder - right.meta.navigationOrder)[0]?.name ||
      'dashboard'
    )
  }

  return 'account-organizations'
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const organizationStore = useOrganizationStore()
  const scopeStore = useScopeStore()
  authStore.syncFromStorage()
  const loggedIn = authStore.isLoggedIn()

  if (to.meta.public && loggedIn) {
    if (!organizationStore.loaded) await organizationStore.loadMemberships().catch(() => [])
    if (organizationStore.currentOrganizationId) {
      await scopeStore.ensureCurrentScope({ silent: true })
    }
    return { name: getFirstAccessibleRouteName(authStore, scopeStore) }
  }

  if (to.meta.requiresAuth && !loggedIn) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? {} : { redirect: to.fullPath },
    }
  }

  if (!loggedIn) return true

  if (!organizationStore.loaded) {
    await organizationStore.loadMemberships().catch(() => [])
  }

  if (
    !authStore.isPlatformAdmin &&
    organizationStore.memberships.length === 0 &&
    to.name !== 'account-organizations'
  ) {
    return { name: 'account-organizations' }
  }

  if (needsScope(to) && !hasRouteRole(to, authStore, scopeStore)) {
    if (!authStore.isPlatformAdmin && !organizationStore.currentOrganizationId) {
      return { name: 'account-organizations' }
    }
    await scopeStore.ensureCurrentScope({ silent: true })
  }

  if (!hasRouteRole(to, authStore, scopeStore)) {
    return { name: getFirstAccessibleRouteName(authStore, scopeStore) }
  }

  return true
})

export default router
