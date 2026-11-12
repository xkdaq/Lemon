import { getToken, removeToken } from './auth'

/** 接口错误，code 为后端业务码（401 未登录等），网络错误为 -1 */
export class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message)
  }
}

/** 后端统一响应体 */
interface R<T> {
  code: number
  msg: string
  data: T
}

const BASE = import.meta.env.VITE_API_BASE

/** 后端业务码：模块未激活（data = { moduleId, moduleName }） */
export const NEED_ACTIVATION = 1010

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

/**
 * uni.request 封装：H5 / 小程序通用。
 * code=401 时清 token 并跳登录页；其它非 0 code 弹 toast 并 reject。
 */
export function request<T>(method: Method, url: string, data?: object): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.request({
      url: BASE + url,
      method,
      data: data as UniApp.RequestOptions['data'],
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode === 429) {
          uni.showToast({ title: '操作太频繁，请稍后再试', icon: 'none' })
          reject(new ApiError(429, '操作太频繁'))
          return
        }
        const body = res.data as R<T>
        if (body?.code === 0) {
          resolve(body.data)
          return
        }
        if (body?.code === 401) {
          removeToken()
          uni.navigateTo({ url: '/pages/login/index' })
        } else if (body?.code === NEED_ACTIVATION) {
          // 模块未激活：引导去激活（没登录先登录）
          const moduleId = (body.data as { moduleId?: number } | null)?.moduleId
          uni.showModal({
            title: '需要激活',
            content: `${body.msg}。可以在「我的 → 我的模块」里输入激活码。`,
            confirmText: '去激活',
            success: (r) => {
              if (!r.confirm) return
              uni.navigateTo({ url: getToken() ? `/pages/activate/index${moduleId ? `?moduleId=${moduleId}` : ''}` : '/pages/login/index' })
            },
          })
        } else {
          uni.showToast({ title: body?.msg || '请求失败', icon: 'none' })
        }
        reject(new ApiError(body?.code ?? -1, body?.msg ?? '请求失败'))
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
        reject(new ApiError(-1, err.errMsg ?? '网络异常'))
      },
    })
  })
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>('GET', url, params),
  post: <T>(url: string, data?: object) => request<T>('POST', url, data),
  put: <T>(url: string, data?: object) => request<T>('PUT', url, data),
  delete: <T>(url: string) => request<T>('DELETE', url),
}
