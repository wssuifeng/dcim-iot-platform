import request from '@/utils/request'

export function getNotificationPage(params) {
  return request.get('/in-app-notifications/page', { params })
}

export function getUnreadNotificationCount(config = {}) {
  return request.get('/in-app-notifications/unread/count', config)
}

export function markNotificationRead(id) {
  return request.post(`/in-app-notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return request.post('/in-app-notifications/read-all')
}
