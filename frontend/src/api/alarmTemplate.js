import request from '@/utils/request'

// 分页查询告警模版（含引用计数）
export function pageTemplates(params) {
  return request.get('/alarm-templates/page', { params })
}

// 全部启用模版（绑定下拉数据源）
export function listEnabledTemplates() {
  return request.get('/alarm-templates')
}

// 模版详情
export function getTemplate(id) {
  return request.get(`/alarm-templates/${id}`)
}

// 模版规则行列表
export function listTemplateItems(id) {
  return request.get(`/alarm-templates/${id}/items`)
}

// 创建模版
export function createTemplate(data) {
  return request.post('/alarm-templates', data)
}

// 更新模版
export function updateTemplate(id, data) {
  return request.put(`/alarm-templates/${id}`, data)
}

// 删除模版
export function deleteTemplate(id) {
  return request.delete(`/alarm-templates/${id}`)
}

// 复制模版
export function duplicateTemplate(id, newCode, newName) {
  return request.post(`/alarm-templates/${id}/duplicate`, null, { params: { newCode, newName } })
}

// 新增规则行
export function createTemplateItem(templateId, data) {
  return request.post(`/alarm-templates/${templateId}/items`, data)
}

// 更新规则行
export function updateTemplateItem(itemId, data) {
  return request.put(`/alarm-templates/items/${itemId}`, data)
}

// 删除规则行
export function deleteTemplateItem(itemId) {
  return request.delete(`/alarm-templates/items/${itemId}`)
}

// 设备生效模版解析
export function getEffectiveTemplate(deviceId) {
  return request.get(`/alarm-templates/devices/${deviceId}/effective`)
}

// 设备改绑模版（templateId 空=跟随类型）
export function bindDeviceTemplate(deviceId, templateId) {
  return request.put(`/alarm-templates/devices/${deviceId}/binding`, null, { params: { templateId } })
}

// 类型绑定基础模版（templateId 空=解绑）
export function bindTypeTemplate(typeId, templateId) {
  return request.put(`/alarm-templates/types/${typeId}/binding`, null, { params: { templateId } })
}
