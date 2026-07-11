import request from '@/utils/request'

export const getRegistrationSettings = () => request.get('/platform/registration-settings')
export const updateRegistrationSettings = (data) =>
  request.put('/platform/registration-settings', data)
export const getOrganizationApplications = () =>
  request.get('/platform/organization-applications')
export const approveOrganizationApplication = (id, note) =>
  request.put(`/platform/organization-applications/${id}/approve`, { note })
export const rejectOrganizationApplication = (id, note) =>
  request.put(`/platform/organization-applications/${id}/reject`, { note })
