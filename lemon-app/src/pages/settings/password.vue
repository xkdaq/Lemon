<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'
import { useCountdown } from '@/utils/useCountdown'

/** 设置 / 修改密码：验证码发到账号绑定的手机号，验证本人后设置新密码 */
const phone = ref('')
const hasPassword = ref(false)
const form = reactive({ code: '', password: '', confirm: '' })
const sending = ref(false)
const saving = ref(false)
const { seconds, start } = useCountdown()

onLoad(async () => {
  const me = await authApi.getMe()
  phone.value = me.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
  hasPassword.value = !!me.hasPassword
  uni.setNavigationBarTitle({ title: hasPassword.value ? '修改密码' : '设置密码' })
})

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function sendCode() {
  if (seconds.value > 0 || sending.value) return
  sending.value = true
  try {
    await authApi.sendPasswordCode()
    start()
    toast('验证码已发送')
  } finally {
    sending.value = false
  }
}

async function save() {
  if (!/^\d{4,6}$/.test(form.code)) return toast('请输入验证码')
  if (form.password.length < 6) return toast('密码至少 6 位')
  if (form.password !== form.confirm) return toast('两次输入的密码不一致')
  saving.value = true
  try {
    await authApi.setPassword({ code: form.code, password: form.password })
    uni.showToast({ title: '密码已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="muted desc">验证码将发送到 {{ phone }}，设置后可以用手机号 + 密码登录。</view>
    <view class="field code-field">
      <input v-model="form.code" type="number" maxlength="6" placeholder="验证码" class="grow" />
      <text class="get-code" :class="{ off: seconds > 0 || sending }" @click="sendCode">
        {{ seconds > 0 ? `${seconds} 秒后重发` : sending ? '发送中…' : '获取验证码' }}
      </text>
    </view>
    <view class="field">
      <input v-model="form.password" password maxlength="32" placeholder="新密码（至少 6 位）" />
    </view>
    <view class="field">
      <input v-model="form.confirm" password maxlength="32" placeholder="再输入一次新密码" />
    </view>
    <button class="submit" :loading="saving" :disabled="saving" @click="save">保存</button>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding: 40rpx 40rpx;
}
.desc {
  margin-bottom: 32rpx;
  font-size: 26rpx;
}
.field {
  background: $lemon-surface;
  border: 1px solid $lemon-line;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
}
.code-field {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.grow {
  flex: 1;
}
.get-code {
  flex-shrink: 0;
  font-size: 26rpx;
  color: $lemon-primary;
}
.get-code.off {
  color: $lemon-muted;
}
.submit {
  margin-top: 40rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
}
</style>
