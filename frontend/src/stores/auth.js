/*
 * 文件功能:全局 AuthStore（Pinia Setup Store 风格），管理用户认证状态
 *
 * 【状态字段】
 * - token:   JWT Token 字符串（同时存储在 localStorage）
 * - role:    JWT 中的原始角色，兼容 ADMIN 和第四阶段四类角色
 * - normalizedRole: 将旧 ADMIN 映射为 ORG_ADMIN 后的导航角色
 * - username: 用户名，从 Token Payload 的 username 字段解析
 * - userId: 用户 ID，从 Token Payload 的 sub 字段解析
 *
 * 【Token 解析流程】
 *   login() 成功 → localStorage.setItem('token')
 *                → authStore.parseToken()
 *                → jwtDecode(token)
 *                → payload.role → role
 *                → payload.username → username
 *                → payload.sub → userId
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { jwtDecode } from 'jwt-decode'

export const PLATFORM_ROLES = Object.freeze({
  PLATFORM_ADMIN: 'PLATFORM_ADMIN',
  ORG_ADMIN: 'ORG_ADMIN',
  OPERATOR: 'OPERATOR',
  VIEWER: 'VIEWER',
})

const LEGACY_ROLE_ALIASES = Object.freeze({
  ADMIN: PLATFORM_ROLES.ORG_ADMIN,
})

function normalizeRole(role) {
  if (typeof role !== 'string') return ''
  const normalized = role.trim().toUpperCase()
  return LEGACY_ROLE_ALIASES[normalized] || normalized
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const role = ref('')
  const username = ref('')
  const userId = ref(null)
  const normalizedRole = computed(() => normalizeRole(role.value))

  /*
   * 方法功能:解析 JWT Token，提取角色和用户名
   * 参数:无（从 token.value 读取）
   * 返回数据:无
   *
   * 【异常处理】Token 格式无效时清空所有状态（防止脏数据残留）
   */
  function parseToken() {
    if (!token.value) {
      role.value = ''
      username.value = ''
      userId.value = null
      return
    }
    try {
      const payload = jwtDecode(token.value)
      role.value = payload.role || ''
      username.value = payload.username || ''
      userId.value = payload.sub || null
    } catch {
      token.value = ''
      role.value = ''
      username.value = ''
      userId.value = null
      localStorage.removeItem('token')
    }
  }

  function syncFromStorage() {
    const storedToken = localStorage.getItem('token') || ''
    if (storedToken === token.value) return
    token.value = storedToken
    parseToken()
  }

  function hasAnyRole(roles = []) {
    return roles.length === 0 || roles.includes(normalizedRole.value)
  }

  /*
   * 方法功能:退出登录，清除本地存储的 Token 和状态
   * 参数:无
   * 返回数据:无
   */
  function logout() {
    token.value = ''
    role.value = ''
    username.value = ''
    userId.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('rememberMe')
  }

  /*
   * 方法功能:判断当前是否已登录
   * 参数:无
   * 返回数据:boolean - Token 是否存在
   */
  function isLoggedIn() {
    return !!token.value && localStorage.getItem('token') === token.value
  }

  // Store 初始化时自动解析已存在的 Token（支持刷新页面后恢复登录状态）
  parseToken()

  return {
    token,
    role,
    username,
    userId,
    normalizedRole,
    parseToken,
    syncFromStorage,
    hasAnyRole,
    logout,
    isLoggedIn,
  }
})
