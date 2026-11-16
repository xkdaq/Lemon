import { http, upload } from '@/utils/request'

export interface ReciteSet {
  no: number
  name: string
  count: number
  /** 对照题库里的章节，用户端“去刷这套” */
  chapterId: number | null
}

export interface ReciteBook {
  id: number
  name: string
  intro: string | null
  sort: number
  status: number
  itemCount: number
  linkBankId: number | null
  linkBankName: string | null
  linkedCount: number
  sets: ReciteSet[]
  updatedAt: string | null
}

export interface ReciteBookUpdate {
  name?: string
  intro?: string
  sort?: number
  status?: number
}

export interface ReciteImportResult {
  bookId: number
  itemCount: number
  linkedCount: number
  /** 没对照上题库的题，如“第3套第1题” */
  unlinked: string[]
}

export const listReciteBooks = () => http.get<ReciteBook[]>('/recite-books')
export const importReciteBook = (file: File, bookId?: number, linkBankId?: number) =>
  upload<ReciteImportResult>('/recite-books/import', file, { bookId, linkBankId })
export const updateReciteBook = (id: number, data: ReciteBookUpdate) => http.put<void>(`/recite-books/${id}`, data)
export const deleteReciteBook = (id: number) => http.delete<void>(`/recite-books/${id}`)
