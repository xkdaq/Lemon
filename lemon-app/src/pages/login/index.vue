<script setup lang="ts">
import { reactive, ref } from 'vue'
import * as authApi from '@/api/auth'
import { setToken } from '@/utils/auth'

/**
 * 一期：手机号 + 密码（短信签名审核通过后改为验证码登录）
 * 二期小程序：微信一键登录（条件编译 #ifdef MP-WEIXIN）
 */
const isRegister = ref(false)
const loading = ref(false)
const form = reactive({ phone: '', password: '', nickname: '' })

async function submit() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  if (form.password.length < 6) {
    uni.showToast({ title: '密码至少 6 位', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = isRegister.value
      ? await authApi.register({ phone: form.phone, password: form.password, nickname: form.nickname || undefined })
      : await authApi.login({ phone: form.phone, password: form.password })
    setToken(res.token)
    uni.showToast({ title: isRegister.value ? '注册成功' : '登录成功', icon: 'success' })
    setTimeout(() => {
      if (getCurrentPages().length > 1) {
        uni.navigateBack()
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }, 600)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="login">
    <view class="title">{{ isRegister ? '注册账号' : '欢迎回来' }}</view>
    <view class="muted sub">登录后同步做题进度、收藏和错题</view>

    <view class="field">
      <input v-model="form.phone" type="number" maxlength="11" placeholder="手机号" />
    </view>
    <view class="field">
      <input v-model="form.password" password maxlength="32" placeholder="密码（至少 6 位）" />
    </view>
    <view v-if="isRegister" class="field">
      <input v-model="form.nickname" maxlength="32" placeholder="昵称（选填）" />
    </view>

    <button class="submit" :loading="loading" :disabled="loading" @click="submit">
      {{ isRegister ? '注册并登录' : '登录' }}
    </button>
    <view class="switch" @click="isRegister = !isRegister">
      {{ isRegister ? '已有账号？去登录' : '没有账号？立即注册' }}
    </view>
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
  margin: 12rpx 0 56rpx;
}
.field {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
}
.submit {
  margin-top: 40rpx;
  background: $lemon-primary;
  color: #fff;
  border-radius: 999rpx;
}
.switch {
  margin-top: 32rpx;
  text-align: center;
  color: #b88a00;
}
</style>
