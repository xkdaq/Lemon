<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'
import type { AppUser } from '@/api/types'
import { clearAccessCache } from '@/utils/access'
import { isLoggedIn, removeToken } from '@/utils/auth'
import { confirmModal } from '@/utils/modal'

/** 个人信息：昵称、用户名、手机号、密码、退出登录 */
const user = ref<AppUser | null>(null)

onShow(async () => {
  if (!isLoggedIn()) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  user.value = await authApi.getMe().catch(() => null)
})

const maskPhone = (p?: string) => (p ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '')

function editNickname() {
  if (!user.value) return
  uni.showModal({
    title: '修改昵称',
    editable: true,
    placeholderText: '最多 32 个字',
    content: user.value.nickname ?? '',
    success: async (r) => {
      if (!r.confirm) return
      const name = (r.content ?? '').trim()
      if (!name) return uni.showToast({ title: '昵称不能为空', icon: 'none' })
      if (name.length > 32) return uni.showToast({ title: '昵称最多 32 个字', icon: 'none' })
      await authApi.updateNickname(name)
      if (user.value) user.value.nickname = name
      uni.showToast({ title: '已修改', icon: 'success' })
    },
  })
}

function copyUsername() {
  if (!user.value) return
  uni.setClipboardData({ data: user.value.username, success: () => uni.showToast({ title: '已复制用户名', icon: 'none' }) })
}

const go = (url: string) => uni.navigateTo({ url })

async function logout() {
  const { confirm } = await confirmModal({ title: '退出登录？' })
  if (!confirm) return
  await authApi.logout().catch(() => undefined)
  removeToken()
  clearAccessCache()
  uni.switchTab({ url: '/pages/mine/index' })
}
</script>

<template>
  <view v-if="user" class="page">
    <view class="head">
      <view class="avatar">{{ user.nickname?.slice(0, 1) ?? '柠' }}</view>
      <view class="head-name">{{ user.nickname }}</view>
    </view>

    <view class="card list-card">
      <view class="list-row" @click="editNickname">
        <view class="grow label">昵称</view>
        <text class="value">{{ user.nickname }}</text>
        <text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="copyUsername">
        <view class="grow label">用户名</view>
        <text class="value mono">{{ user.username }}</text>
      </view>
      <view class="list-row">
        <view class="grow label">手机号</view>
        <text class="value">{{ maskPhone(user.phone) }}</text>
      </view>
    </view>

    <view class="card list-card">
      <view class="list-row" @click="go('/pages/settings/password')">
        <view class="grow label">{{ user.hasPassword ? '修改密码' : '设置密码' }}</view>
        <text v-if="!user.hasPassword" class="value warn">未设置</text>
        <text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="go('/pages/activate/index')">
        <view class="grow label">我的模块</view>
        <text class="row-arrow">›</text>
      </view>
    </view>

    <view class="card logout" @click="logout">退出登录</view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.head {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0 16rpx;
}
.avatar {
  width: 144rpx;
  height: 144rpx;
  border-radius: 50%;
  background: $lemon-primary-light;
  color: $lemon-primary;
  font-weight: 700;
  font-size: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.head-name {
  margin-top: 20rpx;
  font-size: 32rpx;
  font-weight: 600;
}
.grow {
  flex: 1;
  min-width: 0;
}
.label {
  font-size: 30rpx;
}
.value {
  font-size: 28rpx;
  color: $lemon-muted;
  max-width: 50%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mono {
  font-family: ui-monospace, Menlo, monospace;
}
.warn {
  color: $lemon-primary;
}
.logout {
  text-align: center;
  color: $lemon-danger;
}
</style>
