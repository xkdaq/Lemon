import { http } from '@/utils/request'
import type { Session } from './practice'

export type Kind = 'favorite' | 'wrong'
export const kindLabel: Record<Kind, string> = { favorite: '收藏集', wrong: '错题集' }
const base = (kind: Kind) => (kind === 'favorite' ? '/favorites' : '/wrongs')

export interface Scope {
  moduleId?: number
  bankId?: number
  /** 科目（科目型题库） */
  courseId?: number
  chapterId?: number
}

/** 分组：模块 → 题库 →（科目）→ 章节；题库下只有一个科目时没有科目这一层 */
export interface Group {
  level: 'module' | 'bank' | 'course' | 'chapter'
  id: number
  name: string
  count: number
  children: Group[] | null
}

export interface CollectedQuestion {
  questionId: number
  type: number | null
  stemText: string
  moduleName: string
  bankName: string
  chapterName: string
  /** 仍上架可练习 */
  available: boolean
  wrongCount?: number
  rightStreak?: number
  lastWrongAnswer?: string
  time: string
}

export interface Page<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export const getGroups = (kind: Kind) => http.get<Group[]>(`${base(kind)}/groups`)
export const getList = (kind: Kind, scope: Scope, pageNum: number, pageSize = 20) =>
  http.get<Page<CollectedQuestion>>(base(kind), { ...scope, pageNum, pageSize })
export const startPractice = (kind: Kind, scope: Scope & { firstQuestionId?: number }) =>
  http.post<Session>(`${base(kind)}/practice`, scope)
export const removeWrong = (questionId: number) => http.delete<void>(`/wrongs/${questionId}`)

/** 错题导出 Word 的地址；answers=end 答案解析放在文末 */
export const wrongExportUrl = (bankId: number, answers: 'inline' | 'end') =>
  `${import.meta.env.VITE_API_BASE}/wrongs/export?bankId=${bankId}&answers=${answers}`
