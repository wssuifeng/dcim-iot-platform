import request from '@/utils/request'

export function getOrganizationPage(params) {
  return request.get('/organizations/page', { params })
}

export function getOrganizationById(id) {
  return request.get(`/organizations/${id}`)
}

export function createOrganization(data) {
  return request.post('/organizations', data)
}

export function updateOrganization(id, data) {
  return request.put(`/organizations/${id}`, data)
}

export function updateOrganizationStatus(id, status) {
  return request.put(`/organizations/${id}/status`, null, { params: { status } })
}
