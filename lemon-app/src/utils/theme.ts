/**
 * 主题配色（由管理后台「系统参数 → 用户端主题配色」设定）。
 *
 * - 配色值：预设名（paper / indigo / teal / amber / rose / violet / coffee / graphite）或自定义主色 #RRGGBB
 * - 每套配色都有浅色、深色两组颜色；用户在「设置」里选的 跟随系统 / 浅色 / 深色 决定用哪组
 * - 默认配色 paper 写在 App.vue 里；其他配色在 H5 运行时注入一段样式覆盖。
 *   小程序暂不支持运行时换配色（全局 CSS 变量无法动态修改），始终使用默认配色。
 */
import { http } from './request'

export interface Palette {
  bg: string
  surface: string
  text: string
  muted: string
  line: string
  accent: string
  accentSoft: string
  key: string
  g0: string
  g1: string
  g2: string
  doneColor: string
  g2Soft: string
  g0Soft: string
  shadow: string
}

interface Scheme {
  light: Palette
  dark: Palette
}

export const DEFAULT_THEME = 'paper'

export const PRESETS: Record<string, Scheme> = {
  /** 暖纸赭红（与 App.vue 默认值一致） */
  paper: {
    light: {
      bg: '#f6f1e8', surface: '#fffdf8', text: '#2b2520', muted: '#8a7f73', line: '#e7dfd2',
      accent: '#b4432f', accentSoft: '#f6e3dc', key: '#9a3412',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', doneColor: '#ebc3b5', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(60, 40, 20, 0.06), 0 4px 16px rgba(60, 40, 20, 0.05)',
    },
    dark: {
      bg: '#171412', surface: '#221e1b', text: '#ece5dc', muted: '#9b9086', line: '#36302b',
      accent: '#e0785f', accentSoft: '#3a2620', key: '#f0a07f',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', doneColor: '#6b4236', g2Soft: '#1f3328', g0Soft: '#3a2620',
      shadow: 'none',
    },
  },
  /** 靛蓝 */
  indigo: {
    light: {
      bg: '#f5f6fb', surface: '#ffffff', text: '#0f172a', muted: '#64748b', line: '#e2e5f0',
      accent: '#4338ca', accentSoft: '#e9e8fa', key: '#3730a3',
      g0: '#dc2626', g1: '#d97706', g2: '#16a34a', doneColor: '#bcb8f0', g2Soft: '#e6f6ec', g0Soft: '#fdeaea',
      shadow: '0 1px 2px rgba(67, 56, 202, 0.04), 0 10px 26px rgba(67, 56, 202, 0.06)',
    },
    dark: {
      bg: '#0f1117', surface: '#1a1d27', text: '#e2e8f0', muted: '#94a3b8', line: '#2a2f3d',
      accent: '#818cf8', accentSoft: '#262a4a', key: '#a5b4fc',
      g0: '#f87171', g1: '#fbbf24', g2: '#4ade80', doneColor: '#3f4380', g2Soft: '#16301f', g0Soft: '#3a1e1e',
      shadow: 'none',
    },
  },
  /** 青绿 */
  teal: {
    light: {
      bg: '#f3f7f6', surface: '#ffffff', text: '#1b2826', muted: '#6b7f7b', line: '#dfe8e6',
      accent: '#0f766e', accentSoft: '#dcefec', key: '#115e59',
      g0: '#c2410c', g1: '#b7791f', g2: '#15803d', doneColor: '#a3d3cd', g2Soft: '#e3f2e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(15, 60, 55, 0.05), 0 4px 16px rgba(15, 60, 55, 0.05)',
    },
    dark: {
      bg: '#101615', surface: '#19211f', text: '#e3ece9', muted: '#8fa39e', line: '#2a3532',
      accent: '#2aa89a', accentSoft: '#1d3532', key: '#5eead4',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', doneColor: '#24564f', g2Soft: '#1f3328', g0Soft: '#3a2620',
      shadow: 'none',
    },
  },
  /** 琥珀 */
  amber: {
    light: {
      bg: '#faf6ec', surface: '#fffdf7', text: '#2a2418', muted: '#8a7d62', line: '#ece3cc',
      accent: '#b45309', accentSoft: '#fbecd6', key: '#92400e', doneColor: '#f0c99a',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(80, 55, 10, 0.06), 0 4px 16px rgba(80, 55, 10, 0.05)',
    },
    dark: {
      bg: '#16130c', surface: '#211c13', text: '#efe7d6', muted: '#a2967c', line: '#3a3222',
      accent: '#d97706', accentSoft: '#3a2a12', key: '#fbbf24', doneColor: '#6b4a1c',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  },
  /** 玫瑰 */
  rose: {
    light: {
      bg: '#fbf4f5', surface: '#fffbfb', text: '#2b1d21', muted: '#8c7479', line: '#f0dfe2',
      accent: '#be123c', accentSoft: '#fbe2e8', key: '#9f1239', doneColor: '#f2b3c2',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(90, 20, 40, 0.05), 0 4px 16px rgba(90, 20, 40, 0.05)',
    },
    dark: {
      bg: '#171113', surface: '#22191c', text: '#f1e4e7', muted: '#a38d92', line: '#3a2a2f',
      accent: '#e11d48', accentSoft: '#3d1a24', key: '#fda4af', doneColor: '#6e2738',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  },
  /** 紫罗兰 */
  violet: {
    light: {
      bg: '#f7f5fc', surface: '#fffeff', text: '#1f1a2e', muted: '#776f8c', line: '#e6e1f2',
      accent: '#6d28d9', accentSoft: '#ede5fb', key: '#5b21b6', doneColor: '#c9b4f2',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(60, 30, 120, 0.05), 0 8px 22px rgba(60, 30, 120, 0.05)',
    },
    dark: {
      bg: '#121019', surface: '#1c1926', text: '#e9e5f3', muted: '#9a93ad', line: '#2f2a3d',
      accent: '#8b5cf6', accentSoft: '#2a2143', key: '#c4b5fd', doneColor: '#4b3680',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  },
  /** 咖啡 */
  coffee: {
    light: {
      bg: '#f5f0ea', surface: '#fffcf8', text: '#2a211b', muted: '#85776b', line: '#e6dbcf',
      accent: '#7c4a2d', accentSoft: '#f0e2d8', key: '#5c3520', doneColor: '#d5b7a2',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(60, 40, 25, 0.06), 0 4px 16px rgba(60, 40, 25, 0.05)',
    },
    dark: {
      bg: '#151210', surface: '#201b18', text: '#ede4dc', muted: '#9e9187', line: '#372f29',
      accent: '#b07a55', accentSoft: '#33251c', key: '#e0b48f', doneColor: '#5e4231',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  },
  /** 石墨 */
  graphite: {
    light: {
      bg: '#f4f4f5', surface: '#ffffff', text: '#18181b', muted: '#71717a', line: '#e4e4e7',
      accent: '#27272a', accentSoft: '#e9e9eb', key: '#18181b', doneColor: '#b4b4bb',
      g0: '#c2410c', g1: '#b7791f', g2: '#2f855a', g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(20, 20, 25, 0.05), 0 4px 16px rgba(20, 20, 25, 0.05)',
    },
    dark: {
      bg: '#111113', surface: '#1b1b1e', text: '#e7e7ea', muted: '#9a9aa2', line: '#2e2e33',
      accent: '#71717a', accentSoft: '#2a2a2f', key: '#d4d4d8', doneColor: '#44444b',
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  },
}

// ---------------- 自定义主色：中性底色 + 由主色推算浅色 / 深色 ----------------

const toRgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16))
const toHex = (rgb: number[]) => `#${rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('')}`
/** 按比例 t 把颜色 a 混向 b */
const mix = (a: string, b: string, t: number) => {
  const x = toRgb(a)
  const y = toRgb(b)
  return toHex(x.map((v, i) => v + (y[i] - v) * t))
}

function customScheme(accent: string): Scheme {
  const lightSurface = '#ffffff'
  const darkSurface = '#1e1f22'
  const darkAccent = mix(accent, '#ffffff', 0.25)
  return {
    light: {
      bg: '#f5f6f8', surface: lightSurface, text: '#1f2329', muted: '#80868f', line: '#e4e6ea',
      accent, accentSoft: mix(accent, lightSurface, 0.88), key: mix(accent, '#000000', 0.2),
      g0: '#d9480f', g1: '#c27c0e', g2: '#2f855a', doneColor: mix(accent, lightSurface, 0.7),
      g2Soft: '#e3f1e8', g0Soft: '#fbe6dc',
      shadow: '0 1px 2px rgba(20, 25, 35, 0.05), 0 4px 16px rgba(20, 25, 35, 0.05)',
    },
    dark: {
      bg: '#141517', surface: darkSurface, text: '#e8e9eb', muted: '#8f949c', line: '#2e3035',
      accent: darkAccent, accentSoft: mix(darkAccent, darkSurface, 0.82), key: mix(darkAccent, '#ffffff', 0.2),
      g0: '#f08a5d', g1: '#e0b04d', g2: '#5fbf8a', doneColor: mix(darkAccent, darkSurface, 0.6),
      g2Soft: '#1f3328', g0Soft: '#3a2620', shadow: 'none',
    },
  }
}

export function resolveScheme(theme: string): Scheme {
  if (/^#[0-9a-fA-F]{6}$/.test(theme)) return customScheme(theme.toLowerCase())
  return PRESETS[theme] ?? PRESETS[DEFAULT_THEME]
}

// ---------------- 读取 / 应用 ----------------

const CACHE_KEY = 'lemon_app_theme'

/** 当前配色（本地缓存，启动时先用缓存，避免先闪一下默认色） */
export function getAppTheme(): string {
  const v = uni.getStorageSync(CACHE_KEY)
  return typeof v === 'string' && v ? v : DEFAULT_THEME
}

/** 启动时从服务器拉取最新配色，有变化就重新应用 */
export async function syncAppTheme(apply: () => void) {
  try {
    const { theme } = await http.get<{ theme: string }>('/app-config')
    if (theme && theme !== getAppTheme()) {
      uni.setStorageSync(CACHE_KEY, theme)
      apply()
    }
  } catch {
    // 网络失败沿用缓存
  }
}

const vars = (p: Palette) =>
  `--bg:${p.bg};--surface:${p.surface};--text:${p.text};--muted:${p.muted};--line:${p.line};` +
  `--accent:${p.accent};--accent-soft:${p.accentSoft};--key:${p.key};--g0:${p.g0};--g1:${p.g1};--g2:${p.g2};` +
  `--done-color:${p.doneColor};--g2-soft:${p.g2Soft};--g0-soft:${p.g0Soft};--shadow:${p.shadow};`

/**
 * H5：把配色写成一段样式注入页面。选择器比 App.vue 里的默认值更具体，所以能覆盖；
 * 默认配色直接移除这段样式，用 App.vue 的写法。
 */
export function injectPalette(theme: string) {
  // #ifdef H5
  const id = 'lemon-palette'
  document.getElementById(id)?.remove()
  if (theme === DEFAULT_THEME) return
  const { light, dark } = resolveScheme(theme)
  const el = document.createElement('style')
  el.id = id
  el.textContent = `
html body, html body uni-page-body { ${vars(light)} background: var(--bg); }
@media (prefers-color-scheme: dark) { html body, html body uni-page-body { ${vars(dark)} } }
html body.theme-light, html body.theme-light uni-page-body { ${vars(light)} }
html body.theme-dark, html body.theme-dark uni-page-body { ${vars(dark)} }
html body .uni-page-head, html body.theme-light .uni-page-head, html body.theme-dark .uni-page-head {
  background-color: var(--bg) !important;
}
html body .uni-tabbar, html body.theme-light .uni-tabbar, html body.theme-dark .uni-tabbar {
  background-color: var(--surface) !important;
}
html body .uni-tabbar-border, html body.theme-light .uni-tabbar-border, html body.theme-dark .uni-tabbar-border {
  background-color: var(--line) !important;
}`
  document.head.appendChild(el)
  // #endif
}

/** 底部 Tab 的文字颜色只能用接口改：按当前实际是浅色还是深色取色 */
export function applyTabBar(theme: string, dark: boolean) {
  // #ifdef H5
  const p = resolveScheme(theme)[dark ? 'dark' : 'light']
  uni.setTabBarStyle({ color: p.muted, selectedColor: p.accent, backgroundColor: p.surface, fail: () => undefined })
  // #endif
}
