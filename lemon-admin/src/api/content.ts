import { http, type PageResult } from '@/utils/request'
import type { BankCategory, Chapter, Course, ExamModule, QuestionBank } from '@/types/content'

// ---- 模块 ----
export type ModuleSave = Pick<ExamModule, 'name' | 'icon' | 'sort' | 'status' | 'free' | 'singleScore' | 'multiScore'>
export const listModules = () => http.get<ExamModule[]>('/modules')
export const createModule = (data: ModuleSave) => http.post<number>('/modules', data)
export const updateModule = (id: number, data: ModuleSave) => http.put<void>(`/modules/${id}`, data)
export const deleteModule = (id: number) => http.delete<void>(`/modules/${id}`)

// ---- 分类 ----
export type CategorySave = Pick<BankCategory, 'moduleId' | 'name' | 'status'> & { sort?: number }
export const listCategories = (moduleId?: number) => http.get<BankCategory[]>('/categories', { moduleId })
export const createCategory = (data: CategorySave) => http.post<number>('/categories', data)
export const updateCategory = (id: number, data: CategorySave) => http.put<void>(`/categories/${id}`, data)
export const deleteCategory = (id: number) => http.delete<void>(`/categories/${id}`)
/** 拖动排序：同一模块下全部分类 id 的新顺序 */
export const sortCategories = (ids: number[]) => http.put<void>('/categories/sort', { ids })

// ---- 题库 ----
export type BankSave = Pick<QuestionBank, 'moduleId' | 'categoryId' | 'name' | 'type' | 'cover' | 'intro' | 'sort' | 'status'>
export interface BankQuery {
  pageNum: number
  pageSize: number
  moduleId?: number
  /** 0 表示未分类 */
  categoryId?: number
  type?: number
  keyword?: string
}
export const pageBanks = (params: BankQuery) => http.get<PageResult<QuestionBank>>('/banks', params)
export const getBank = (id: number) => http.get<QuestionBank>(`/banks/${id}`)
export const createBank = (data: BankSave) => http.post<number>('/banks', data)
export const updateBank = (id: number, data: BankSave) => http.put<void>(`/banks/${id}`, data)
export const updateBankStatus = (id: number, status: number) => http.put<void>(`/banks/${id}/status`, { status })
export const deleteBank = (id: number) => http.delete<void>(`/banks/${id}`)
/** 拖动排序：同一模块下全部题库 id 的新顺序 */
export const sortBanks = (ids: number[]) => http.put<void>('/banks/sort', { ids })

// ---- 科目 ----
export type CourseSave = Pick<Course, 'bankId' | 'name' | 'sort' | 'status'>
export const listCourses = (bankId: number) => http.get<Course[]>('/courses', { bankId })
export const createCourse = (data: CourseSave) => http.post<number>('/courses', data)
export const updateCourse = (id: number, data: CourseSave) => http.put<void>(`/courses/${id}`, data)
export const deleteCourse = (id: number) => http.delete<void>(`/courses/${id}`)

// ---- 章节 ----
export type ChapterSave = Pick<Chapter, 'bankId' | 'courseId' | 'name' | 'sort' | 'status' | 'timeLimit' | 'totalScore'>
export const listChapters = (bankId: number, courseId?: number) => http.get<Chapter[]>('/chapters', { bankId, courseId })
export const createChapter = (data: ChapterSave) => http.post<number>('/chapters', data)
export const updateChapter = (id: number, data: ChapterSave) => http.put<void>(`/chapters/${id}`, data)
export const deleteChapter = (id: number) => http.delete<void>(`/chapters/${id}`)

// ---- 目录树（级联选择） ----
export interface TreeNode {
  kind: 'bank' | 'course' | 'chapter'
  id: number
  label: string
  status: number
  /** 仅 bank 节点：1 科目型 2 试卷型 */
  bankType: number | null
  children: TreeNode[] | null
}
export const getContentTree = () => http.get<TreeNode[]>('/content-tree')
