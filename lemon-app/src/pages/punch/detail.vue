<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getCamp, getRank, joinCamp, startPunch, type CalendarDay, type CampDetail, type RankItem } from '@/api/punch'
import { isLoggedIn } from '@/utils/auth'
import { assetUrl } from '@/utils/html'
import { confirmModal } from '@/utils/modal'

/**
 * 打卡活动：加入、今日打卡、打卡日历（可补卡）、排行榜
 */
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']

const camp = ref<CampDetail>()
const rank = ref<RankItem[]>([])
const me = ref<RankItem | null>(null)
const busy = ref(false)
let campId = 0
/** 当前查看的月份 yyyy-MM，首次进入由后端决定 */
let month: string | undefined

onLoad((query) => {
  campId = Number(query?.id)
})

// 做完题回来刷新日历和排行
onShow(() => load())

async function load() {
  const [c, r] = await Promise.all([getCamp(campId, month), getRank(campId)])
  camp.value = c
  month = c.month
  rank.value = r.list
  me.value = r.me
  uni.setNavigationBarTitle({ title: c.name })
}

// ==================== 头部 ====================

const restText = computed(() => {
  const r = camp.value?.restWeekdays ?? []
  return r.length ? `周${r.map((d) => WEEKDAYS[d - 1]).join('、')}休息` : '每天打卡'
})
const shortDate = (d: string) => d.slice(5).replace('-', '月') + '日'

/** 主按钮 */
const action = computed<{ text: string; disabled?: boolean; run?: () => void }>(() => {
  const c = camp.value
  if (!c) return { text: '' }
  if (!isLoggedIn()) return { text: '登录后加入打卡', run: () => uni.navigateTo({ url: '/pages/login/index' }) }
  const today = c.today
  if (!c.joined) {
    const ended = !today && localToday() > c.endDate
    return ended ? { text: '活动已结束', disabled: true } : { text: '加入打卡', run: join }
  }
  if (!today) {
    return localToday() < c.startDate
      ? { text: `${shortDate(c.startDate)}开始打卡`, disabled: true }
      : { text: '活动已结束', disabled: true }
  }
  switch (today.state) {
    case 'rest':
      return { text: '今天休息，明天继续', disabled: true }
    case 'doing':
      return { text: '继续今日打卡', run: () => openDay(today) }
    case 'done':
      return { text: '今日已打卡 · 查看结果', run: () => openDay(today) }
    default:
      return { text: `开始今日打卡（${c.questionCount} 题）`, run: () => openDay(today) }
  }
})

async function join() {
  busy.value = true
  try {
    await joinCamp(campId)
    uni.showToast({ title: '已加入', icon: 'success' })
    await load()
  } finally {
    busy.value = false
  }
}

// ==================== 日历 ====================

function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 日历格子：前面补空格，让 1 号落在正确的星期 */
const cells = computed<(CalendarDay | null)[]>(() => {
  const days = camp.value?.calendar ?? []
  if (!days.length) return []
  const [y, m] = days[0].day.split('-').map(Number)
  const firstWeekday = (new Date(y, m - 1, 1).getDay() + 6) % 7 // 周一为 0
  return [...Array(firstWeekday).fill(null), ...days]
})

const monthTitle = computed(() => {
  const [y, m] = (camp.value?.month ?? '').split('-')
  return y ? `${y}年${Number(m)}月` : ''
})
const canPrev = computed(() => !!camp.value && camp.value.month > camp.value.startDate.slice(0, 7))
const canNext = computed(() => !!camp.value && camp.value.month < camp.value.endDate.slice(0, 7))

async function shiftMonth(delta: number) {
  const [y, m] = camp.value!.month.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  month = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  camp.value = await getCamp(campId, month)
}

/** 点日历上的某天 */
async function openDay(d: CalendarDay) {
  const c = camp.value
  if (!c || busy.value) return
  if (d.state === 'done' && d.sessionId) {
    uni.navigateTo({ url: `/pages/practice/result?sessionId=${d.sessionId}` })
    return
  }
  if (d.state === 'doing' && d.sessionId) {
    uni.navigateTo({ url: `/pages/practice/index?sessionId=${d.sessionId}` })
    return
  }
  if (!c.joined) {
    if (d.state === 'todo' || d.state === 'missed') uni.showToast({ title: '请先加入打卡', icon: 'none' })
    return
  }
  if (d.state === 'missed') {
    if (!c.makeupLeft) {
      uni.showToast({ title: c.makeupLimit ? '补卡次数已用完' : '该活动不支持补卡', icon: 'none' })
      return
    }
    const { confirm } = await confirmModal({
      title: `补卡 ${shortDate(d.day)}`,
      content: `将消耗 1 次补卡机会（剩余 ${c.makeupLeft} 次），开始后即扣除`,
      confirmText: '补卡',
    })
    if (!confirm) return
  } else if (d.state !== 'todo') {
    return
  }
  busy.value = true
  try {
    const s = await startPunch(campId, d.day)
    uni.navigateTo({ url: `/pages/practice/index?sessionId=${s.id}` })
  } finally {
    busy.value = false
  }
}

const isToday = (d: CalendarDay) => d.day === localToday()
</script>

<template>
  <view v-if="camp" class="page">
    <view class="card head">
      <image v-if="camp.cover" class="cover" :src="assetUrl(camp.cover)" mode="aspectFill" />
      <view class="name">{{ camp.name }}</view>
      <view class="muted">每天 {{ camp.questionCount }} 题 · {{ camp.memberCount }} 人参加 · 今日 {{ camp.todayCount }} 人已打卡</view>
      <view class="muted">{{ shortDate(camp.startDate) }} - {{ shortDate(camp.endDate) }} · {{ restText }}</view>

      <view v-if="camp.joined" class="stats">
        <view class="stat"><text class="stat-num">{{ camp.punchDays }}</text><text class="stat-label">已打卡（天）</text></view>
        <view class="stat"><text class="stat-num">{{ camp.streakDays }}</text><text class="stat-label">连续（天）</text></view>
        <view class="stat"><text class="stat-num">{{ camp.makeupLeft }}</text><text class="stat-label">补卡剩余</text></view>
      </view>

      <view
        class="btn btn-block main-btn"
        :class="action.disabled ? '' : 'btn-primary'"
        @click="!action.disabled && !busy && action.run?.()"
      >
        {{ action.text }}
      </view>
    </view>

    <view class="card">
      <view class="cal-head">
        <text class="cal-nav" :class="{ disabled: !canPrev }" @click="canPrev && shiftMonth(-1)">‹</text>
        <text class="cal-title">{{ monthTitle }}</text>
        <text class="cal-nav" :class="{ disabled: !canNext }" @click="canNext && shiftMonth(1)">›</text>
      </view>
      <view class="cal-grid">
        <text v-for="w in WEEKDAYS" :key="w" class="cal-week">{{ w }}</text>
        <view
          v-for="(d, i) in cells"
          :key="i"
          class="cal-cell"
          :class="d ? [`is-${d.state}`, { 'is-today': isToday(d) }] : ''"
          @click="d && openDay(d)"
        >
          <template v-if="d">
            <text class="cal-day">{{ Number(d.day.slice(8)) }}</text>
            <text class="cal-mark">{{ d.state === 'done' ? (d.makeup ? '补' : '✓') : d.state === 'rest' ? '休' : d.state === 'doing' ? '…' : '' }}</text>
          </template>
        </view>
      </view>
      <view class="legend muted">
        <text class="dot is-done" />已打卡 <text class="dot is-missed" />漏打（可补卡） <text class="dot is-todo" />今天
      </view>
    </view>

    <view v-if="camp.intro" class="card intro">
      <view class="card-title">活动介绍</view>
      <text class="intro-text">{{ camp.intro }}</text>
    </view>

    <view class="card">
      <view class="card-title">打卡排行</view>
      <view v-if="me" class="rank-row mine">
        <text class="rank-no">{{ me.rank }}</text>
        <text class="rank-name">我</text>
        <text class="rank-days">{{ me.days }} 天</text>
      </view>
      <view v-for="r in rank" :key="r.userId" class="rank-row">
        <text class="rank-no" :class="{ top: r.rank <= 3 }">{{ r.rank }}</text>
        <image v-if="r.avatar" class="avatar" :src="assetUrl(r.avatar)" mode="aspectFill" />
        <text class="rank-name">{{ r.nickname }}</text>
        <text class="rank-days">{{ r.days }} 天</text>
      </view>
      <view v-if="!rank.length" class="muted empty-rank">还没有人打卡，来做第一个吧</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  padding-bottom: 48rpx;
}
.cover {
  width: 100%;
  height: 280rpx;
  border-radius: 20rpx;
  margin-bottom: 20rpx;
}
.name {
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}
.head .muted {
  font-size: 24rpx;
  margin-top: 4rpx;
}
.stats {
  display: flex;
  margin: 28rpx 0 8rpx;
}
.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-num {
  font-size: 40rpx;
  font-weight: 700;
  color: $lemon-primary;
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: 22rpx;
  color: $lemon-muted;
}
.main-btn {
  margin-top: 24rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

/* ---------- 日历 ---------- */
.cal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.cal-title {
  font-size: 30rpx;
  font-weight: 600;
}
.cal-nav {
  font-size: 40rpx;
  padding: 0 24rpx;
  color: $lemon-primary;
}
.cal-nav.disabled {
  color: $lemon-line;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 10rpx;
}
.cal-week {
  text-align: center;
  font-size: 22rpx;
  color: $lemon-muted;
  padding-bottom: 4rpx;
}
.cal-cell {
  height: 84rpx;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-variant-numeric: tabular-nums;
}
.cal-day {
  font-size: 26rpx;
  line-height: 1.2;
}
.cal-mark {
  font-size: 20rpx;
  line-height: 1.2;
  min-height: 24rpx;
}
/* 不在活动期内（或早于加入日期）的日子淡化；未到的日子保持正常颜色 */
.is-out .cal-day {
  color: $lemon-muted;
  opacity: 0.5;
}
.is-rest {
  color: $lemon-muted;
}
.is-done {
  background: $lemon-success-soft;
  color: $lemon-success;
}
.is-doing {
  background: $lemon-primary-light;
  color: $lemon-primary;
}
.is-missed {
  background: $lemon-danger-soft;
  color: $lemon-danger;
}
.is-todo {
  background: $lemon-primary-light;
  color: $lemon-primary;
}
.is-today {
  box-shadow: inset 0 0 0 2rpx $lemon-primary;
}
.legend {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  margin-top: 20rpx;
}
.dot {
  width: 18rpx;
  height: 18rpx;
  border-radius: 6rpx;
  margin-left: 12rpx;
}

/* ---------- 介绍 / 排行 ---------- */
.intro-text {
  font-size: 26rpx;
  line-height: 1.7;
  white-space: pre-wrap;
  color: $lemon-text;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid $lemon-line;
}
.rank-row:last-child {
  border-bottom: none;
}
.rank-row.mine {
  background: $lemon-primary-light;
  border-radius: 16rpx;
  padding: 18rpx 16rpx;
  border-bottom: none;
  margin-bottom: 8rpx;
}
.rank-no {
  width: 56rpx;
  text-align: center;
  font-weight: 700;
  color: $lemon-muted;
  font-variant-numeric: tabular-nums;
}
.rank-no.top {
  color: $lemon-primary;
}
.avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
}
.rank-name {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.rank-days {
  color: $lemon-muted;
  font-size: 26rpx;
}
.empty-rank {
  text-align: center;
  padding: 24rpx 0;
}
</style>
