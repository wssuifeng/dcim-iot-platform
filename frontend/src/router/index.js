/*
 * 文件功能:全局路由配置，定义页面路由表、嵌套路由结构和导航守卫
 *
 * 【路由架构】
 * - /login: 独立页面（无 Layout），未认证用户的唯一入口
 * - /: Layout 嵌套路由，所有后台管理页面共用一个 Layout 壳
 *
 * 【导航守卫逻辑】（beforeEach）
 *   1. 已登录访问 /login  → 重定向 /dashboard（避免重复登录）
 *   2. 未登录访问后台页面 → 重定向 /login（强制认证）
 *   3. 其他情况           → 正常放行
 */
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 登录页：独立全屏页面，不嵌套 Layout
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/index.vue'),
    },
    // 后台管理：Layout 作为父路由，子页面通过 <router-view /> 渲染
    {
      path: '/',
      component: () => import('@/layout/index.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/index.vue'),
        },
        {
          path: 'alerts',
          name: 'alerts',
          component: () => import('@/views/alert/index.vue'),
        },
        {
          path: 'devices',
          name: 'devices',
          component: () => import('@/views/device/index.vue'),
        },
        {
          path: 'data-monitor',
          name: 'data-monitor',
          component: () => import('@/views/data-monitor/index.vue'),
        },
        {
          path: 'metric',
          name: 'metric',
          component: () => import('@/views/metric/index.vue'),
        },
        {
          path: 'device-types',
          name: 'device-types',
          component: () => import('@/views/device-types/index.vue'),
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/user/index.vue'),
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('@/views/audit-log/index.vue'),
        },
      ],
    },
  ],
})

/*
 * 方法功能:全局前置守卫，在每次路由跳转前执行认证检查
 * 参数:
 *   - to: 目标路由对象
 *   - from: 来源路由对象
 *   - next: 放行函数（Vue Router 4 也支持 return 重定向）
 * 返回数据:无（调用 next 放行或重定向）
 */
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  // 已登录用户访问登录页 → 重定向到仪表盘
  if (to.path === '/login' && token) {
    return next('/dashboard')
  }

  // 未登录用户访问后台页面 → 重定向到登录页
  if (to.path !== '/login' && !token) {
    return next('/login')
  }

  next()
})

export default router
