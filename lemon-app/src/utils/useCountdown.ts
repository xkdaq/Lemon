import { onUnmounted, ref } from 'vue'

/** 获取验证码按钮的倒计时：start() 后 seconds 从 60 递减到 0 */
export function useCountdown(total = 60) {
  const seconds = ref(0)
  let timer: ReturnType<typeof setInterval> | undefined

  function start() {
    seconds.value = total
    clearInterval(timer)
    timer = setInterval(() => {
      seconds.value -= 1
      if (seconds.value <= 0) clearInterval(timer)
    }, 1000)
  }

  onUnmounted(() => clearInterval(timer))
  return { seconds, start }
}
