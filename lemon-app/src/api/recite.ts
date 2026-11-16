import { http } from '@/utils/request'

/** 背诵模块：免费开放，浏览不需要登录；“错误说法已掌握”标记需要登录 */

export interface ReciteSet {
  no: number
  name: string
  count: number
  /** 对照题库里的章节：有则可以“去刷这套” */
  chapterId: number | null
}

export interface ReciteBook {
  id: number
  name: string
  intro: string | null
  itemCount: number
  linkBankId: number | null
  sets: ReciteSet[]
}

export interface ReciteItem {
  id: number
  setNo: number
  no: number
  /** 1 单选 2 多选 */
  type: number
  subject: string | null
  topic: string | null
  qtype: string | null
  stem: string
  options: { key: string; content: string }[]
  answer: string | null
  core: string | null
  cards: { q: string; a: string }[]
  traps: { wrong: string; why: string }[]
  dists: { title: string; rows: string[][] }[]
  tip: string | null
  mnemonic: string | null
  questionId: number | null
  chapterId: number | null
}

export const SUBJECTS = ['马原', '毛概', '习思想', '史纲', '思法', '时政']

export const listReciteBooks = () => http.get<ReciteBook[]>('/recite/books')

/** 同一份资料的条目在本次打开期间只拉一次，四个页面共用 */
const itemCache = new Map<number, Promise<ReciteItem[]>>()
export function getReciteItems(bookId: number): Promise<ReciteItem[]> {
  let p = itemCache.get(bookId)
  if (!p) {
    p = http.get<ReciteItem[]>(`/recite/books/${bookId}/items`)
    p.catch(() => itemCache.delete(bookId))
    itemCache.set(bookId, p)
  }
  return p
}

let bookCache: Promise<ReciteBook[]> | null = null
export async function getReciteBook(bookId: number): Promise<ReciteBook | undefined> {
  if (!bookCache) {
    bookCache = listReciteBooks()
    bookCache.catch(() => (bookCache = null))
  }
  return (await bookCache).find((b) => b.id === bookId)
}
/** 背诵首页下拉刷新时清掉缓存 */
export function clearReciteCache() {
  bookCache = null
  itemCache.clear()
}

/** 已掌握的错误说法，key 为 “套-题号-第几条” */
export const listTrapMarks = (bookId: number) => http.get<string[]>('/recite-marks', { bookId })
export const setTrapMark = (bookId: number, setNo: number, no: number, idx: number, mastered: boolean) =>
  http.put<void>('/recite-marks', { bookId, setNo, no, idx, mastered })

export const trapKey = (item: ReciteItem, idx: number) => `${item.setNo}-${item.no}-${idx}`
export const typeLabel = (t: number) => (t === 2 ? '多选题' : '单选题')
export const setLabel = (book: ReciteBook | undefined, no: number) =>
  book?.sets.find((s) => s.no === no)?.name ?? `第${no}套`
