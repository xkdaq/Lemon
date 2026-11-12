import { download, http, type PageResult } from '@/utils/request'

export interface UserBrief {
  id: number
  username: string | null
  phone: string | null
  nickname: string | null
}

export interface ActivationCode {
  id: number
  /** XXXX-XXXX-XXXX */
  code: string
  moduleId: number
  moduleName: string | null
  batchName: string | null
  /** 1 激活后有效 N 天 2 到固定日期为止 */
  validityType: number
  validDays: number | null
  expireAt: string | null
  /** 0 未使用 1 已使用 2 已作废 */
  status: number
  user: UserBrief | null
  usedAt: string | null
  /** 使用后该模块的到期时间 */
  accessUntil: string | null
  createdAt: string
}

export interface CodeQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  moduleId?: number
  status?: number
  batchName?: string
}

export const pageCodes = (params: CodeQuery) => http.get<PageResult<ActivationCode>>('/activation-codes', params)
export const listBatches = () => http.get<string[]>('/activation-codes/batches')
export const exportCodes = (params: Omit<CodeQuery, 'pageNum' | 'pageSize'>) =>
  download('/activation-codes/export', params, '激活码.csv')
export const generateCodes = (data: {
  moduleId: number
  count: number
  validityType: number
  validDays?: number
  expireDate?: string
  batchName?: string
}) => http.post<{ codes: string[] }>('/activation-codes', data)
export const voidCode = (id: number) => http.post<void>(`/activation-codes/${id}/void`)

// ---- 用户的模块权限 ----
export interface ModuleAccess {
  moduleId: number
  moduleName: string
  expireAt: string | null
  active: boolean
  /** 免费模块，不需要激活 */
  free: boolean
}
export interface AccessLog {
  id: number
  moduleName: string | null
  /** 1 激活码 2 后台开通 / 延长 3 后台取消 4 恢复老系统错题赠送 5 新用户注册赠送 */
  source: number
  code: string | null
  adminName: string | null
  beforeExpire: string | null
  afterExpire: string | null
  remark: string | null
  createdAt: string
}
export interface UserAccess {
  user: UserBrief
  modules: ModuleAccess[]
  logs: AccessLog[]
}
export const getUserAccess = (userId: number) => http.get<UserAccess>(`/app-users/${userId}/access`)
export const grantAccess = (userId: number, data: { moduleId: number; days?: number; until?: string; remark?: string }) =>
  http.post<{ expireAt: string }>(`/app-users/${userId}/access/grant`, data)
export const revokeAccess = (userId: number, data: { moduleId: number; remark?: string }) =>
  http.post<void>(`/app-users/${userId}/access/revoke`, data)

// ---- 模块用户 ----
export interface ModuleUser {
  /** user_module_access.id */
  id: number
  userId: number
  username: string | null
  phone: string | null
  nickname: string | null
  moduleId: number
  moduleName: string | null
  /** 最近一次开通方式：1 激活码 2 后台开通 / 延长 4 恢复老系统错题赠送 5 新用户注册赠送 */
  source: number | null
  code: string | null
  adminName: string | null
  /** 首次开通时间 */
  firstAt: string
  /** 最近一次激活时间 */
  lastAt: string | null
  grantCount: number
  expireAt: string
  active: boolean
  /** 剩余天数；已过期为 null */
  remainDays: number | null
}
export interface ModuleUserQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  moduleId?: number
  /** 1 有效 2 7 天内到期 3 已过期 */
  status?: number
  source?: number
  activatedFrom?: string
  activatedTo?: string
  orderBy?: 'lastAt' | 'expireAt' | 'createdAt'
  order?: 'asc' | 'desc'
}
export interface ModuleStat {
  moduleId: number
  moduleName: string
  free: boolean
  active: number
  expiring: number
  expired: number
}
export const pageModuleUsers = (params: ModuleUserQuery) => http.get<PageResult<ModuleUser>>('/module-users', params)
export const moduleUserStats = () => http.get<ModuleStat[]>('/module-users/stats')
export const exportModuleUsers = (params: Omit<ModuleUserQuery, 'pageNum' | 'pageSize'>) =>
  download('/module-users/export', params, '模块用户.csv')
