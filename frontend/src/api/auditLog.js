import request from '@/utils/request'

export function getAuditLogPage(params) { return request.get('/audit-logs/page', { params }) }
export function cleanupAuditLog(beforeTime) { return request.delete('/audit-logs/cleanup', { params: { beforeTime } }) }
