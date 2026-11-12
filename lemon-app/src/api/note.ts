import { http, ApiError } from '@/utils/request'
import { getToken } from '@/utils/auth'

/** 题目笔记（私密）：每人每题一条，文字 + 最多 6 张图片 */
export const MAX_NOTE_CONTENT = 2000
export const MAX_NOTE_IMAGES = 6

export interface Note {
  content: string
  images: string[]
  updatedAt: string
}

export interface NoteItem {
  questionId: number
  stemText: string
  bankName?: string
  chapterName?: string
  content: string
  images: string[]
  available: boolean
  updatedAt: string
}

export interface NoteDetail {
  questionId: number
  type: number
  stem: string
  options: { key: string; content: string }[]
  answer?: string
  analysis?: string
  bankName?: string
  chapterName?: string
  note: Note | null
}

export const listNotes = (pageNum: number, pageSize = 20) =>
  http.get<{ list: NoteItem[]; total: number }>('/notes', { pageNum, pageSize })
export const getNote = (questionId: number) => http.get<NoteDetail>(`/notes/${questionId}`)
export const saveNote = (questionId: number, content: string, images: string[]) =>
  http.put<Note | null>(`/notes/${questionId}`, { content, images })
export const deleteNote = (questionId: number) => http.delete<void>(`/notes/${questionId}`)

/** 笔记保存 / 删除后通知做题页、详情页刷新 */
export const NOTE_SAVED = 'note-saved'
export interface NoteSavedEvent {
  questionId: number
  note: Note | null
}

/** 上传一张笔记图片，返回图片地址 */
export function uploadNoteImage(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${import.meta.env.VITE_API_BASE}/notes/images`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => {
        try {
          const body = JSON.parse(res.data as string)
          if (body.code === 0) return resolve(body.data.url as string)
          uni.showToast({ title: body.msg || '上传失败', icon: 'none' })
          reject(new ApiError(body.code, body.msg))
        } catch {
          uni.showToast({ title: '上传失败', icon: 'none' })
          reject(new ApiError(-1, '上传失败'))
        }
      },
      fail: (e) => {
        uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
        reject(new ApiError(-1, e.errMsg))
      },
    })
  })
}
