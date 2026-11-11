import { download, http, upload, type PageResult } from '@/utils/request'
import type { Question, QuestionOption } from '@/types/content'

export interface QuestionQuery {
  pageNum: number
  pageSize: number
  moduleId?: number
  bankId?: number
  courseId?: number
  chapterId?: number
  type?: number
  status?: number
  keyword?: string
}

export interface QuestionSave {
  chapterId: number
  type: number
  stem: string
  /** key 可留空，后端按顺序补 A/B/C… */
  options?: Partial<QuestionOption>[]
  /** 客观题答案，如 A / ABD；判断题可填 对/错 */
  answer?: string
  /** 解析；主观题即参考答案，不填为背诵题（题干即背诵内容） */
  analysis?: string
  score?: number
  difficulty?: number
  source?: string
  /** 小节，如 论述题、材料分析题；不填按题型分组 */
  section?: string
  sort?: number
  status?: number
}

export const pageQuestions = (params: QuestionQuery) => http.get<PageResult<Question>>('/questions', params)
export const getQuestion = (id: number) => http.get<Question>(`/questions/${id}`)
export const createQuestion = (data: QuestionSave) => http.post<number>('/questions', data)
export const updateQuestion = (id: number, data: QuestionSave) => http.put<void>(`/questions/${id}`, data)
export const deleteQuestion = (id: number) => http.delete<void>(`/questions/${id}`)
export const batchStatus = (ids: number[], status: number) => http.put<void>('/questions/batch-status', { ids, status })
export const batchMove = (ids: number[], targetChapterId: number) =>
  http.post<void>('/questions/batch-move', { ids, targetChapterId })
export const batchDelete = (ids: number[]) => http.post<void>('/questions/batch-delete', { ids })

// ---- Excel ----
export interface ImportResult {
  total: number
  imported: number
  createdCourses: number
  createdChapters: number
  errors: { row: number; message: string }[]
}
export const downloadTemplate = () => download('/questions/import-template', undefined, '题目导入模板.xlsx')
export const exportQuestions = (params: Omit<QuestionQuery, 'pageNum' | 'pageSize'>) =>
  download('/questions/export', params, '题目导出.xlsx')
export const importQuestions = (file: File, bankId: number, chapterId?: number) =>
  upload<ImportResult>('/questions/import', file, { bankId, chapterId })
