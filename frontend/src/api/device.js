import request from '@/utils/request'

export function getDevicePage(params) {
  return request.get('/devices/page', { params })
}

export function getDeviceDetail(id) {
  return request.get(`/devices/${id}`)
}

export function createDevice(data) {
  return request.post('/devices', data)
}

export function updateDevice(id, data) {
  return request.put(`/devices/${id}`, data)
}

export function deleteDevice(id) {
  return request.delete(`/devices/${id}`)
}

export function updateDeviceStatus(id, status) {
  return request.put(`/devices/${id}/status`, null, { params: { status } })
}

export function updateDeviceAlertConfig(id, data) {
  return request.put(`/devices/${id}/alert-config`, data)
}

export function getAllDevices() {
  return request.get('/devices')
}
