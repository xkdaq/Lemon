import { http, type PageResult } from '@/utils/request'

export interface PunchCamp {
  id: number
  name: string
  cover: string | null
  intro: string | null
  paperRuleId: number
  paperRuleName: string | null
  /** yyyy-MM-dd */
  startDate: string
  endDate: string
  /** 休息日，周一到周日为 1-7 */
  restWeekdays: number[]
  makeupLimit: number
  sort: number
  status: number
  memberCount: number
  /** 今天已打卡人数 */
  todayCount: number
  createdAt: string
}

export type CampSave = Pick<PunchCamp, 'name' | 'cover' | 'intro' | 'paperRuleId' | 'startDate' | 'endDate'
  | 'restWeekdays' | 'makeupLimit' | 'sort' | 'status'>

export interface ScheduleDay {
  day: string
  /** 周一到周日为 1-7 */
  weekday: number
  rest: boolean
  paperRuleId: number
  paperRuleName: string | null
  /** 当天单独设置了规则 */
  overridden: boolean
  doneCount: number
}

export interface PunchMember {
  userId: number
  nickname: string | null
  phone: string | null
  punchDays: number
  lastDay: string | null
  makeupUsed: number
  joinedAt: string
}

export const listCamps = () => http.get<PunchCamp[]>('/punch-camps')
export const createCamp = (data: CampSave) => http.post<number>('/punch-camps', data)
export const updateCamp = (id: number, data: CampSave) => http.put<void>(`/punch-camps/${id}`, data)
export const updateCampStatus = (id: number, status: number) => http.put<void>(`/punch-camps/${id}/status`, { status })
export const deleteCamp = (id: number) => http.delete<void>(`/punch-camps/${id}`)
/** month：2026-11 */
export const getSchedule = (id: number, month: string) => http.get<ScheduleDay[]>(`/punch-camps/${id}/schedule`, { month })
/** paperRuleId 为 null 表示恢复默认规则 */
export const setSchedule = (id: number, from: string, to: string, paperRuleId: number | null) =>
  http.put<void>(`/punch-camps/${id}/schedule`, { from, to, paperRuleId })
export const pageMembers = (id: number, params: { pageNum: number; pageSize: number; keyword?: string }) =>
  http.get<PageResult<PunchMember>>(`/punch-camps/${id}/members`, params)
