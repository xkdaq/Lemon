/** 题库类型：1 科目型（题库→科目→章节） 2 试卷型（题库→章节/试卷） */
export const BankType = { COURSE: 1, PAPER: 2 } as const
export type BankTypeValue = (typeof BankType)[keyof typeof BankType]

export const bankTypeLabel: Record<number, string> = {
  [BankType.COURSE]: '科目型',
  [BankType.PAPER]: '试卷型',
}

interface Base {
  id: number
  createdAt: string
  updatedAt: string
}

export interface ExamModule extends Base {
  name: string
  icon?: string
  sort: number
  status: number
  /** 1 免费（不需要激活） 0 收费（激活码激活） */
  free: number
}

export interface BankCategory extends Base {
  moduleId: number
  name: string
  sort: number
  status: number
  /** 管理端列表返回：分类下题库数 */
  bankCount?: number
}

export interface QuestionBank extends Base {
  moduleId: number
  /** 所属分类，null 为未分类 */
  categoryId: number | null
  name: string
  type: BankTypeValue
  cover?: string
  intro?: string
  sort: number
  status: number
  questionCount: number
}

export interface Course extends Base {
  bankId: number
  name: string
  sort: number
  status: number
}

export interface Chapter extends Base {
  bankId: number
  courseId: number | null
  name: string
  sort: number
  status: number
  timeLimit?: number | null
  totalScore?: number | null
  questionCount: number
}

/** 题型 */
export const QuestionType = { SINGLE: 1, MULTIPLE: 2, JUDGE: 3, SUBJECTIVE: 4 } as const

export const questionTypeLabel: Record<number, string> = {
  [QuestionType.SINGLE]: '单选题',
  [QuestionType.MULTIPLE]: '多选题',
  [QuestionType.JUDGE]: '判断题',
  [QuestionType.SUBJECTIVE]: '主观题',
}

export interface QuestionOption {
  key: string
  content: string
}

export interface Question {
  id: number
  moduleId: number
  bankId: number
  bankName: string | null
  courseId: number | null
  courseName: string | null
  chapterId: number
  chapterName: string | null
  type: number
  stem: string
  options: QuestionOption[]
  answer: string | null
  analysis: string | null
  score: number
  difficulty: number
  source: string | null
  /** 小节（如 论述题、材料分析题），为空按题型分组 */
  section: string | null
  sort: number
  status: number
  createdAt: string
  updatedAt: string
}
