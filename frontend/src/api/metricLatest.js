import request from '@/utils/request'

export function getDeviceMetricLatest(deviceId) {
  return request.get(`/metric-latest/device/${deviceId}`)
}

export function getMetricLatestPage(params) {
  return request.get('/metric-latest/page', { params })
}
