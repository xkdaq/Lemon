import { http } from '@/utils/request'

/** 一种题型抽多少道。type：1 单选 2 多选 3 判断 4 主观 */
export interface RuleItem {
  type: number
  count: number
}

export interface PaperRule {
  id: number
  bankId: number
  bankName: string | null
  name: string
  /** 抽题范围，null 为整个题库 */
  chapterIds: number[] | null
  items: RuleItem[]
  /** 难度比例 [易, 中, 难]，null 不限 */
  difficultyRatio: number[] | null
  /** 每次抽题总数 */
  questionCount: number
  status: number
  updatedAt: string
}

export interface PaperRuleSave {
  bankId: number
  name: string
  chapterIds: number[] | null
  items: RuleItem[]
  difficultyRatio: number[] | null
  status: number
}

export interface PoolStat {
  type: number
  label: string
  total: number
  /** [易, 中, 难] */
  byDifficulty: number[]
}

export interface PreviewQuestion {
  id: number
  type: number
  difficulty: number
  chapterName: string | null
  stemText: string
}

export const listPaperRules = (bankId?: number) => http.get<PaperRule[]>('/paper-rules', { bankId })
export const createPaperRule = (data: PaperRuleSave) => http.post<number>('/paper-rules', data)
export const updatePaperRule = (id: number, data: PaperRuleSave) => http.put<void>(`/paper-rules/${id}`, data)
export const deletePaperRule = (id: number) => http.delete<void>(`/paper-rules/${id}`)
export const previewPaperRule = (id: number) => http.post<PreviewQuestion[]>(`/paper-rules/${id}/preview`)
/** chapterIds 为空表示整个题库 */
export const getPool = (bankId: number, chapterIds?: number[] | null) =>
  http.get<PoolStat[]>('/paper-rules/pool', { bankId, chapterIds: chapterIds?.length ? chapterIds.join(',') : undefined })
