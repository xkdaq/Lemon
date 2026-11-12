import { http } from '@/utils/request'

/** 数据统计 */
export interface DayStat {
  date: string
  newUsers: number
  /** 当天用过用户端的登录用户 */
  activeUsers: number
  answerCount: number
  answerUsers: number
  /** 客观题正确率 %，没有判题作答为 null */
  correctRate: number | null
  activations: number
  newNotes: number
}

/** 注册日留存：注册后第 N 天仍活跃的人数，还没到那一天为 null */
export interface Retention {
  date: string
  newUsers: number
  d1: number | null
  d3: number | null
  d7: number | null
}

export interface RankItem {
  id: number
  name: string
  sub: string | null
  answers: number
  users: number
  wrongRate: number | null
}

export interface ContentStat {
  banks: RankItem[]
  chapters: RankItem[]
  hardQuestions: RankItem[]
}

export const getTrend = (days: number) => http.get<DayStat[]>('/stats/trend', { days })
export const getRetention = (days: number) => http.get<Retention[]>('/stats/retention', { days })
export const getContent = (days: number) => http.get<ContentStat>('/stats/content', { days })
