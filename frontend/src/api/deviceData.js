import request from '@/utils/request'

export function getDeviceData(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}`, { params })
}

export function getDeviceLatestData(deviceId) {
  return request.get(`/device-data/device/${deviceId}/latest`)
}

export function getDeviceDataByRange(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}/range`, { params })
}

export function getDeviceDataStatistics(deviceId, params) {
  return request.get(`/device-data/device/${deviceId}/statistics`, { params })
}
