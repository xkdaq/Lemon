import { http } from '@/utils/request'

/** 1 练习 2 背题 3 考试 4 错题重练 5 收藏练习 */
export const Mode = { PRACTICE: 1, RECITE: 2, EXAM: 3, WRONG: 4, FAVORITE: 5 } as const
export const modeLabel: Record<number, string> = { 1: '练习模式', 2: '背题模式', 3: '考试模式', 4: '错题重练', 5: '收藏练习' }
/** 错题重练、收藏练习和练习模式一样：即时判题 */
export const isPracticeLike = (mode: number) => mode === Mode.PRACTICE || mode === Mode.WRONG || mode === Mode.FAVORITE
/** 页面参数里的模式名 → 编码 */
export const modeByName: Record<string, number> = { practice: 1, recite: 2, exam: 3 }

export const QType = { SINGLE: 1, MULTIPLE: 2, JUDGE: 3, SUBJECTIVE: 4 } as const
export const qTypeLabel: Record<number, string> = { 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }

export interface Ans {
  /** 用户答案 */
  a: string
  /** 1 对 0 错 null 未判 */
  c: number | null
}

export interface Session {
  id: number
  /** 错题重练 / 收藏练习没有章节 */
  chapterId?: number
  chapterName?: string
  /** 按题型练习时的题型，整章为空 */
  questionType?: number | null
  bankId?: number
  bankName?: string
  mode: number
  /** 0 进行中 1 已完成 2 已放弃 */
  status: number
  questionIds: number[]
  currentIndex: number
  answeredCount: number
  correctCount: number
  timeLimit?: number
  remainingSeconds?: number
  score?: number
  totalScore?: number
  startedAt: string
  answers: Record<string, Ans>
}

export interface QuestionItem {
  id: number
  type: number
  stem: string
  options: { key: string; content: string }[]
  score: number
  answer?: string
  analysis?: string
  favorited: boolean
  deleted?: boolean
}

export interface AnswerResult {
  correct?: boolean
  answer?: string
  analysis?: string
  answeredCount: number
  correctCount: number
}

export interface Result {
  sessionId: number
  chapterId?: number
  chapterName?: string
  mode: number
  total: number
  answered: number
  correct: number
  subjectiveCount: number
  score: number | null
  totalScore: number | null
  costSeconds: number
  wrongQuestionIds: number[]
}

/** questionType：只练该题型（章节下的“节”），不传练整章 */
export const startSession = (chapterId: number, mode: number, restart = false, questionType?: number) =>
  http.post<Session>('/sessions', { chapterId, mode, restart, questionType })
export const getSession = (id: number) => http.get<Session>(`/sessions/${id}`)
export const getQuestions = (id: number, ids: number[]) =>
  http.get<QuestionItem[]>(`/sessions/${id}/questions`, { ids: ids.join(',') })
export const submitAnswer = (id: number, questionId: number, answer: string, costMs?: number) =>
  http.post<AnswerResult>(`/sessions/${id}/answers`, { questionId, answer, costMs })
export const saveProgress = (id: number, currentIndex: number) =>
  http.put<void>(`/sessions/${id}/progress`, { currentIndex })
export const submitSession = (id: number) => http.post<Result>(`/sessions/${id}/submit`)
export const getResult = (id: number) => http.get<Result>(`/sessions/${id}/result`)

export const favorite = (questionId: number) => http.post<void>(`/favorites/${questionId}`)
export const unfavorite = (questionId: number) => http.delete<void>(`/favorites/${questionId}`)
