import request from '@/utils/request'

export const getMembers = (organizationId) => request.get(`/organizations/${organizationId}/members`)
export const getJoinApplications = (organizationId) => request.get(`/organizations/${organizationId}/join-applications`)
export const approveJoinApplication = (organizationId, id, data) => request.put(`/organizations/${organizationId}/join-applications/${id}/approve`, data)
export const rejectJoinApplication = (organizationId, id, note) => request.put(`/organizations/${organizationId}/join-applications/${id}/reject`, { note })
export const updateMemberRole = (organizationId, userId, role) => request.put(`/organizations/${organizationId}/members/${userId}/role`, { role })
export const removeMember = (organizationId, userId) => request.delete(`/organizations/${organizationId}/members/${userId}`)
export const updateJoinSetting = (organizationId, enabled) => request.put(`/organizations/${organizationId}/join-setting`, null, { params: { enabled } })
