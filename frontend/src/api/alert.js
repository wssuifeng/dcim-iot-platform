/*
 * 文件功能:告警接口封装，提供告警分页查询、时间范围统计、批量处理等接口
 */
import request from '@/utils/request'
import axios from 'axios'

/*
 * 方法功能:按时间范围统计告警数量
 * 参数:params { startTime: string, endTime: string }
 * 返回数据:Promise-告警数量统计
 */
export function getAlertStatsByTimeRange(params) {
  return request.get('/alerts/stats/time-range', { params })
}

/*
 * 方法功能:分页查询告警列表
 * 参数:params { current, size, isHandled, alertLevel, deviceId, startTime, endTime }
 * 返回数据:Promise-Page<Alert> { records, total, current, size }
 */
export function getAlerts(params) {
  return request.get('/alerts', { params })
}

/*
 * 方法功能:批量处理告警（标记为已处理）
 * 参数:data { ids: number[], handleUser: string }
 * 返回数据:Promise-操作结果
 */
export function batchHandleAlerts(data) {
  return request.put('/alerts/batch-handle', data)
}

/**
 * 导出告警记录
 * @param {object} params 筛选参数 { deviceId, isHandled, alertLevel, startTime, endTime }
 */
export function exportAlerts(params) {
  return axios.get('/api/alerts/export', {
    params,
    responseType: 'blob',
    headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
  })
}
