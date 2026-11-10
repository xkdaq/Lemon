<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getHome } from '@/api/content'
import type { Banner, ExamModule, LastSession, Notice } from '@/api/types'
import { assetUrl } from '@/utils/html'
import { modeLabel } from '@/api/practice'

const modules = ref<ExamModule[]>([])
const notices = ref<Notice[]>([])
const lastSession = ref<LastSession | null>(null)
const banners = ref<Banner[]>([])
/** 网站必须在首页底部展示 ICP 备案号（仅 H5） */
const icpNo = import.meta.env.VITE_ICP_NO

/** 模块卡片配色，按顺序循环 */
const themes = [
  { bg: 'linear-gradient(135deg, #ff8a65, #e34d59)', emoji: '📕' },
  { bg: 'linear-gradient(135deg, #64b5f6, #3a7bd5)', emoji: '📘' },
]

async function load() {
  const res = await getHome()
  modules.value = res.modules
  notices.value = res.notices
  lastSession.value = res.lastSession
  banners.value = res.banners
}

// 每次回到首页刷新“继续上次练习”
onShow(load)
onPullDownRefresh(async () => {
  await load().finally(() => uni.stopPullDownRefresh())
})

/** Banner 跳转：站内页面直接打开；外链只有 H5 能打开 */
function openBanner(b: Banner) {
  if (!b.link) return
  if (b.link.startsWith('/pages/')) {
    uni.navigateTo({ url: b.link, fail: () => uni.switchTab({ url: b.link! }) })
    return
  }
  // #ifdef H5
  window.open(b.link, '_blank', 'noopener')
  // #endif
  // #ifndef H5
  uni.setClipboardData({ data: b.link })
  // #endif
}

function openIcp() {
  // #ifdef H5
  window.open('https://beian.miit.gov.cn/', '_blank', 'noopener')
  // #endif
}

function continueLast() {
  uni.navigateTo({ url: `/pages/practice/index?sessionId=${lastSession.value!.sessionId}` })
}

function openModule(m: ExamModule) {
  uni.navigateTo({ url: `/pages/bank/list?moduleId=${m.id}&name=${encodeURIComponent(m.name)}` })
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">🍋 Lemon 刷题</view>
      <view class="hero-sub">考研政治 · 教育学，每天刷一点</view>
    </view>

    <swiper v-if="banners.length" class="banner" circular autoplay :indicator-dots="banners.length > 1" indicator-active-color="#fff">
      <swiper-item v-for="b in banners" :key="b.id" @click="openBanner(b)">
        <image class="banner-img" :src="assetUrl(b.image)" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <view v-if="notices.length" class="card notice">
      <text class="notice-tag">公告</text>
      <text class="notice-text">{{ notices[0].title }}</text>
    </view>

    <view class="modules">
      <view
        v-for="(m, i) in modules"
        :key="m.id"
        class="module"
        :style="{ background: themes[i % themes.length].bg }"
        @click="openModule(m)"
      >
        <text class="module-emoji">{{ themes[i % themes.length].emoji }}</text>
        <text class="module-name">{{ m.name }}</text>
        <text class="module-enter">进入题库 ›</text>
      </view>
    </view>

    <view v-if="lastSession" class="card continue" @click="continueLast">
      <view>
        <view class="continue-title">继续上次{{ modeLabel[lastSession.mode].replace('模式', '') }}</view>
        <view class="muted">{{ lastSession.bankName }} · {{ lastSession.chapterName }}</view>
      </view>
      <view class="continue-right">
        <text class="continue-progress">{{ lastSession.currentIndex + 1 }}/{{ lastSession.total }}</text>
        <text class="continue-go">继续 ›</text>
      </view>
    </view>
    <!-- #ifdef H5 -->
    <view v-if="icpNo" class="icp" @click="openIcp">{{ icpNo }}</view>
    <!-- #endif -->
  </view>
</template>

<style lang="scss" scoped>
.hero {
  padding: 48rpx 32rpx 16rpx;
}
.hero-title {
  font-size: 44rpx;
  font-weight: 700;
}
.hero-sub {
  margin-top: 8rpx;
  color: $lemon-muted;
}
.banner {
  margin: 24rpx 24rpx 0;
  height: 280rpx;
  border-radius: 20rpx;
  overflow: hidden;
}
.banner-img {
  width: 100%;
  height: 100%;
}
.notice {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.notice-tag {
  background: $lemon-primary-light;
  color: #b88a00;
  font-size: 22rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.icp {
  text-align: center;
  font-size: 22rpx;
  color: $lemon-muted;
  padding: 48rpx 0 24rpx;
}
.continue {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-left: 8rpx solid $lemon-primary;
}
.continue-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}
.continue-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.continue-progress {
  font-size: 32rpx;
  font-weight: 700;
  color: $lemon-primary;
}
.continue-go {
  font-size: 24rpx;
  color: $lemon-muted;
}
.modules {
  display: flex;
  gap: 24rpx;
  padding: 0 24rpx;
  margin-top: 24rpx;
}
.module {
  flex: 1;
  height: 280rpx;
  border-radius: 24rpx;
  padding: 32rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #fff;
}
.module-emoji {
  font-size: 56rpx;
}
.module-name {
  font-size: 40rpx;
  font-weight: 700;
}
.module-enter {
  font-size: 24rpx;
  opacity: 0.9;
}
</style>
