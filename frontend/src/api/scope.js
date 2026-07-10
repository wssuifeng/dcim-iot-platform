import request from '@/utils/request'

export const CURRENT_SCOPE_ENDPOINT = '/auth/current-scope'

/**
 * @typedef {Object} CurrentScopeDTO
 * @property {number|string} userId 当前用户 ID
 * @property {'PLATFORM_ADMIN'|'ORG_ADMIN'|'OPERATOR'|'VIEWER'} role 当前角色
 * @property {Array<number|string>} organizationIds 授权组织 ID
 * @property {Array<number|string>} cloudRoomIds 授权机房 ID
 * @property {'PLATFORM'|'ORG_ALL'|'ROOMS'|'READ_ONLY'} scopeType 范围类型
 */

/**
 * 获取后端解析后的当前用户数据范围。
 *
 * 返回值保留统一响应结构 Result<CurrentScopeDTO>，由 Store 负责校验和归一化 data。
 * 接口路径后续发生调整时只需修改 CURRENT_SCOPE_ENDPOINT。
 */
export function getCurrentScope(config = {}) {
  return request.get(CURRENT_SCOPE_ENDPOINT, config)
}
