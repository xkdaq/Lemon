/**
 * 本机设置（存在本地，不同步到服务器）：主题、做题字号
 */
export type ThemeMode = 'system' | 'light' | 'dark'

const THEME_KEY = 'lemon_theme'
const FONT_KEY = 'lemon_font_scale'

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

/** 应用主题：H5 在 body 上加 theme-light / theme-dark，跟随系统时不加（走 prefers-color-scheme） */
export function applyTheme() {
  // #ifdef H5
  const mode = getTheme()
  document.body.classList.remove('theme-light', 'theme-dark')
  if (mode !== 'system') {
    document.body.classList.add(`theme-${mode}`)
  }
  // #endif
}

export function getFontScale(): number {
  const v = Number(uni.getStorageSync(FONT_KEY))
  return FONT_SCALES.some((f) => f.value === v) ? v : 1
}

export function setFontScale(v: number) {
  uni.setStorageSync(FONT_KEY, v)
}
