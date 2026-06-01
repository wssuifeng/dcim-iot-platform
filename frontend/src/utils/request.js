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
  withCredentials: true,  // 跨域请求携带 Cookie（配合后端 CORS allowCredentials=true）
})

// 请求拦截器：自动附加 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一错误处理 + 业务数据解包
request.interceptors.response.use(
  (response) => response.data,  // 业务层直接拿到 data，不需要 response.data.data
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    } else if (error.response?.status === 403) {
      ElMessage.error('权限不足，无法执行此操作')
    } else {
      ElMessage.error(error.response?.data?.message || '请求失败')
    }
    return Promise.reject(error)
  },
)

export default request
