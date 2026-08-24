/*
 * 文件功能:前端枚举常量映射表，将后端数字状态码映射为中文标签和颜色值
 *
 * 【设计原则】
 * - 所有枚举定义集中管理，页面中禁止硬编码中文标签
 * - 颜色值统一使用主题 CSS 变量（var(--color-*)），支持日间/夜间主题切换
 * - bgColor 为标签/Tag 的背景色（比 color 更浅的版本）
 */
export const DEVICE_STATUS = {
  0: { label: '离线', color: 'var(--color-info)' },
  1: { label: '在线', color: 'var(--color-success)' },
  2: { label: '故障', color: 'var(--color-danger)' },
}

export const ALERT_LEVEL = {
  1: { label: '紧急', color: 'var(--color-danger)', bgColor: 'var(--color-danger-light)' },
  2: { label: '严重', color: 'var(--color-warning)', bgColor: 'var(--color-warning-light)' },
  3: { label: '一般', color: 'var(--color-primary)', bgColor: 'var(--color-primary-light)' },
  4: { label: '警告', color: 'var(--color-info)', bgColor: 'var(--color-info-light)' },
  5: { label: '提示', color: 'var(--color-text-3)', bgColor: 'var(--color-surface-2)' },
}

export const USER_STATUS = {
  0: { label: '启用', color: 'var(--color-success)' },
  1: { label: '停用', color: 'var(--color-danger)' },
}

export const HANDLE_STATUS = {
  0: { label: '未处理', color: 'var(--color-danger)' },
  1: { label: '已处理', color: 'var(--color-success)' },
}

export const USER_ROLE = {
  PLATFORM_ADMIN: { label: '平台管理员', color: 'var(--color-danger)' },
  ORG_ADMIN: { label: '组织管理员', color: 'var(--color-warning)' },
  ADMIN: { label: '管理员', color: 'var(--color-warning)' },
  OPERATOR: { label: '运维人员', color: 'var(--color-success)' },
  VIEWER: { label: '查看者', color: 'var(--color-info)' },
}

export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss'
export const DATE_FORMAT_SHORT = 'YYYY-MM-DD'
export const ALARM_EVENT_TYPE = { ALARM_CREATED: 'ALARM_CREATED', ALARM_UPGRADED: 'ALARM_UPGRADED', ALARM_RECOVERED: 'ALARM_RECOVERED' }
export const ALARM_EVENT_TYPE_TEXT = { ALARM_CREATED: '新增告警', ALARM_UPGRADED: '告警升级', ALARM_RECOVERED: '告警恢复' }
