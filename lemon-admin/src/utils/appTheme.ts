/**
 * 用户端主题配色的预览数据。完整配色定义在用户端 lemon-app/src/utils/theme.ts，
 * 这里只取预览需要的几种颜色；新增 / 修改预设时两边一起改，后端 SysConfigService 的校验也要加上新预设名。
 */
export interface PreviewPalette {
  bg: string
  surface: string
  text: string
  muted: string
  line: string
  accent: string
  accentSoft: string
}

export interface ThemePreset {
  key: string
  name: string
  light: PreviewPalette
  dark: PreviewPalette
}

export const THEME_PRESETS: ThemePreset[] = [
  {
    key: 'paper',
    name: '暖纸赭红',
    light: { bg: '#f6f1e8', surface: '#fffdf8', text: '#2b2520', muted: '#8a7f73', line: '#e7dfd2', accent: '#b4432f', accentSoft: '#f6e3dc' },
    dark: { bg: '#171412', surface: '#221e1b', text: '#ece5dc', muted: '#9b9086', line: '#36302b', accent: '#e0785f', accentSoft: '#3a2620' },
  },
  {
    key: 'indigo',
    name: '靛蓝',
    light: { bg: '#f5f6fb', surface: '#ffffff', text: '#0f172a', muted: '#64748b', line: '#e2e5f0', accent: '#4338ca', accentSoft: '#e9e8fa' },
    dark: { bg: '#0f1117', surface: '#1a1d27', text: '#e2e8f0', muted: '#94a3b8', line: '#2a2f3d', accent: '#818cf8', accentSoft: '#262a4a' },
  },
  {
    key: 'teal',
    name: '青绿',
    light: { bg: '#f3f7f6', surface: '#ffffff', text: '#1b2826', muted: '#6b7f7b', line: '#dfe8e6', accent: '#0f766e', accentSoft: '#dcefec' },
    dark: { bg: '#101615', surface: '#19211f', text: '#e3ece9', muted: '#8fa39e', line: '#2a3532', accent: '#2aa89a', accentSoft: '#1d3532' },
  },
  {
    key: 'amber',
    name: '琥珀',
    light: { bg: '#faf6ec', surface: '#fffdf7', text: '#2a2418', muted: '#8a7d62', line: '#ece3cc', accent: '#b45309', accentSoft: '#fbecd6' },
    dark: { bg: '#16130c', surface: '#211c13', text: '#efe7d6', muted: '#a2967c', line: '#3a3222', accent: '#d97706', accentSoft: '#3a2a12' },
  },
  {
    key: 'rose',
    name: '玫瑰',
    light: { bg: '#fbf4f5', surface: '#fffbfb', text: '#2b1d21', muted: '#8c7479', line: '#f0dfe2', accent: '#be123c', accentSoft: '#fbe2e8' },
    dark: { bg: '#171113', surface: '#22191c', text: '#f1e4e7', muted: '#a38d92', line: '#3a2a2f', accent: '#e11d48', accentSoft: '#3d1a24' },
  },
  {
    key: 'violet',
    name: '紫罗兰',
    light: { bg: '#f7f5fc', surface: '#fffeff', text: '#1f1a2e', muted: '#776f8c', line: '#e6e1f2', accent: '#6d28d9', accentSoft: '#ede5fb' },
    dark: { bg: '#121019', surface: '#1c1926', text: '#e9e5f3', muted: '#9a93ad', line: '#2f2a3d', accent: '#8b5cf6', accentSoft: '#2a2143' },
  },
  {
    key: 'coffee',
    name: '咖啡',
    light: { bg: '#f5f0ea', surface: '#fffcf8', text: '#2a211b', muted: '#85776b', line: '#e6dbcf', accent: '#7c4a2d', accentSoft: '#f0e2d8' },
    dark: { bg: '#151210', surface: '#201b18', text: '#ede4dc', muted: '#9e9187', line: '#372f29', accent: '#b07a55', accentSoft: '#33251c' },
  },
  {
    key: 'graphite',
    name: '石墨',
    light: { bg: '#f4f4f5', surface: '#ffffff', text: '#18181b', muted: '#71717a', line: '#e4e4e7', accent: '#27272a', accentSoft: '#e9e9eb' },
    dark: { bg: '#111113', surface: '#1b1b1e', text: '#e7e7ea', muted: '#9a9aa2', line: '#2e2e33', accent: '#71717a', accentSoft: '#2a2a2f' },
  },
]

export const isCustomColor = (v: string) => /^#[0-9a-fA-F]{6}$/.test(v)

const toRgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
const toHex = (rgb: number[]) => `#${rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`
const mix = (a: string, b: string, t: number) => {
  const y = toRgb(b)
  return toHex(toRgb(a).map((v, i) => v + (y[i] - v) * t))
}

/** 自定义主色的预览（推算方式与用户端一致） */
export function customPreset(accent: string): ThemePreset {
  const darkAccent = mix(accent, '#ffffff', 0.25)
  return {
    key: accent,
    name: '自定义',
    light: { bg: '#f5f6f8', surface: '#ffffff', text: '#1f2329', muted: '#80868f', line: '#e4e6ea', accent, accentSoft: mix(accent, '#ffffff', 0.88) },
    dark: { bg: '#141517', surface: '#1e1f22', text: '#e8e9eb', muted: '#8f949c', line: '#2e3035', accent: darkAccent, accentSoft: mix(darkAccent, '#1e1f22', 0.82) },
  }
}

/** 主色与白色文字的对比度（按钮是白字）；低于 3 时按钮文字会看不清 */
export function contrastWithWhite(hex: string) {
  const lum = toRgb(hex)
    .map((v) => v / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  const l = 0.2126 * lum[0] + 0.7152 * lum[1] + 0.0722 * lum[2]
  return 1.05 / (l + 0.05)
}
