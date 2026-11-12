import { http } from '@/utils/request'

/** 协议文档：agreement 用户协议 / privacy 隐私政策 */
export type DocKey = 'agreement' | 'privacy'
export const getDocument = (key: DocKey) => http.get<{ title: string; content: string; updatedAt: string }>(`/documents/${key}`)

/** 联系客服 */
export const getServiceInfo = () => http.get<{ wechat: string; note: string }>('/service-info')

export const openDocument = (key: DocKey) => uni.navigateTo({ url: `/pages/doc/index?key=${key}` })
