<script setup lang="ts">
import { getCurrentInstance, nextTick, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getHome } from '@/api/content'
import type { Banner, ExamModule, LastSession, Notice } from '@/api/types'
import { assetUrl } from '@/utils/html'
import { modeLabel } from '@/api/practice'
import { getStats, type Stats } from '@/api/mine'
import { isLoggedIn } from '@/utils/auth'
import { listCamps, type CampCard } from '@/api/punch'

const modules = ref<ExamModule[]>([])
const notices = ref<Notice[]>([])
const lastSession = ref<LastSession | null>(null)
const banners = ref<Banner[]>([])
/** 进行中的打卡活动（首页最多显示 3 个） */
const camps = ref<CampCard[]>([])
const HOME_CAMPS = 3
/** 登录后首页顶部显示今日作答次数 */
const stats = ref<Stats | null>(null)
/** 网站必须在首页底部展示 ICP 备案号（仅 H5） */
const icpNo = import.meta.env.VITE_ICP_NO

async function load() {
  const [res, s, c] = await Promise.all([
    getHome(),
    isLoggedIn() ? getStats().catch(() => null) : Promise.resolve(null),
    listCamps().catch(() => [] as CampCard[]),
  ])
  stats.value = s
  camps.value = c.filter((x) => x.endDate >= today())
  modules.value = res.modules
  notices.value = res.notices
  nextTick(measureNotice)
  lastSession.value = res.lastSession
  banners.value = res.banners
}

// 每次回到首页刷新“继续上次练习”
onShow(load)
onPullDownRefresh(async () => {
  await load().finally(() => uni.stopPullDownRefresh())
})

/** 公告：所有公告标题连成一行，放不下就从右往左循环滚动 */
const instance = getCurrentInstance()
const noticeScroll = ref(false)
const noticeDuration = ref(10)
const NOTICE_SPEED = 50 // 每秒滚动的像素
function measureNotice() {
  noticeScroll.value = false
  if (!notices.value.length) return
  nextTick(() => {
    uni.createSelectorQuery().in(instance?.proxy)
      .select('.notice-box').boundingClientRect()
      .select('.notice-item').boundingClientRect()
      .exec(([box, item]) => {
        if (!box || !item || item.width <= box.width) return
        noticeDuration.value = Math.max(6, Math.round(item.width / NOTICE_SPEED))
        noticeScroll.value = true
      })
  })
}
const noticeText = () => notices.value.map((n) => n.title).join('　　')

function openNotice() {
  const n = notices.value[0]
  if (notices.value.length === 1 && !n.content) return
  uni.showModal({
    title: notices.value.length === 1 ? '公告' : `公告（${notices.value.length}）`,
    content: notices.value.map((x) => (x.content ? `${x.title}\n${x.content}` : x.title)).join('\n\n'),
    showCancel: false,
    confirmText: '知道了',
  })
}

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

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function openAllCamps() {
  uni.navigateTo({ url: '/pages/punch/list' })
}

function openCamp(c: CampCard) {
  uni.navigateTo({ url: `/pages/punch/detail?id=${c.id}` })
}

function openModule(m: ExamModule) {
  uni.navigateTo({ url: `/pages/bank/list?moduleId=${m.id}&name=${encodeURIComponent(m.name)}` })
}
</script>

<template>
  <view class="page">
    <view v-if="stats" class="hero">
      <view class="hero-label">今日作答</view>
      <view><text class="hero-num">{{ stats.todayCount }}</text><text class="hero-unit">次</text></view>
      <view class="hero-sub">
        做过 {{ stats.questionTotal }} 题 · 正确率 {{ stats.accuracy }}%<text v-if="stats.streakDays"> · 连续 {{ stats.streakDays }} 天</text>
      </view>
    </view>
    <view v-else class="hero">
      <view class="hero-label">考研政治 · 教育学</view>
      <view class="brand">Lemon 刷题</view>
      <view class="hero-sub">每天刷一点，登录后记录进度</view>
    </view>

    <swiper v-if="banners.length" class="banner" circular autoplay :indicator-dots="banners.length > 1" indicator-active-color="#fff">
      <swiper-item v-for="b in banners" :key="b.id" @click="openBanner(b)">
        <image class="banner-img" :src="assetUrl(b.image)" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <view v-if="notices.length" class="card notice" @click="openNotice">
      <text class="tag">公告</text>
      <view class="notice-box">
        <view class="notice-track" :class="{ scrolling: noticeScroll }" :style="{ animationDuration: `${noticeDuration}s` }">
          <text class="notice-item">{{ noticeText() }}</text>
          <text v-if="noticeScroll" class="notice-item">{{ noticeText() }}</text>
        </view>
      </view>
    </view>

    <view v-if="lastSession" class="card continue">
      <view class="continue-bar">
        <view class="continue-fill" :style="{ width: `${Math.round(((lastSession.currentIndex + 1) / lastSession.total) * 100)}%` }" />
      </view>
      <view class="btn btn-primary btn-block" @click="continueLast">
        继续{{ modeLabel[lastSession.mode].replace('模式', '') }}：{{ lastSession.chapterName }}
      </view>
      <view class="muted continue-sub">
        {{ lastSession.bankName }} · 第 {{ lastSession.currentIndex + 1 }}/{{ lastSession.total }} 题
      </view>
    </view>

    <template v-if="camps.length">
      <view class="section-label punch-label">
        <text>每日打卡</text>
        <text v-if="camps.length > HOME_CAMPS" class="more" @click="openAllCamps">全部 ›</text>
      </view>
      <view class="card list-card">
        <view v-for="c in camps.slice(0, HOME_CAMPS)" :key="c.id" class="list-row" @click="openCamp(c)">
          <view class="module-main">
            <view class="module-name">{{ c.name }}</view>
            <view class="muted">每天 {{ c.questionCount }} 题 · {{ c.memberCount }} 人参加</view>
          </view>
          <text v-if="c.todayDone" class="tag tag-done">已打卡</text>
          <text v-else-if="c.joined" class="tag">去打卡</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
    </template>

    <view class="section-label">选择科目</view>
    <view class="card list-card">
      <view v-for="m in modules" :key="m.id" class="list-row" @click="openModule(m)">
        <view class="module-main">
          <view class="module-name">{{ m.name }}</view>
          <view class="muted">题库 · 模拟卷 · 章节练习</view>
        </view>
        <text class="row-arrow">›</text>
      </view>
    </view>
    <!-- #ifdef H5 -->
    <view v-if="icpNo" class="icp" @click="openIcp">{{ icpNo }}</view>
    <!-- #endif -->
  </view>
</template>

<style lang="scss" scoped>
.brand {
  font-size: 64rpx;
  font-weight: 700;
  line-height: 1.3;
  color: $lemon-primary;
}
.banner {
  margin: 24rpx 32rpx 0;
  height: 280rpx;
  border-radius: $lemon-radius;
  overflow: hidden;
}
.banner-img {
  width: 100%;
  height: 100%;
}
.notice {
  display: flex;
  align-items: center;
  gap: 14rpx;
  /* 细横幅：比普通卡片矮 */
  padding: 14rpx 24rpx;
  margin-top: 16rpx;
  margin-bottom: 16rpx;
  font-size: 26rpx;
  box-shadow: none;
}
.notice .tag {
  font-size: 20rpx;
  padding: 0 12rpx;
}
.notice-box {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.notice-track {
  display: flex;
  width: max-content;
  white-space: nowrap;
}
.notice-item {
  flex-shrink: 0;
}
.notice-track.scrolling .notice-item {
  padding-right: 96rpx;
}
.notice-track.scrolling {
  animation: notice-marquee linear infinite;
}
@keyframes notice-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}
.continue-bar {
  height: 6rpx;
  background: $lemon-line;
  border-radius: 4rpx;
  overflow: hidden;
  margin-bottom: 24rpx;
}
.continue-fill {
  height: 100%;
  background: $lemon-primary;
}
.continue-sub {
  text-align: center;
  margin-top: 16rpx;
}
.module-main {
  flex: 1;
}
.module-name {
  font-size: 32rpx;
  font-weight: 600;
}
.punch-label {
  display: flex;
  justify-content: space-between;
}
.more {
  color: $lemon-primary;
}
.tag-done {
  background: $lemon-success-soft;
  color: $lemon-success;
}
.icp {
  text-align: center;
  font-size: 22rpx;
  color: $lemon-muted;
  padding: 48rpx 0 24rpx;
}
</style>
