import request from '@/utils/request'

export function getGlobalMetricRetention() {
  return request.get('/metric-retention-policies/global')
}

export function getPointMetricRetention(pointId) {
  return request.get(`/metric-retention-policies/point/${pointId}`)
}

export function getEffectiveMetricRetention(pointId = 0) {
  return request.get(`/metric-retention-policies/effective/${pointId}`)
}

export function getMetricRetentionList() {
  return request.get('/metric-retention-policies/list')
}

export function upsertMetricRetention(data) {
  return request.put('/metric-retention-policies', data)
}
