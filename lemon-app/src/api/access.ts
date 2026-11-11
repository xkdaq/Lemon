import { http } from '@/utils/request'

export interface ModuleAccess {
  moduleId: number
  name: string
  /** 到期时间；没激活过为 null */
  expireAt: string | null
  active: boolean
}

export interface AccessInfo {
  /** 未激活模块每章可免费试做的题数 */
  trialQuestions: number
  modules: ModuleAccess[]
}

export const getAccess = () => http.get<AccessInfo>('/access')

/** 激活：extended = 原来就激活着，这次是顺延 */
export const activate = (code: string) =>
  http.post<{ moduleId: number; moduleName: string; expireAt: string; extended: boolean }>('/activate', { code })
