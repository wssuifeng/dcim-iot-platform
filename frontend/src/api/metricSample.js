import request from '@/utils/request'

export function createMetricSample(data) {
  return request.post('/metric-samples', data)
}

export function createMetricSampleBatch(items) {
  return request.post('/metric-samples/batch', { items })
}

export function getMetricSamplePage(params) {
  return request.get('/metric-samples/page', { params })
}
