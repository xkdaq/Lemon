<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as authApi from '@/api/auth'
import { getStats, type Stats } from '@/api/mine'
import type { AppUser } from '@/api/types'
import { isLoggedIn } from '@/utils/auth'

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
const openProfile = () => uni.navigateTo({ url: '/pages/profile/index' })
/** 设置不需要登录（主题、字号是本机设置） */
const openSettings = () => uni.navigateTo({ url: '/pages/settings/index' })
const go = (url: string) => (user.value ? uni.navigateTo({ url }) : goLogin())
</script>

<template>
  <view>
    <view class="card profile" @click="user ? openProfile() : goLogin()">
      <view class="avatar">{{ user?.nickname?.slice(0, 1) ?? '柠' }}</view>
      <view>
        <view class="nickname">{{ user?.nickname ?? '点击登录' }}</view>
        <view class="muted">{{ user ? `用户名 ${user.username}` : '登录后同步做题记录' }}</view>
      </view>
      <view v-if="stats?.streakDays" class="streak">
        <text class="streak-num">{{ stats.streakDays }}</text>
        <text class="muted">天连续打卡</text>
      </view>
      <text v-if="user" class="row-arrow profile-arrow">›</text>
    </view>

    <view class="card stats">
      <view class="stat"><view class="num">{{ stats?.questionTotal ?? '-' }}</view><view class="muted">做过题数</view></view>
      <view class="stat"><view class="num">{{ stats ? `${stats.accuracy}%` : '-' }}</view><view class="muted">正确率</view></view>
      <view class="stat"><view class="num">{{ stats?.todayCount ?? '-' }}</view><view class="muted">今日作答</view></view>
      <view class="stat"><view class="num">{{ stats?.answerTotal ?? '-' }}</view><view class="muted">累计作答</view></view>
    </view>

    <view class="collections">
      <view class="collection wrong" @click="go('/pages/wrong/index')">
        <view class="collection-name">错题集</view>
        <view class="collection-count">{{ stats ? stats.wrongCount : '-' }}<text class="unit"> 题</text></view>
        <view class="collection-tip">答错自动收录，可错题重练</view>
      </view>
      <view class="collection favorite" @click="go('/pages/favorite/index')">
        <view class="collection-name">收藏集</view>
        <view class="collection-count">{{ stats ? stats.favoriteCount : '-' }}<text class="unit"> 题</text></view>
        <view class="collection-tip">做题时点“收藏”加入</view>
      </view>
    </view>

    <view class="card list-card">
      <view class="list-row" @click="go('/pages/activate/index')">
        <text class="menu-text">我的模块</text><text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="go('/pages/note/list')">
        <text class="menu-text">我的笔记</text><text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="go('/pages/restore/index')">
        <text class="menu-text">恢复老系统错题</text><text class="row-arrow">›</text>
      </view>
      <view class="list-row" @click="openSettings">
        <text class="menu-text">设置</text><text class="row-arrow">›</text>
      </view>
    </view>

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
  color: $lemon-primary;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  flex-shrink: 0;
}
.nickname {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}
.profile-arrow {
  margin-left: auto;
}
.streak + .profile-arrow {
  margin-left: 8rpx;
}
.streak {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.streak-num {
  font-size: 40rpx;
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
  font-size: 36rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
  font-variant-numeric: tabular-nums;
}
.collections {
  display: flex;
  gap: 24rpx;
  margin: 0 32rpx;
}
.collection {
  flex: 1;
  background: $lemon-surface;
  border: 1px solid $lemon-line;
  border-radius: $lemon-radius;
  box-shadow: $lemon-shadow;
  padding: 32rpx;
}
.collection-name {
  font-size: 30rpx;
  font-weight: 600;
}
.collection-count {
  font-size: 52rpx;
  font-weight: 700;
  line-height: 1.2;
  margin: 12rpx 0 4rpx;
  color: $lemon-primary;
  font-variant-numeric: tabular-nums;
}
.unit {
  font-size: 26rpx;
  font-weight: 500;
  color: $lemon-text;
}
.collection-tip {
  font-size: 22rpx;
  color: $lemon-muted;
}
.menu-text {
  flex: 1;
  font-size: 30rpx;
}
</style>
