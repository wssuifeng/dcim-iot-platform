/*
 * 文件功能:告警看板接口封装，提供范围内告警聚合统计查询
 */
import request from '@/utils/request'

export function getAlarmDashboardStats() {
  return request.get('/dashboard/alarm-stats')
}
