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

// 趋势分桶聚合（桶内均值/最小/最大，桶数≤200）——趋势图专用，避免分页截断
export function getMetricSampleTrend(params) {
  return request.get('/metric-samples/trend', { params })
}
