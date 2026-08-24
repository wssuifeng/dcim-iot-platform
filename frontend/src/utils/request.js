/*
 * 文件功能:Axios 封装，提供全局请求/响应拦截器
 *
 * 【拦截器链路】
 *   请求拦截器:
 *     每个请求自动从 localStorage 读取 token 附加到 Authorization Header
 *     → Bearer <token>
 *
 *   响应拦截器:
 *     HTTP 200 → 返回 response.data（业务层无需手动 .data）
 *     HTTP 401 → 清除 token → 跳转 /login（Token 过期或无效）
 *     HTTP 403 → ElMessage.error('权限不足')（无权访问该资源）
 *     其他错误 → ElMessage.error 展示后端消息
 *
 * 【使用方式】
 *   import request from '@/utils/request'
 *   const data = await request.get('/devices/page', { params: { current: 1, size: 10 } })
 *
 *   ❌ 禁止直接 import axios，会绕过拦截器
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true, // 跨域请求携带 Cookie（配合后端 CORS allowCredentials=true）
})

export function requestBlob(config) {
  return request({ ...config, responseType: 'blob' })
}

// 请求拦截器：自动附加 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  const organizationId = localStorage.getItem('currentOrganizationId')
  if (organizationId) {
    config.headers = config.headers || {}
    config.headers['X-Organization-Id'] = organizationId
  }
  return config
})

function getErrorMessage(error, fallback) {
  return error.response?.data?.message || error.message || fallback
}

// 响应拦截器：统一错误处理 + 业务数据解包
request.interceptors.response.use(
  (response) =>
    response.config?.responseType === 'blob' ? response : response.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('rememberMe')
      localStorage.removeItem('currentOrganizationId')
      window.dispatchEvent(new Event('auth:unauthorized'))
      if (router.currentRoute.value.path !== '/login') {
        router.replace({
          path: '/login',
          query: { redirect: router.currentRoute.value.fullPath },
        })
      }
    } else if (error.response?.status === 403) {
      if (!error.config?.skipErrorMessage) {
        ElMessage.error(getErrorMessage(error, '权限不足，无法执行此操作'))
      }
    } else if (!error.config?.skipErrorMessage) {
      ElMessage.error(getErrorMessage(error, '请求失败'))
    }
    return Promise.reject(error)
  },
)

export default request
