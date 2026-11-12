import { http, type PageResult } from '@/utils/request'

export interface Dashboard {
  userTotal: number
  userToday: number
  activeToday: number
  answerToday: number
  questionTotal: number
  draftTotal: number
  riskPending: number
  modules: { moduleId: number; name: string; bankCount: number; questionCount: number }[]
}
export const getDashboard = () => http.get<Dashboard>('/dashboard')

// ---- 用户端用户 ----
export interface AppUser {
  id: number
  username: string | null
  phone: string | null
  nickname: string | null
  avatar: string | null
  registerFrom: number
  status: number
  lastLoginAt: string | null
  createdAt: string
}
export const pageAppUsers = (params: { pageNum: number; pageSize: number; keyword?: string; status?: number }) =>
  http.get<PageResult<AppUser>>('/app-users', params)
export const updateAppUserStatus = (id: number, status: number) => http.put<void>(`/app-users/${id}/status`, { status })
/** 重置密码：返回一次性临时密码 */
export const resetAppUserPassword = (id: number) => http.post<{ password: string }>(`/app-users/${id}/reset-password`)

// ---- 短信记录 ----
export interface SmsLog {
  id: number
  phone: string
  /** 1 验证码登录（旧） 2 设置密码（旧） 3 注册 4 找回密码 */
  scene: number
  /** 1 已提交 0 提交失败 2 未实际发送（开发环境） */
  status: number
  requestId: string | null
  error: string | null
  ip: string | null
  createdAt: string
}
export interface SmsDayCount {
  day: string
  total: number
  ok: number
  fail: number
}
export interface SmsStats {
  today: SmsDayCount
  last7: SmsDayCount
  last30: SmsDayCount
  /** 最近 30 天每天，按日期升序 */
  days: SmsDayCount[]
}
export const pageSmsLogs = (params: {
  pageNum: number
  pageSize: number
  phone?: string
  status?: number
  scene?: number
  startDate?: string
  endDate?: string
}) => http.get<PageResult<SmsLog>>('/sms-logs', params)
export const getSmsStats = () => http.get<SmsStats>('/sms-logs/stats')

// ---- 公告 ----
export interface Notice {
  id: number
  title: string
  content: string | null
  sort: number
  status: number
  createdAt: string
}
export type NoticeSave = Pick<Notice, 'title' | 'content' | 'sort' | 'status'>
export const pageNotices = (params: { pageNum: number; pageSize: number }) => http.get<PageResult<Notice>>('/notices', params)
export const createNotice = (data: NoticeSave) => http.post<number>('/notices', data)
export const updateNotice = (id: number, data: NoticeSave) => http.put<void>(`/notices/${id}`, data)
export const deleteNotice = (id: number) => http.delete<void>(`/notices/${id}`)

// ---- 系统参数 ----
export interface SysConfig {
  id: number
  configKey: string
  configValue: string
  remark: string | null
}
export const listConfigs = () => http.get<SysConfig[]>('/configs')
export const updateConfig = (key: string, value: string) => http.put<void>(`/configs/${key}`, { value })

// ---- 协议文档（用户协议、隐私政策） ----
export interface AppDocument {
  id: number
  docKey: string
  title: string
  content: string
  updatedAt: string
}
export const listDocuments = () => http.get<AppDocument[]>('/documents')
export const updateDocument = (key: string, title: string, content: string) =>
  http.put<void>(`/documents/${key}`, { title, content })

// ---- 反馈 ----
export interface Feedback {
  id: number
  /** 1 题目纠错 2 意见建议 */
  type: number
  questionId?: number
  stemText?: string
  content: string
  /** 0 待处理 1 已处理 */
  status: number
  reply?: string
  user?: string
  createdAt: string
  updatedAt: string
}
export const pageFeedback = (params: { pageNum: number; pageSize: number; type?: number; status?: number }) =>
  http.get<PageResult<Feedback>>('/feedback', params)
export const handleFeedback = (id: number, data: { reply?: string; status: number }) => http.put<void>(`/feedback/${id}`, data)

// ---- Banner ----
export interface Banner {
  id: number
  image: string
  link: string | null
  sort: number
  status: number
  createdAt: string
}
export type BannerSave = Pick<Banner, 'image' | 'link' | 'sort' | 'status'>
export const listBanners = () => http.get<Banner[]>('/banners')
export const createBanner = (data: BannerSave) => http.post<number>('/banners', data)
export const updateBanner = (id: number, data: BannerSave) => http.put<void>(`/banners/${id}`, data)
export const deleteBanner = (id: number) => http.delete<void>(`/banners/${id}`)

// ---- 风险告警（防偷题） ----
export interface RiskAlert {
  id: number
  userId: number
  username: string | null
  phone: string | null
  nickname: string | null
  /** 用户状态：1 正常 0 禁用 */
  userStatus: number | null
  alertType: 'hour_questions' | 'day_questions' | 'hour_sessions'
  alertDate: string
  metric: number
  threshold: number
  ip: string | null
  status: number
  handledBy: string | null
  handledAt: string | null
  remark: string | null
  createdAt: string
  updatedAt: string
}
export const pageRiskAlerts = (params: { pageNum: number; pageSize: number; status?: number; alertType?: string }) =>
  http.get<PageResult<RiskAlert>>('/risk-alerts', params)
export const handleRiskAlert = (id: number, remark: string) => http.put<void>(`/risk-alerts/${id}/handle`, { remark })

// ---- 飞书推送 ----
export const sendFeishuTest = () => http.post<void>('/notify/test')
export const sendDailyReport = (date?: string) => http.post<void>(`/notify/daily-report${date ? `?date=${date}` : ''}`)
