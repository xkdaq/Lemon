<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import * as authApi from '@/api/auth'
import { setToken } from '@/utils/auth'
import { clearAccessCache } from '@/utils/access'
import { useCountdown } from '@/utils/useCountdown'
import { openDocument } from '@/api/site'

/**
 * Web：手机号 / 用户名 + 密码登录；注册 = 手机号 + 短信验证码 + 设置密码；忘记密码用短信验证码重置。
 * 短信只在注册和找回密码时发，省短信费。
 * 二期小程序：授权手机号直接登录（条件编译 #ifdef MP-WEIXIN），按手机号并入同一个账号。
 */
type Mode = 'login' | 'register' | 'reset'
const mode = ref<Mode>('login')
const loading = ref(false)
const sending = ref(false)
/** 注册须勾选同意用户协议和隐私政策（默认不勾选） */
const agreed = ref(false)
const form = reactive({ account: '', phone: '', code: '', password: '', confirm: '', nickname: '' })
const { seconds, start } = useCountdown()

const TITLES: Record<Mode, string> = { login: '欢迎回来', register: '注册账号', reset: '找回密码' }
const submitText = computed(() => ({ login: '登录', register: '注册并登录', reset: '重置密码并登录' })[mode.value])

const phoneOk = () => /^1[3-9]\d{9}$/.test(form.phone)

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function switchMode(m: Mode) {
  mode.value = m
  form.code = ''
  form.password = ''
  form.confirm = ''
  // 登录框里输的是手机号就带过去
  if (m !== 'login' && /^1[3-9]\d{9}$/.test(form.account.trim())) form.phone = form.account.trim()
}

async function sendCode() {
  if (seconds.value > 0 || sending.value) return
  if (!phoneOk()) return toast('请输入正确的手机号')
  if (mode.value === 'register' && !agreed.value) return toast('请先阅读并同意用户协议和隐私政策')
  sending.value = true
  try {
    await authApi.sendCode(form.phone, mode.value === 'register' ? 'register' : 'reset')
    start()
    toast('验证码已发送')
  } finally {
    sending.value = false
  }
}

function validate(): string | null {
  if (mode.value === 'login') {
    if (!form.account.trim()) return '请输入手机号或用户名'
    if (!form.password) return '请输入密码'
    return null
  }
  if (!phoneOk()) return '请输入正确的手机号'
  if (!/^\d{4,6}$/.test(form.code)) return '请输入验证码'
  if (form.password.length < 6) return '密码至少 6 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  if (mode.value === 'register' && !agreed.value) return '请先阅读并同意用户协议和隐私政策'
  return null
}

async function submit() {
  const err = validate()
  if (err) return toast(err)
  loading.value = true
  try {
    const res =
      mode.value === 'login'
        ? await authApi.login({ account: form.account.trim(), password: form.password })
        : mode.value === 'register'
          ? await authApi.register({ phone: form.phone, code: form.code, password: form.password, nickname: form.nickname.trim() || undefined })
          : await authApi.resetPassword({ phone: form.phone, code: form.code, password: form.password })
    setToken(res.token)
    clearAccessCache()
    await afterLogin()
  } finally {
    loading.value = false
  }
}

async function afterLogin() {
  const me = await authApi.getMe().catch(() => null)
  // 后台重置过密码：提示改成自己的新密码
  if (me?.mustChangePassword) {
    uni.showModal({
      title: '请修改密码',
      content: '你的密码已由管理员重置，为了账号安全，请设置一个自己的新密码。',
      confirmText: '去修改',
      showCancel: false,
      success: () => uni.redirectTo({ url: '/pages/settings/password' }),
    })
    return
  }
  if (mode.value === 'register' && me) {
    // 告诉用户分配的用户名，以后也可以用它登录
    uni.showModal({
      title: '注册成功',
      content: `你的用户名是 ${me.username}，以后可以用手机号或用户名 + 密码登录。`,
      showCancel: false,
      success: goBack,
    })
    return
  }
  uni.showToast({ title: mode.value === 'reset' ? '密码已重置' : '登录成功', icon: 'success' })
  setTimeout(goBack, 600)
}

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/index/index' })
  }
}
</script>

<template>
  <view class="login">
    <view class="title">{{ TITLES[mode] }}</view>
    <view class="muted sub">{{ mode === 'reset' ? '用注册时的手机号接收验证码，设置新密码' : '登录后同步做题进度、收藏和错题' }}</view>

    <template v-if="mode === 'login'">
      <view class="field">
        <input v-model="form.account" maxlength="32" placeholder="手机号或用户名" />
      </view>
      <view class="field">
        <input v-model="form.password" password maxlength="32" placeholder="密码" @confirm="submit" />
      </view>
      <view class="row-links">
        <text class="link" @click="switchMode('reset')">忘记密码？</text>
      </view>
    </template>

    <template v-else>
      <view class="field">
        <input v-model="form.phone" type="number" maxlength="11" placeholder="手机号" />
      </view>
      <view class="field code-field">
        <input v-model="form.code" type="number" maxlength="6" placeholder="短信验证码" class="grow" />
        <text class="get-code" :class="{ off: seconds > 0 || sending }" @click="sendCode">
          {{ seconds > 0 ? `${seconds} 秒后重发` : sending ? '发送中…' : '获取验证码' }}
        </text>
      </view>
      <view class="field">
        <input v-model="form.password" password maxlength="32" :placeholder="mode === 'reset' ? '新密码（至少 6 位）' : '设置密码（至少 6 位）'" />
      </view>
      <view class="field">
        <input v-model="form.confirm" password maxlength="32" placeholder="再输入一次密码" />
      </view>
      <view v-if="mode === 'register'" class="field">
        <input v-model="form.nickname" maxlength="32" placeholder="昵称（选填，之后可以修改）" />
      </view>
    </template>

    <view v-if="mode === 'register'" class="agree" @click="agreed = !agreed">
      <view class="check" :class="{ on: agreed }">{{ agreed ? '✓' : '' }}</view>
      <text>已阅读并同意</text>
      <text class="doc-link" @click.stop="openDocument('agreement')">《用户协议》</text>
      <text>和</text>
      <text class="doc-link" @click.stop="openDocument('privacy')">《隐私政策》</text>
    </view>
    <button class="submit" :loading="loading" :disabled="loading" @click="submit">{{ submitText }}</button>

    <view class="switch">
      <text v-if="mode === 'login'" @click="switchMode('register')">没有账号？立即注册</text>
      <text v-else @click="switchMode('login')">{{ mode === 'register' ? '已有账号？去登录' : '想起密码了？去登录' }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login {
  padding: 64rpx 40rpx;
}
.title {
  font-size: 44rpx;
  font-weight: 700;
}
.sub {
  margin: 12rpx 0 48rpx;
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
.row-links {
  display: flex;
  justify-content: flex-end;
  margin-top: -8rpx;
}
.link {
  font-size: 26rpx;
  color: $lemon-primary;
}
.agree {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $lemon-muted;
}
.check {
  width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  margin-right: 10rpx;
  border: 1px solid $lemon-muted;
  border-radius: 6rpx;
  text-align: center;
  font-size: 22rpx;
  color: #fff;
}
.check.on {
  background: $lemon-primary;
  border-color: $lemon-primary;
}
.doc-link {
  color: $lemon-primary;
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
  color: $lemon-primary;
  font-size: 28rpx;
}
</style>
