import request from '@/utils/request'

export function getMetricPage(params) { return request.get('/metric/page', { params }) }
export function createMetric(data) { return request.post('/metric', data) }
export function updateMetric(data) { return request.put('/metric', data) }
export function deleteMetric(id) { return request.delete(`/metric/${id}`) }
export function refreshMetricCache() { return request.post('/metric/cache/refresh') }
