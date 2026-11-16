import { ref } from 'vue'

/** 科目标签颜色 */
const SUBJECT_COLORS: Record<string, string> = {
  马原: '#b5322a',
  毛概: '#a8670d',
  习思想: '#c2410c',
  史纲: '#6b4f2a',
  思法: '#2f7d4f',
  时政: '#2563a8',
}
export const subjectColor = (s: string | null) => SUBJECT_COLORS[s ?? ''] ?? '#888888'

/** “遮住答案”开关：四个页面共用，记在本地 */
const HIDE_KEY = 'recite.hideAnswer'
export function useHideAnswer() {
  const hide = ref<boolean>(!!uni.getStorageSync(HIDE_KEY))
  const revealed = ref<Record<string, boolean>>({})
  function toggleHide() {
    hide.value = !hide.value
    revealed.value = {}
    uni.setStorageSync(HIDE_KEY, hide.value ? 1 : '')
  }
  /** 该答案当前是否被遮住 */
  const hidden = (key: string) => hide.value && !revealed.value[key]
  function reveal(key: string) {
    if (hide.value) revealed.value = { ...revealed.value, [key]: !revealed.value[key] }
  }
  return { hide, toggleHide, hidden, reveal, revealed }
}
