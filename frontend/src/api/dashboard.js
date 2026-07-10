/*
 * 文件功能:仪表盘接口封装，提供仪表盘统计数据查询
 */
import request from '@/utils/request'

/*
 * 方法功能:获取仪表盘统计数据（设备统计 + 告警统计）
 * 参数:无
 * 返回数据:Promise-DashboardStatsDTO { totalDevices, onlineDevices, statusDistribution, totalAlerts, unhandledAlerts }
 */
export function getDashboardStats() {
  return request.get('/dashboard/stats')
}

export function getAlarmDashboardStats() {
  return request.get('/dashboard/alarm-stats')
}
