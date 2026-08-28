/*
 * 文件功能:告警看板接口封装，提供范围内告警聚合统计查询
 */
import request from '@/utils/request'

export function getAlarmDashboardStats(params) {
  return request.get('/dashboard/alarm-stats', { params })
}

// 多机房对比聚合（跨机房检索对比页）
export function compareRooms(params) {
  return request.get('/dashboard/comparison', { params })
}
