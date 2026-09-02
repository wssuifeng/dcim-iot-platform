import request from '@/utils/request'

// 按机房查询自定义报文映射模板（ADMIN）
export function listPayloadMappings(roomId) {
  return request.get('/payload-mappings', { params: { roomId } })
}

// 新建映射模板
export function createPayloadMapping(data) {
  return request.post('/payload-mappings', data)
}

// 更新映射模板
export function updatePayloadMapping(id, data) {
  return request.put(`/payload-mappings/${id}`, data)
}

// 删除映射模板
export function deletePayloadMapping(id) {
  return request.delete(`/payload-mappings/${id}`)
}

// dry-run 转译预览（模板草稿 + 原始报文）
export function dryRunPayloadMapping(data) {
  return request.post('/payload-mappings/dry-run', data)
}

// 机房最新原始报文留存（映射配置参照物）
export function getLatestRawMessage(roomId) {
  return request.get('/payload-mappings/raw-message', { params: { roomId } })
}
