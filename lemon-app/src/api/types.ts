/** 题库类型：1 科目型（题库→科目→章节） 2 试卷型（题库→试卷） */
export const BankType = { COURSE: 1, PAPER: 2 } as const

export interface ExamModule {
  id: number
  name: string
  icon?: string
}

export interface Notice {
  id: number
  title: string
  content?: string
}

export interface BankCategory {
  id: number
  moduleId: number
  name: string
}

export interface QuestionBank {
  id: number
  moduleId: number
  /** 所属分类，null 为未分类（归入“其他”） */
  categoryId: number | null
  name: string
  type: number
  cover?: string
  intro?: string
  questionCount: number
}

export interface Course {
  id: number
  bankId: number
  name: string
}

export interface Chapter {
  id: number
  bankId: number
  courseId: number | null
  name: string
  questionCount: number
  timeLimit?: number | null
  totalScore?: number | null
  /** 当前用户：已做题数、最近一次作答正确的题数（未登录为 0） */
  doneCount: number
  correctCount: number
  /** 未完成的会话模式：1 练习 2 背题 3 考试 */
  inProgressModes: number[]
  /** 试卷最近一次考试得分 */
  lastScore?: number | null
  /** 章节下的“节”：按题型分组（单选题 / 多选题 / 分析题） */
  sections: ChapterSection[]
}

export interface ChapterSection {
  /** 题型：1 单选 2 多选 3 判断 4 主观（分析题） */
  type: number
  name: string
  questionCount: number
  doneCount: number
  correctCount: number
  /** 有未完成的该题型练习 */
  inProgress: boolean
}

export interface LastSession {
  sessionId: number
  chapterId: number
  chapterName: string
  bankId: number
  bankName: string
  mode: number
  currentIndex: number
  total: number
  answeredCount: number
}

export interface AppUser {
  id: number
  phone: string
  nickname: string
  avatar?: string
}

export interface Banner {
  id: number
  image: string
  /** 空、站内页面（/pages/...）或 https 外链 */
  link?: string | null
}
