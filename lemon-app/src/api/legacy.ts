import { http } from '@/utils/request'

/** available 可以恢复 / mine 本账号已恢复过 / taken 已被其他账号恢复 */
export interface LegacyPreview {
  state: 'available' | 'mine' | 'taken'
  /** 老系统昵称（已打码） */
  nickname: string | null
  /** 老错题题目数 */
  wrongTotal: number
  /** 能恢复到现在题库的题目数 */
  restorable: number
}

/** 激活码 / 订单号放在请求体里 */
export const lookupLegacy = (code: string) => http.post<LegacyPreview>('/legacy/lookup', { code })
/** grantedModules：同时赠送开通的模块（政治、教育学），grantedUntil：到期时间 */
export const claimLegacy = (code: string) =>
  http.post<{ restored: number; wrongTotal: number; grantedModules: string[]; grantedUntil: string | null }>('/legacy/claim', { code })
