import request from '@/utils/request'

export function getCredentialPage(params) {
  return request.get('/ingest-credentials/page', { params })
}

export function getCredentialById(id) {
  return request.get(`/ingest-credentials/${id}`)
}

export function createCredential(data) {
  return request.post('/ingest-credentials', data)
}

export function updateCredential(id, data) {
  return request.put(`/ingest-credentials/${id}`, data)
}

export function updateCredentialStatus(id, status) {
  return request.put(`/ingest-credentials/${id}/status`, null, { params: { status } })
}

export function resetCredentialSecret(id) {
  return request.put(`/ingest-credentials/${id}/secret`)
}

export function deleteCredential(id) {
  return request.delete(`/ingest-credentials/${id}`)
}
