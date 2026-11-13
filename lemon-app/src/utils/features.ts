import { ref } from 'vue'

/**
 * 小程序功能开关（后台「系统参数」控制，启动时随 /app-config 拉取）：
 * - showActivation：激活码入口（输入激活码、「去激活」引导）
 * - showRestore：「恢复老系统错题」入口
 * 网页端始终显示。小程序在拿到后台配置前按上次缓存，没有缓存时默认隐藏（审核期间宁可少显示）。
 */
const CACHE_KEY = 'lemon_mp_features'

let isMp = false
// #ifdef MP-WEIXIN
isMp = true
// #endif

const cached: { activation?: boolean; restore?: boolean } = isMp ? uni.getStorageSync(CACHE_KEY) || {} : {}

export const showActivation = ref(!isMp || cached.activation === true)
export const showRestore = ref(!isMp || cached.restore === true)

/** 用后台最新配置更新开关（网页端忽略） */
export function setMpFeatures(cfg: { mpActivation?: boolean; mpRestore?: boolean }) {
  if (!isMp) return
  showActivation.value = !!cfg.mpActivation
  showRestore.value = !!cfg.mpRestore
  uni.setStorageSync(CACHE_KEY, { activation: showActivation.value, restore: showRestore.value })
}
