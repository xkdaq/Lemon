/**
 * 本机设置（存在本地，不同步到服务器）：主题（浅色 / 深色）、做题字号、答对自动下一题。
 * 配色本身由管理后台设定，见 utils/theme.ts
 */
import { applyTabBar, getAppTheme, injectPalette } from './theme'

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

/**
 * 应用主题：H5 在 body 上加 theme-light / theme-dark，跟随系统时不加（走 prefers-color-scheme）；
 * 同时应用后台设定的配色，并按实际的浅色 / 深色设置底部 Tab 颜色
 */
export function applyTheme() {
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
