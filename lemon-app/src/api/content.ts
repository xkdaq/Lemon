import { http } from '@/utils/request'
import type { BankCategory, Banner, Chapter, Course, ExamModule, LastSession, Notice, QuestionBank } from './types'

export const getHome = () => http.get<{ modules: ExamModule[]; notices: Notice[]; banners: Banner[]; lastSession: LastSession | null }>('/home')

export const getBanks = (moduleId: number) => http.get<QuestionBank[]>(`/modules/${moduleId}/banks`)
export const getCategories = (moduleId: number) => http.get<BankCategory[]>(`/modules/${moduleId}/categories`)

export const getBankDetail = (bankId: number) =>
  http.get<{ bank: QuestionBank; courses: Course[]; chapters: Chapter[] }>(`/banks/${bankId}`)

export const getChapters = (bankId: number, courseId?: number) =>
  http.get<Chapter[]>(`/banks/${bankId}/chapters`, courseId ? { courseId } : undefined)
