import { http } from '@/utils/request'
import type { Session } from './practice'

export interface CampCard {
  id: number
  name: string
  cover: string | null
  startDate: string
  endDate: string
  /** 每天题数 */
  questionCount: number
  memberCount: number
  joined: boolean
  todayDone: boolean
}

/**
 * out 不在活动期 / rest 休息日 / future 未到 / todo 今天还没打 / doing 做题中 / done 已打卡 / missed 漏打
 */
export type DayState = 'out' | 'rest' | 'future' | 'todo' | 'doing' | 'done' | 'missed'

export interface CalendarDay {
  /** yyyy-MM-dd */
  day: string
  state: DayState
  sessionId?: number
  makeup?: boolean
}

export interface CampDetail {
  id: number
  name: string
  cover: string | null
  intro: string | null
  startDate: string
  endDate: string
  /** 休息日，周一到周日为 1-7 */
  restWeekdays: number[]
  makeupLimit: number
  questionCount: number
  memberCount: number
  todayCount: number
  joined: boolean
  /** 今天的状态，不在活动期内没有 */
  today?: CalendarDay
  /** 以下仅已加入时有 */
  punchDays?: number
  streakDays?: number
  makeupLeft?: number
  /** yyyy-MM */
  month: string
  calendar: CalendarDay[]
}

export interface RankItem {
  rank: number
  userId: number
  nickname: string
  avatar: string | null
  days: number
}

export const listCamps = () => http.get<CampCard[]>('/punch-camps')
export const getCamp = (id: number, month?: string) => http.get<CampDetail>(`/punch-camps/${id}`, month ? { month } : undefined)
export const getRank = (id: number) => http.get<{ list: RankItem[]; me: RankItem | null }>(`/punch-camps/${id}/rank`)
export const joinCamp = (id: number) => http.post<void>(`/punch/${id}/join`)
/** day 不传为今天，早于今天为补卡 */
export const startPunch = (id: number, day?: string) => http.post<Session>(`/punch/${id}/start`, { day })
