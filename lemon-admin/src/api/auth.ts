import { http } from '@/utils/request'

export interface AdminInfo {
  id: number
  username: string
  nickname: string
  status: number
  /** super_admin | editor */
  roles: string[]
}

export const login = (data: { username: string; password: string }) =>
  http.post<{ token: string }>('/auth/login', data)

export const getInfo = () => http.get<AdminInfo>('/auth/info')

export const logout = () => http.post<void>('/auth/logout')

export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  http.put<void>('/auth/password', data)
