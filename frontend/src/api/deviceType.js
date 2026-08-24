import request from '@/utils/request'

export function getDeviceTypes() {
  return request.get('/device-types')
}

export function getDeviceTypeById(id) {
  return request.get(`/device-types/${id}`)
}

export function createDeviceType(data) {
  return request.post('/device-types', data)
}

export function updateDeviceType(id, data) {
  return request.put(`/device-types/${id}`, data)
}

export function deleteDeviceType(id) {
  return request.delete(`/device-types/${id}`)
}
