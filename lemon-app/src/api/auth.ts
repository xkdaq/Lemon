import { http } from '@/utils/request'
import type { AppUser } from './types'

/** 发送登录验证码 */
export const sendLoginCode = (phone: string) => http.post<void>('/auth/sms-code', { phone })

/** 手机号 + 验证码登录，没注册过自动注册；passwordCleared = 旧密码因首次验证手机号而失效 */
export const smsLogin = (data: { phone: string; code: string }) =>
  http.post<{ token: string; passwordCleared: boolean }>('/auth/sms-login', data)

/** 手机号 + 密码登录（设置过密码的账号） */
export const login = (data: { phone: string; password: string }) => http.post<{ token: string }>('/auth/login', data)

export const logout = () => http.post<void>('/auth/logout')

export const getMe = () => http.get<AppUser>('/user/me')

/** 给当前账号的手机号发验证码（设置密码用） */
export const sendPasswordCode = () => http.post<void>('/user/password/code')

/** 设置 / 修改密码 */
export const setPassword = (data: { code: string; password: string }) => http.post<void>('/user/password', data)
