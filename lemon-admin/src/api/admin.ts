import { http, type PageResult } from '@/utils/request'
import type { AdminInfo } from './auth'

export interface Role {
  id: number
  code: string
  name: string
}

export type AdminItem = AdminInfo & { lastLoginAt: string | null; createdAt: string }

export const listRoles = () => http.get<Role[]>('/roles')
export const pageAdmins = (params: { pageNum: number; pageSize: number; keyword?: string }) =>
  http.get<PageResult<AdminItem>>('/admins', params)
export const createAdmin = (data: { username: string; password: string; nickname?: string; roles: string[] }) =>
  http.post<number>('/admins', data)
export const updateAdmin = (id: number, data: { nickname?: string; status: number; roles: string[] }) =>
  http.put<void>(`/admins/${id}`, data)
export const resetAdminPassword = (id: number, password: string) => http.put<void>(`/admins/${id}/password`, { password })
export const deleteAdmin = (id: number) => http.delete<void>(`/admins/${id}`)
