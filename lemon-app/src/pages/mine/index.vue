<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'
import { getStats, type Stats } from '@/api/mine'
import type { AppUser } from '@/api/types'
import { isLoggedIn, removeToken } from '@/utils/auth'

const user = ref<AppUser | null>(null)
const stats = ref<Stats | null>(null)

onShow(async () => {
  if (!isLoggedIn()) {
    user.value = null
    stats.value = null
    return
  }
  const [u, s] = await Promise.all([authApi.getMe().catch(() => null), getStats().catch(() => null)])
  user.value = u
  stats.value = s
})

const goLogin = () => uni.navigateTo({ url: '/pages/login/index' })
const go = (url: string) => (user.value ? uni.navigateTo({ url }) : goLogin())
const goTab = (url: string) => uni.switchTab({ url })

async function logout() {
  const { confirm } = await uni.showModal({ title: '退出登录？' })
  if (!confirm) return
  await authApi.logout().catch(() => undefined)
  removeToken()
  user.value = null
  stats.value = null
}
</script>

<template>
  <view>
    <view class="card profile" @click="!user && goLogin()">
      <view class="avatar">{{ user?.nickname?.slice(0, 1) ?? '🍋' }}</view>
      <view>
        <view class="nickname">{{ user?.nickname ?? '点击登录' }}</view>
        <view class="muted">{{ user ? user.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '登录后同步做题记录' }}</view>
      </view>
      <view v-if="stats?.streakDays" class="streak">
        <text class="streak-num">{{ stats.streakDays }}</text>
        <text class="muted">天连续打卡</text>
      </view>
    </view>

    <view class="card stats">
      <view class="stat"><view class="num">{{ stats?.questionTotal ?? '-' }}</view><view class="muted">做过题数</view></view>
      <view class="stat"><view class="num">{{ stats ? `${stats.accuracy}%` : '-' }}</view><view class="muted">正确率</view></view>
      <view class="stat"><view class="num">{{ stats?.todayCount ?? '-' }}</view><view class="muted">今日作答</view></view>
      <view class="stat"><view class="num">{{ stats?.answerTotal ?? '-' }}</view><view class="muted">累计作答</view></view>
    </view>

    <view class="card menu">
      <view class="menu-item" @click="goTab('/pages/wrong/index')">
        <text>错题集</text><text class="muted">{{ stats ? `${stats.wrongCount} 题` : '' }} ›</text>
      </view>
      <view class="menu-item" @click="goTab('/pages/favorite/index')">
        <text>收藏集</text><text class="muted">{{ stats ? `${stats.favoriteCount} 题` : '' }} ›</text>
      </view>
      <view class="menu-item" @click="go('/pages/feedback/index')">
        <text>意见反馈</text><text class="muted">›</text>
      </view>
    </view>

    <view v-if="user" class="card logout" @click="logout">退出登录</view>
  </view>
</template>

<style lang="scss" scoped>
.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: $lemon-primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  flex-shrink: 0;
}
.nickname {
  font-size: 34rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}
.streak {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.streak-num {
  font-size: 44rpx;
  font-weight: 700;
  color: $lemon-primary;
  line-height: 1.1;
}
.stats {
  display: flex;
}
.stat {
  flex: 1;
  text-align: center;
}
.num {
  font-size: 38rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
  font-variant-numeric: tabular-nums;
}
.menu {
  padding: 0 28rpx;
}
.menu-item {
  display: flex;
  justify-content: space-between;
  padding: 30rpx 0;
  border-bottom: 2rpx solid #f2f3f5;
  font-size: 30rpx;
}
.menu-item:last-child {
  border-bottom: none;
}
.logout {
  text-align: center;
  color: $lemon-danger;
}
</style>
