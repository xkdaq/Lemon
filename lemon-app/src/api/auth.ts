import { http } from '@/utils/request'
import type { AppUser } from './types'

export const login = (data: { phone: string; password: string }) => http.post<{ token: string }>('/auth/login', data)

export const register = (data: { phone: string; password: string; nickname?: string }) =>
  http.post<{ token: string }>('/auth/register', data)

export const logout = () => http.post<void>('/auth/logout')

export const getMe = () => http.get<AppUser>('/user/me')
