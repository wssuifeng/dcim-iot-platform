import request from '@/utils/request'

export function getDeviceTypes() {
  return request.get('/device-types')
}
