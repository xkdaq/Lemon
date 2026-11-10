import { isLoggedIn } from './auth'

/** 做题需要登录：未登录先去登录页，登录后返回当前页再点一次即可 */
export function goPractice(chapterId: number, mode: 'practice' | 'recite' | 'exam', questionType?: number) {
  if (!isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/login/index' }), 400)
    return
  }
  const type = questionType ? `&type=${questionType}` : ''
  uni.navigateTo({ url: `/pages/practice/index?chapterId=${chapterId}&mode=${mode}${type}` })
}
