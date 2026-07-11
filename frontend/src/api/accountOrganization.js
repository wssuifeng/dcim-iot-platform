import request from '@/utils/request'

export const getMemberships = () => request.get('/auth/memberships')
export const getPublicOrganizations = (keyword) =>
  request.get('/organizations/public', { params: { keyword } })
export const submitOrganizationApplication = (data) =>
  request.post('/organization-applications', data)
export const getMyOrganizationApplications = () =>
  request.get('/organization-applications/mine')
export const submitJoinApplication = (organizationId, note) =>
  request.post(`/organizations/${organizationId}/join-applications`, { note })
