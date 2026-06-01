/*
 * 文件功能:登录接口封装
 */
import request from '@/utils/request'

/*
 * 方法功能:用户登录，返回 JWT Token
 * 参数:data { username: string, password: string }
 * 返回数据:Promise-Result<LoginResponseDTO> { token: string }
 *
 * 【认证说明】此接口为 permitAll()，无需携带 Token
 */
export function login(data) {
  return request.post('/auth/login', data)
}
