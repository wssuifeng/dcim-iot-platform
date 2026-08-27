import request from '@/utils/request'

export function getUserPage(params) { return request.get('/users/page', { params }) }
export function getUserById(id) { return request.get(`/users/${id}`) }
export function searchUser(username) { return request.get('/users/search', { params: { username } }) }
export function createUser(data) { return request.post('/users', data) }
export function updateUser(id, data) { return request.put(`/users/${id}`, data) }
export function resetUserPassword(id, data) { return request.put(`/users/${id}/password`, data) }
export function updateUserStatus(id, status) { return request.put(`/users/${id}/status`, null, { params: { status } }) }
export function deleteUser(id) { return request.delete(`/users/${id}`) }
