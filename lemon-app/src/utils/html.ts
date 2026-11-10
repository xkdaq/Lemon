/** 静态资源前缀：H5 走同域（开发期由 vite 代理 /uploads），小程序需要完整地址 */
const ASSET_BASE = import.meta.env.VITE_ASSET_BASE ?? ''

/**
 * 题干 / 解析 HTML 渲染前处理（rich-text 组件用）：
 * - 图片限制宽度，避免撑破屏幕
 * - 相对路径 /uploads/... 补上资源域名（小程序没有“同域”概念）
 * 后端保存时已做白名单过滤，这里不再处理安全问题。
 */
export function renderHtml(html?: string | null): string {
  if (!html) {
    return ''
  }
  return html
    .replace(/<img /g, '<img style="max-width:100%;height:auto;display:block;margin:8px 0;" ')
    .replace(/src="\/uploads\//g, `src="${ASSET_BASE}/uploads/`)
}

/** 秒数格式化为 mm:ss 或 h:mm:ss */
export function formatSeconds(total: number): string {
  const s = Math.max(0, Math.floor(total))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h ? `${h}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`
}

/** 单个资源地址（图片 src 等）补上资源域名 */
export function assetUrl(url?: string | null): string {
  if (!url) return ''
  return url.startsWith('/uploads/') ? `${ASSET_BASE}${url}` : url
}
