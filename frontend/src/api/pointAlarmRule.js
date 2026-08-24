import request from '@/utils/request'

export function getPointAlarmRulePage(params) {
  return request.get('/point-alarm-rules/page', { params })
}

export function createPointAlarmRule(data) {
  return request.post('/point-alarm-rules', data)
}

export function updatePointAlarmRule(id, data) {
  return request.put(`/point-alarm-rules/${id}`, data)
}

export function deletePointAlarmRule(id) {
  return request.delete(`/point-alarm-rules/${id}`)
}

export function updatePointAlarmRuleStatus(id, enabled) {
  return request.put(`/point-alarm-rules/${id}/status`, null, { params: { enabled } })
}

export function getEffectivePointAlarmRules(deviceId, pointId) {
  const params = { deviceId }
  if (pointId !== undefined && pointId !== null) params.pointId = pointId
  return request.get('/point-alarm-rules/effective', { params })
}
