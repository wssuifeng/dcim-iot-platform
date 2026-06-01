/*
 * 文件功能:前端枚举常量映射表，将后端数字状态码映射为中文标签和颜色值
 *
 * 【设计原则】
 * - 所有枚举定义集中管理，页面中禁止硬编码中文标签
 * - 颜色值使用 Element Plus 品牌色系：
 *   #409EFF(蓝) #67C23A(绿) #E6A23C(橙) #F56C6C(红) #909399(灰)
 * - bgColor 为标签/Tag 的背景色（比 color 更浅的版本）
 */
export const DEVICE_STATUS = {
  0: { label: '离线', color: '#909399' },
  1: { label: '在线', color: '#67C23A' },
  2: { label: '故障', color: '#F56C6C' },
}

export const ALERT_LEVEL = {
  1: { label: '紧急', color: '#F56C6C', bgColor: '#FEF0F0' },
  2: { label: '严重', color: '#E6A23C', bgColor: '#FDF6EC' },
  3: { label: '一般', color: '#409EFF', bgColor: '#ECF5FF' },
  4: { label: '警告', color: '#909399', bgColor: '#F5F7FA' },
  5: { label: '提示', color: '#C0C4CC', bgColor: '#FFFFFF' },
}

export const USER_STATUS = {
  0: { label: '启用', color: '#67C23A' },
  1: { label: '停用', color: '#F56C6C' },
}

export const HANDLE_STATUS = {
  0: { label: '未处理', color: '#F56C6C' },
  1: { label: '已处理', color: '#67C23A' },
}

export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'
export const DATE_FORMAT_SHORT = 'YYYY-MM-DD'
