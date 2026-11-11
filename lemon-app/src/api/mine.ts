import { http } from '@/utils/request'
import type { Page } from './collection'

export interface Stats {
  answerTotal: number
  questionTotal: number
  accuracy: number
  streakDays: number
  todayCount: number
  wrongCount: number
  favoriteCount: number
}

export interface Feedback {
  id: number
  /** 1 题目纠错 2 意见建议 */
  type: number
  questionId?: number
  stemText?: string
  content: string
  /** 0 待处理 1 已处理 */
  status: number
  reply?: string
  createdAt: string
  updatedAt: string
}

export const getStats = () => http.get<Stats>('/user/stats')
export const submitFeedback = (data: { type: number; questionId?: number; content: string }) =>
  http.post<number>('/feedback', data)
export const myFeedback = (pageNum: number) => http.get<Page<Feedback>>('/feedback/mine', { pageNum, pageSize: 20 })

/** 有做题记录的题库 */
export interface BankRecord {
  bankId: number
  bankName: string
  moduleName: string | null
  /** 做过的题数 */
  doneCount: number
  /** 错题集里的题数 */
  wrongCount: number
}
export const getRecords = () => http.get<BankRecord[]>('/user/records')
/** includeWrong：连同该题库的错题一起清除 */
export const clearRecords = (bankId: number, includeWrong: boolean) =>
  http.post<void>('/user/records/clear', { bankId, includeWrong })
