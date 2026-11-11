import { getAccess, type AccessInfo } from '@/api/access'

/**
 * 模块激活状态：内存缓存，激活成功、登录、退出后调用 clearAccessCache() 刷新
 */
let cache: Promise<AccessInfo> | null = null

export function loadAccess(force = false): Promise<AccessInfo> {
  if (!cache || force) {
    cache = getAccess().catch((e) => {
      cache = null
      throw e
    })
  }
  return cache
}

export function clearAccessCache() {
  cache = null
}

/** 去激活页，可带上要激活的模块 */
export function goActivate(moduleId?: number | null) {
  uni.navigateTo({ url: `/pages/activate/index${moduleId ? `?moduleId=${moduleId}` : ''}` })
}
