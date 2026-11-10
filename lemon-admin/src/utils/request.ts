import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, removeToken } from './auth'

/** 后端统一响应体 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

const instance = axios.create({
  baseURL: '/api/admin',
  timeout: 15000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (resp) => {
    const body = resp.data as R<unknown>
    if (body.code === 0) {
      return resp
    }
    if (body.code === 401) {
      removeToken()
      // 首次进入页面时由路由守卫负责跳转（它知道目标地址），这里只处理页面内请求过期的情况
      const current = router.currentRoute.value
      if (current.matched.length && current.name !== 'login') {
        router.replace({ name: 'login', query: { redirect: current.fullPath } })
      }
    }
    ElMessage.error(body.msg || '请求失败')
    return Promise.reject(new Error(body.msg))
  },
  (err) => {
    ElMessage.error(err?.response?.status === 429 ? '操作太频繁，请稍后再试' : '网络异常，请稍后再试')
    return Promise.reject(err)
  },
)

/** 只返回 data 字段 */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return instance.request<R<T>>(config).then((resp) => resp.data.data)
}

/** multipart 上传，extra 为附带的表单字段 */
export function upload<T>(url: string, file: File, extra: Record<string, string | number | undefined> = {}): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  for (const [k, v] of Object.entries(extra)) {
    if (v !== undefined && v !== null && v !== '') {
      form.append(k, String(v))
    }
  }
  return request<T>({ url, method: 'post', data: form, timeout: 120000 })
}

/**
 * 下载文件（带 token）。成功时后端返回文件流；失败时返回的是 JSON 错误体，需要从 Blob 里读出来提示。
 */
export async function download(url: string, params?: object, fallbackName = 'download.xlsx'): Promise<void> {
  const token = getToken()
  const resp = await axios.get<Blob>(`/api/admin${url}`, {
    params,
    responseType: 'blob',
    timeout: 120000,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (resp.data.type.includes('application/json')) {
    const body = JSON.parse(await resp.data.text()) as R<unknown>
    ElMessage.error(body.msg || '下载失败')
    throw new Error(body.msg)
  }
  const disposition = String(resp.headers['content-disposition'] ?? '')
  const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  const name = match ? decodeURIComponent(match[1]) : fallbackName
  const href = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = href
  a.download = name
  a.click()
  URL.revokeObjectURL(href)
}

export const http = {
  get: <T>(url: string, params?: object) => request<T>({ url, method: 'get', params }),
  post: <T>(url: string, data?: object) => request<T>({ url, method: 'post', data }),
  put: <T>(url: string, data?: object) => request<T>({ url, method: 'put', data }),
  delete: <T>(url: string) => request<T>({ url, method: 'delete' }),
}
