import { getToken, setToken } from './auth'

/**
 * 小程序登录（只在 MP-WEIXIN 生效，H5 下这些函数什么都不做）。
 * - 首次：在登录页用账号密码登录（或注册 / 找回密码），请求里带上 wx.login 的 code，后端把这个微信绑到账号上
 * - 以后：静默登录，wx.login 的 code 换 openid，这个微信绑过账号就直接登录
 * （个人主体的小程序用不了「手机号快速验证」，所以没有授权手机号登录）
 * 用户主动退出登录后不再静默登录，直到他再次手动登录。
 * 隐私合规：本机从没手动登录过（没在登录页同意过用户协议和隐私政策）时不静默登录，不调 wx.login。
 */
const LOGGED_OUT_KEY = 'lemon_wx_logged_out'
/** 本机在登录页同意协议并登录过：之后才允许静默登录 */
const CONSENTED_KEY = 'lemon_wx_consented'
/** 静默登录成功时广播，首页等已经显示的页面据此刷新 */
export const LOGIN_EVENT = 'lemon:login'

let pending: Promise<boolean> | null = null

/**
 * 登录接口直接用 uni.request，不走 request.ts（request.ts 遇到 401 会调这里，互相引用会形成循环依赖）。
 * quiet：出错不弹提示（静默登录用）
 */
function post<T>(url: string, data: object, quiet = false): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: import.meta.env.VITE_API_BASE + url,
      method: 'POST',
      data: data as UniApp.RequestOptions['data'],
      success: (res) => {
        const body = res.data as { code?: number; msg?: string; data: T } | undefined
        if (body?.code === 0) return resolve(body.data)
        const msg = res.statusCode === 429 ? '操作太频繁，请稍后再试' : body?.msg || '登录失败，请重试'
        if (!quiet) uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        if (!quiet) uni.showToast({ title: '网络异常，请稍后再试', icon: 'none' })
        reject(new Error(err.errMsg ?? '网络异常'))
      },
    })
  })
}

function wxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (r) => (r.code ? resolve(r.code) : reject(new Error('微信登录失败'))),
      fail: () => reject(new Error('微信登录失败')),
    })
  })
}

/** 静默登录；成功返回 true。并发调用共用同一次请求 */
export function silentLogin(): Promise<boolean> {
  // #ifdef MP-WEIXIN
  if (getToken()) return Promise.resolve(true)
  if (uni.getStorageSync(LOGGED_OUT_KEY) || !uni.getStorageSync(CONSENTED_KEY)) return Promise.resolve(false)
  if (!pending) {
    pending = wxCode()
      .then((code) => post<{ token: string | null }>('/auth/wx-silent-login', { code }, true))
      .then((r) => {
        if (!r.token) return false
        setToken(r.token)
        uni.$emit(LOGIN_EVENT)
        return true
      })
      .catch(() => false)
      .finally(() => {
        pending = null
      })
  }
  return pending
  // #endif
  // #ifndef MP-WEIXIN
  return Promise.resolve(false)
  // #endif
}

/** 登录 / 注册 / 找回密码时附带的 wx.login code，用来绑定微信；H5 或获取失败时为 undefined（不影响登录） */
export async function getWxCode(): Promise<string | undefined> {
  // #ifdef MP-WEIXIN
  return wxCode().catch(() => undefined)
  // #endif
  // #ifndef MP-WEIXIN
  return undefined
  // #endif
}

/** 手动登录成功（任何方式，登录前已同意协议）后恢复静默登录 */
export function markLoggedIn() {
  uni.removeStorageSync(LOGGED_OUT_KEY)
  // #ifdef MP-WEIXIN
  uni.setStorageSync(CONSENTED_KEY, 1)
  // #endif
}

/** 主动退出登录：本机不再静默登录 */
export function markLoggedOut() {
  // #ifdef MP-WEIXIN
  uni.setStorageSync(LOGGED_OUT_KEY, 1)
  // #endif
}
