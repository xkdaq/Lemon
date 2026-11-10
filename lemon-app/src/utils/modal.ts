/**
 * 确认弹窗：统一走主题色。
 * H5 的弹窗样式在 App.vue 里整体覆盖；小程序原生弹窗只能改按钮颜色，这里传主题色。
 */
const CONFIRM_COLOR = '#b4432f'
const CANCEL_COLOR = '#8a7f73'

export function confirmModal(options: UniApp.ShowModalOptions) {
  return uni.showModal({ confirmColor: CONFIRM_COLOR, cancelColor: CANCEL_COLOR, ...options })
}
