import request from '@/utils/request'

export function getDevicePointBindings(deviceId) {
  return request.get(`/device-points/device/${deviceId}`)
}

export function getDevicePointBindingPage(params) {
  return request.get('/device-points/page', { params })
}

export function bindDevicePoint(data) {
  return request.post('/device-points', data)
}

export function updateDevicePointStatus(bindingId, status) {
  return request.put(`/device-points/${bindingId}/status`, null, { params: { status } })
}

export function unbindDevicePoint(bindingId) {
  return request.delete(`/device-points/${bindingId}`)
}

export function initializeDevicePoints(deviceId, typeId) {
  return request.post(`/device-points/device/${deviceId}/init-from-template/${typeId}`)
}
