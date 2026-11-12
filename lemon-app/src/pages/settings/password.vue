<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'

/**
 * 设置 / 修改密码：已有密码要输入原密码；还没有密码的（如小程序授权登录的用户）直接设置。
 * 忘了原密码就退出登录，在登录页用「忘记密码」短信找回。
 */
const hasPassword = ref(true)
const mustChange = ref(false)
const form = reactive({ old: '', password: '', confirm: '' })
const saving = ref(false)

onLoad(async () => {
  const me = await authApi.getMe()
  hasPassword.value = !!me.hasPassword
  mustChange.value = !!me.mustChangePassword
  uni.setNavigationBarTitle({ title: hasPassword.value ? '修改密码' : '设置密码' })
})

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function save() {
  if (hasPassword.value && !form.old) return toast(mustChange.value ? '请输入管理员给你的临时密码' : '请输入原密码')
  if (form.password.length < 6) return toast('新密码至少 6 位')
  if (form.password !== form.confirm) return toast('两次输入的密码不一致')
  saving.value = true
  try {
    await authApi.changePassword({ oldPassword: hasPassword.value ? form.old : undefined, password: form.password })
    uni.showToast({ title: '密码已保存', icon: 'success' })
    setTimeout(() => {
      if (getCurrentPages().length > 1) uni.navigateBack()
      else uni.switchTab({ url: '/pages/index/index' })
    }, 800)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="muted desc">
      {{
        mustChange
          ? '你的密码已由管理员重置，请输入临时密码，再设置一个自己的新密码。'
          : hasPassword
            ? '修改后其他设备上的登录会退出。忘了原密码可以退出登录，在登录页点「忘记密码」用短信找回。'
            : '设置后可以在网页上用手机号或用户名 + 密码登录。'
      }}
    </view>
    <view v-if="hasPassword" class="field">
      <input v-model="form.old" password maxlength="32" :placeholder="mustChange ? '临时密码' : '原密码'" />
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
  padding: 32rpx;
}
.desc {
  margin-bottom: 32rpx;
  font-size: 26rpx;
  line-height: 1.6;
}
.field {
  background: $lemon-surface;
  border: 1px solid $lemon-line;
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
</style>
