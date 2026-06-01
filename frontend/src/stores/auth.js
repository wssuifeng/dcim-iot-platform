/*
 * 文件功能:全局 AuthStore（Pinia Setup Store 风格），管理用户认证状态
 *
 * 【状态字段】
 * - token:   JWT Token 字符串（同时存储在 localStorage）
 * - role:    用户角色（ADMIN / OPERATOR / VIEWER），从 Token Payload 解析
 * - username: 用户名，从 Token Payload 的 sub 字段解析
 *
 * 【Token 解析流程】
 *   login() 成功 → localStorage.setItem('token')
 *                → authStore.parseToken()
 *                → jwtDecode(token)
 *                → payload.role → role
 *                → payload.sub  → username
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { jwtDecode } from 'jwt-decode'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const role = ref('')
  const username = ref('')

  /*
   * 方法功能:解析 JWT Token，提取角色和用户名
   * 参数:无（从 token.value 读取）
   * 返回数据:无
   *
   * 【异常处理】Token 格式无效时清空所有状态（防止脏数据残留）
   */
  function parseToken() {
    if (!token.value) return
    try {
      const payload = jwtDecode(token.value)
      role.value = payload.role || ''
      username.value = payload.sub || ''
    } catch {
      token.value = ''
      role.value = ''
      username.value = ''
    }
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
    localStorage.removeItem('token')
    localStorage.removeItem('rememberMe')
  }

  /*
   * 方法功能:判断当前是否已登录
   * 参数:无
   * 返回数据:boolean - Token 是否存在
   */
  function isLoggedIn() {
    return !!token.value
  }

  // Store 初始化时自动解析已存在的 Token（支持刷新页面后恢复登录状态）
  parseToken()

  return { token, role, username, parseToken, logout, isLoggedIn }
})
