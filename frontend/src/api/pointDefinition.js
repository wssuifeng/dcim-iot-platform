import request from '@/utils/request'

export function getPointDefinitionPage(params) {
  return request.get('/point-definitions/page', { params })
}

export function getPointDefinitionById(id) {
  return request.get(`/point-definitions/${id}`)
}

export function createPointDefinition(data) {
  return request.post('/point-definitions', data)
}

export function updatePointDefinition(id, data) {
  return request.put(`/point-definitions/${id}`, data)
}

export function deletePointDefinition(id) {
  return request.delete(`/point-definitions/${id}`)
}
