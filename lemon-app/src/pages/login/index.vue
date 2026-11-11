<script setup lang="ts">
import { reactive, ref } from 'vue'
import * as authApi from '@/api/auth'
import { setToken } from '@/utils/auth'
import { useCountdown } from '@/utils/useCountdown'

/**
 * Web：手机号 + 验证码登录（没注册过自动注册），设置过密码的也可以用密码登录。
 * 二期小程序：微信登录 + 手机号快速验证（条件编译 #ifdef MP-WEIXIN），按手机号并入同一个账号。
 */
type Way = 'code' | 'password'
const way = ref<Way>('code')
const loading = ref(false)
const sending = ref(false)
const form = reactive({ phone: '', code: '', password: '' })
const { seconds, start } = useCountdown()

const phoneOk = () => /^1[3-9]\d{9}$/.test(form.phone)

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function sendCode() {
  if (seconds.value > 0 || sending.value) return
  if (!phoneOk()) return toast('请输入正确的手机号')
  sending.value = true
  try {
    await authApi.sendLoginCode(form.phone)
    start()
    toast('验证码已发送')
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!phoneOk()) return toast('请输入正确的手机号')
  if (way.value === 'code' && !/^\d{4,6}$/.test(form.code)) return toast('请输入验证码')
  if (way.value === 'password' && form.password.length < 6) return toast('密码至少 6 位')
  loading.value = true
  try {
    let notice = ''
    if (way.value === 'code') {
      const res = await authApi.smsLogin({ phone: form.phone, code: form.code })
      setToken(res.token)
      if (res.passwordCleared) notice = '为保护账号安全，之前设置的密码已失效，可在「设置」里重新设置'
    } else {
      const res = await authApi.login({ phone: form.phone, password: form.password })
      setToken(res.token)
    }
    const done = () => {
      if (getCurrentPages().length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }
    if (notice) {
      uni.showModal({ title: '登录成功', content: notice, showCancel: false, success: done })
    } else {
      uni.showToast({ title: '登录成功', icon: 'success' })
      setTimeout(done, 600)
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="login">
    <view class="title">登录 Lemon 刷题</view>
    <view class="muted sub">登录后同步做题进度、收藏和错题</view>

    <view class="tabs">
      <text class="tab" :class="{ on: way === 'code' }" @click="way = 'code'">验证码登录</text>
      <text class="tab" :class="{ on: way === 'password' }" @click="way = 'password'">密码登录</text>
    </view>

    <view class="field">
      <input v-model="form.phone" type="number" maxlength="11" placeholder="手机号" />
    </view>
    <view v-if="way === 'code'" class="field code-field">
      <input v-model="form.code" type="number" maxlength="6" placeholder="验证码" class="grow" />
      <text class="get-code" :class="{ off: seconds > 0 || sending }" @click="sendCode">
        {{ seconds > 0 ? `${seconds} 秒后重发` : sending ? '发送中…' : '获取验证码' }}
      </text>
    </view>
    <view v-else class="field">
      <input v-model="form.password" password maxlength="32" placeholder="密码" />
    </view>

    <button class="submit" :loading="loading" :disabled="loading" @click="submit">
      {{ way === 'code' ? '登录 / 注册' : '登录' }}
    </button>
    <view v-if="way === 'code'" class="muted tip">未注册的手机号验证后自动注册</view>
    <view v-else class="link" @click="way = 'code'">没有密码或忘记密码？用验证码登录</view>
  </view>
</template>

<style lang="scss" scoped>
.login {
  padding: 80rpx 56rpx;
}
.title {
  font-size: 48rpx;
  font-weight: 700;
}
.sub {
  margin: 12rpx 0 48rpx;
}
.tabs {
  display: flex;
  gap: 48rpx;
  margin-bottom: 32rpx;
}
.tab {
  font-size: 30rpx;
  color: $lemon-muted;
  padding-bottom: 10rpx;
  border-bottom: 4rpx solid transparent;
}
.tab.on {
  color: $lemon-text;
  font-weight: 600;
  border-bottom-color: $lemon-primary;
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
.tip,
.link {
  margin-top: 28rpx;
  text-align: center;
  font-size: 26rpx;
}
.link {
  color: $lemon-primary;
}
</style>
