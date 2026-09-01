import request from '@/utils/request'

// 全部渠道配置（ADMIN）
export function listWebhookChannels() {
  return request.get('/webhook-channels')
}

// 创建渠道配置
export function createWebhookChannel(data) {
  return request.post('/webhook-channels', data)
}

// 更新渠道配置
export function updateWebhookChannel(id, data) {
  return request.put(`/webhook-channels/${id}`, data)
}

// 删除渠道配置
export function deleteWebhookChannel(id) {
  return request.delete(`/webhook-channels/${id}`)
}

// 测试发送（样例报文验证连通性）
export function testWebhookChannel(id) {
  return request.post(`/webhook-channels/${id}/test-send`)
}

// 企微智能机器人已捕获会话（群聊 chatid 从回调获取：@机器人一句即登记）
export function listAibotChats(botId) {
  return request.get('/webhook-channels/wecom-aibot/chats', { params: { botId } })
}
