import { http } from '@/utils/request'
import type { AppUser } from './types'

/** 发短信验证码：register 注册 / reset 找回密码 */
export const sendCode = (phone: string, purpose: 'register' | 'reset') => http.post<void>('/auth/sms-code', { phone, purpose })

/** 注册：手机号 + 验证码 + 密码，注册后自动登录 */
export const register = (data: { phone: string; code: string; password: string; nickname?: string }) =>
  http.post<{ token: string }>('/auth/register', data)

/** 找回密码：手机号 + 验证码 + 新密码，重置后自动登录 */
export const resetPassword = (data: { phone: string; code: string; password: string }) =>
  http.post<{ token: string }>('/auth/reset-password', data)

/** 账号密码登录：账号 = 手机号或用户名 */
export const login = (data: { account: string; password: string }) => http.post<{ token: string }>('/auth/login', data)

export const logout = () => http.post<void>('/auth/logout')

export const getMe = () => http.get<AppUser>('/user/me')

/** 设置 / 修改密码：已有密码要传原密码 */
export const changePassword = (data: { oldPassword?: string; password: string }) => http.post<void>('/user/password', data)

export const updateNickname = (nickname: string) => http.put<void>('/user/nickname', { nickname })
