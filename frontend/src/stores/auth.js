/*
 * 文件功能:全局 AuthStore（Pinia Setup Store 风格），管理用户认证状态
 *
 * 【状态字段】
 * - token:   JWT Token 字符串（同时存储在 localStorage）
 * - role:    JWT 中的角色（ADMIN/OPERATOR/VIEWER）
 * - username: 用户名，从 Token Payload 的 username 字段解析
 * - userId: 用户 ID，从 Token Payload 的 sub 字段解析
 *
 * 【Token 解析流程】
 *   login() 成功 → localStorage.setItem('token')
 *                → authStore.parseToken()
 *                → jwtDecode(token)
 *                → payload.role → role（历史 token 的 platformRole 归一化为 ADMIN）
 *                → payload.username → username
 *                → payload.sub → userId
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { jwtDecode } from 'jwt-decode'

export const ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  OPERATOR: 'OPERATOR',
  VIEWER: 'VIEWER',
})

const ADMIN_ALIASES = ['ADMIN', 'ORG_ADMIN', 'PLATFORM_ADMIN']

function normalizeRole(role) {
  if (typeof role !== 'string') return ''
  const normalized = role.trim().toUpperCase()
  if (ADMIN_ALIASES.includes(normalized)) return ROLES.ADMIN
  if (Object.values(ROLES).includes(normalized)) return normalized
  return ''
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
      // 新 token 用 role 声明；历史多组织 token 的 platformRole 声明归一化为 ADMIN
      role.value = normalizeRole(payload.role || payload.platformRole)
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
    localStorage.removeItem('currentOrganizationId')
  }

  window.addEventListener('auth:unauthorized', logout)

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
    isAdmin: computed(() => normalizedRole.value === ROLES.ADMIN),
    parseToken,
    syncFromStorage,
    hasAnyRole,
    logout,
    isLoggedIn,
  }
})
