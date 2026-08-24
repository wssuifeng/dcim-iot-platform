import request, { requestBlob } from '@/utils/request'

export function getDevicePage(params) {
  return request.get('/devices/page', { params })
}

export function getDeviceDetail(id) {
  return request.get(`/devices/${id}`)
}

export function createDevice(data) {
  return request.post('/devices', data)
}

export function updateDevice(id, data) {
  return request.put(`/devices/${id}`, data)
}

export function deleteDevice(id) {
  return request.delete(`/devices/${id}`)
}

export function updateDeviceStatus(id, status) {
  return request.put(`/devices/${id}/status`, null, { params: { status } })
}


export function getAllDevices() {
  return request.get('/devices')
}

/**
 * 批量导入设备
 * @param {File} file Excel 文件
 */
export function importDevices(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/devices/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/**
 * 下载设备导入模板
 */
export function downloadDeviceImportTemplate() {
  return requestBlob({ method: 'get', url: '/devices/import-template' })
}

/**
 * 导出设备列表
 * @param {object} params 筛选参数 { status, name }
 */
export function exportDevices(params) {
  return requestBlob({ method: 'get', url: '/devices/export', params })
}
