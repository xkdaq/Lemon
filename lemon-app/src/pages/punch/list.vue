<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { listCamps, type CampCard } from '@/api/punch'
import { assetUrl } from '@/utils/html'

const camps = ref<CampCard[]>([])
const loaded = ref(false)

async function load() {
  camps.value = await listCamps()
  loaded.value = true
}
onShow(load)
onPullDownRefresh(async () => {
  await load().finally(() => uni.stopPullDownRefresh())
})

const shortDate = (d: string) => d.slice(5).replace('-', '.')

function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const ended = (c: CampCard) => c.endDate < localToday()

function open(c: CampCard) {
  uni.navigateTo({ url: `/pages/punch/detail?id=${c.id}` })
}
</script>

<template>
  <view class="page">
    <view v-for="c in camps" :key="c.id" class="card camp" @click="open(c)">
      <image v-if="c.cover" class="cover" :src="assetUrl(c.cover)" mode="aspectFill" />
      <view class="camp-main">
        <view class="camp-name">{{ c.name }}</view>
        <view class="muted">每天 {{ c.questionCount }} 题 · {{ c.memberCount }} 人参加</view>
        <view class="muted">{{ shortDate(c.startDate) }} - {{ shortDate(c.endDate) }}</view>
      </view>
      <text v-if="ended(c)" class="tag tag-muted">已结束</text>
      <text v-else-if="c.todayDone" class="tag tag-done">今日已打卡</text>
      <text v-else-if="c.joined" class="tag">今日待打卡</text>
      <text v-else class="tag">加入</text>
    </view>
    <view v-if="loaded && !camps.length" class="empty">暂时没有打卡活动</view>
  </view>
</template>

<style lang="scss" scoped>
.camp {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.cover {
  width: 160rpx;
  height: 120rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
}
.camp-main {
  flex: 1;
  min-width: 0;
}
.camp-name {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}
.camp-main .muted {
  font-size: 24rpx;
}
.tag-done {
  background: $lemon-success-soft;
  color: $lemon-success;
}
.tag-muted {
  background: $lemon-line;
  color: $lemon-muted;
}
</style>
