/**
 * 本机设置（存在本地，不同步到服务器）：主题（浅色 / 深色）、做题字号、答对自动下一题。
 * 配色本身由管理后台设定，见 utils/theme.ts
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { applyTabBar, getAppTheme, injectPalette, resolveScheme, vars } from './theme'

export type ThemeMode = 'system' | 'light' | 'dark'

const THEME_KEY = 'lemon_theme'
const FONT_KEY = 'lemon_font_scale'
const AUTO_NEXT_KEY = 'lemon_auto_next'

export const FONT_SCALES = [
  { value: 0.9, label: '小' },
  { value: 1, label: '标准' },
  { value: 1.15, label: '大' },
  { value: 1.3, label: '特大' },
] as const

export function getTheme(): ThemeMode {
  const v = uni.getStorageSync(THEME_KEY)
  return v === 'light' || v === 'dark' ? v : 'system'
}

export function setTheme(mode: ThemeMode) {
  uni.setStorageSync(THEME_KEY, mode)
  applyTheme()
}

// ---------------- 小程序：页面配色 ----------------
// 小程序没法改全局样式：当前用哪组颜色放在响应式变量里，每个页面用 page-meta 把它写到页面根节点上，
// 标题栏、底部 Tab、下拉背景用接口设置。H5 不用这些（见 applyTheme）。

const themeMode = ref<ThemeMode>(getTheme())
const appTheme = ref(getAppTheme())
const systemDark = ref(false)
// #ifdef MP-WEIXIN
systemDark.value = (uni.getAppBaseInfo?.() ?? uni.getSystemInfoSync()).theme === 'dark'
uni.onThemeChange?.((res) => {
  systemDark.value = res.theme === 'dark'
  applyMpChrome()
})
// #endif

const isDark = computed(() => themeMode.value === 'dark' || (themeMode.value === 'system' && systemDark.value))
const mpPalette = computed(() => resolveScheme(appTheme.value)[isDark.value ? 'dark' : 'light'])

/** 小程序页面根节点的样式（配色变量 + 背景色）；H5 为空 */
const pageStyle = computed(() => {
  // #ifdef MP-WEIXIN
  return `${vars(mpPalette.value)}background:${mpPalette.value.bg};`
  // #endif
  // #ifndef MP-WEIXIN
  return ''
  // #endif
})

/** 小程序：按当前配色设置标题栏、页面下拉背景、底部 Tab（标题栏要每个页面各设一次） */
function applyMpChrome() {
  // #ifdef MP-WEIXIN
  const p = mpPalette.value
  uni.setNavigationBarColor({ frontColor: isDark.value ? '#ffffff' : '#000000', backgroundColor: p.bg, fail: () => undefined })
  uni.setBackgroundColor?.({ backgroundColor: p.bg, backgroundColorTop: p.bg, backgroundColorBottom: p.bg, fail: () => undefined })
  // 底部 Tab 只能在 Tab 页设置（首页、我的显示时会再调用一次）
  const route = getCurrentPages().slice(-1)[0]?.route ?? ''
  if (TAB_PAGES.includes(route)) applyTabBar(appTheme.value, isDark.value)
  // #endif
}

const TAB_PAGES = ['pages/index/index', 'pages/mine/index']

/**
 * 每个页面调用：返回 page-meta 的 page-style，并在页面显示时设置标题栏等颜色。
 * 模板第一个节点写 <page-meta :page-style="themeStyle" />（只在小程序编译）
 */
export function usePageTheme() {
  onShow(applyMpChrome)
  return pageStyle
}

/**
 * 应用主题：H5 在 body 上加 theme-light / theme-dark，跟随系统时不加（走 prefers-color-scheme）；
 * 同时应用后台设定的配色，并按实际的浅色 / 深色设置底部 Tab 颜色。小程序更新页面配色变量和标题栏、Tab。
 */
export function applyTheme() {
  // #ifdef MP-WEIXIN
  themeMode.value = getTheme()
  appTheme.value = getAppTheme()
  applyMpChrome()
  // #endif
  // #ifdef H5
  const mode = getTheme()
  document.body.classList.remove('theme-light', 'theme-dark')
  if (mode !== 'system') {
    document.body.classList.add(`theme-${mode}`)
  }
  const palette = getAppTheme()
  injectPalette(palette)
  const dark = mode === 'dark' || (mode === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  applyTabBar(palette, dark)
  // #endif
}

/** 跟随系统时，系统切换深浅色也要更新底部 Tab 颜色 */
export function watchSystemTheme() {
  // #ifdef H5
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (getTheme() === 'system') applyTheme()
  })
  // #endif
}

export function getFontScale(): number {
  const v = Number(uni.getStorageSync(FONT_KEY))
  return FONT_SCALES.some((f) => f.value === v) ? v : 1
}

export function setFontScale(v: number) {
  uni.setStorageSync(FONT_KEY, v)
}

/** 练习模式答对后是否自动跳到下一题，默认开 */
export function getAutoNext(): boolean {
  return uni.getStorageSync(AUTO_NEXT_KEY) !== 'off'
}

export function setAutoNext(on: boolean) {
  uni.setStorageSync(AUTO_NEXT_KEY, on ? 'on' : 'off')
}
