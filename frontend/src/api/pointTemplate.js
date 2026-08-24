import request from '@/utils/request'

export function getDeviceTypePointTemplates(typeId) {
  return request.get(`/point-templates/device-types/${typeId}`)
}

export function saveDeviceTypePointTemplates(typeId, items) {
  return request.put(`/point-templates/device-types/${typeId}`, { items })
}

export function getPlatformDeviceTypePointTemplates(typeId) {
  return request.get(`/platform/point-templates/device-types/${typeId}`)
}

export function savePlatformDeviceTypePointTemplates(typeId, items) {
  return request.put(`/platform/point-templates/device-types/${typeId}`, { items })
}
