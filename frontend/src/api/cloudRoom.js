import request from '@/utils/request'

export function getCloudRoomPage(params) {
  return request.get('/cloud-rooms/page', { params })
}

export function getCloudRoomById(id) {
  return request.get(`/cloud-rooms/${id}`)
}

export function createCloudRoom(data) {
  return request.post('/cloud-rooms', data)
}

export function updateCloudRoom(id, data) {
  return request.put(`/cloud-rooms/${id}`, data)
}

export function updateCloudRoomStatus(id, status) {
  return request.put(`/cloud-rooms/${id}/status`, null, { params: { status } })
}
