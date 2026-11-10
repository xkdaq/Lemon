<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getChapters } from '@/api/content'
import type { Chapter } from '@/api/types'
import { goPractice } from '@/utils/nav'

const chapters = ref<Chapter[]>([])
const loaded = ref(false)
let bankId = 0
let courseId = 0

onLoad((query) => {
  uni.setNavigationBarTitle({ title: decodeURIComponent(query?.name ?? '章节') })
  bankId = Number(query?.bankId)
  courseId = Number(query?.courseId)
})

// 从做题页返回时刷新进度
onShow(async () => {
  chapters.value = await getChapters(bankId, courseId)
  loaded.value = true
})

const percent = (c: Chapter) => (c.questionCount ? Math.min(100, Math.round((c.doneCount / c.questionCount) * 100)) : 0)
const accuracy = (c: Chapter) => (c.doneCount ? Math.round((c.correctCount / c.doneCount) * 100) : 0)
</script>

<template>
  <view>
    <view v-for="c in chapters" :key="c.id" class="card">
      <view class="name">{{ c.name }}</view>
      <view class="bar"><view class="bar-fill" :style="{ width: `${percent(c)}%` }" /></view>
      <view class="foot">
        <text class="muted">
          已做 {{ Math.min(c.doneCount, c.questionCount) }}/{{ c.questionCount }}
          <text v-if="c.doneCount"> · 正确率 {{ accuracy(c) }}%</text>
        </text>
        <view class="actions">
          <text class="btn btn-ghost" @click="goPractice(c.id, 'recite')">背题</text>
          <text class="btn" @click="goPractice(c.id, 'practice')">{{ c.inProgressModes.includes(1) ? '继续练习' : '练习' }}</text>
        </view>
      </view>
    </view>
    <view v-if="loaded && !chapters.length" class="empty muted">暂无章节</view>
  </view>
</template>

<style lang="scss" scoped>
.name {
  font-size: 30rpx;
  font-weight: 500;
}
.bar {
  height: 8rpx;
  border-radius: 8rpx;
  background: #f2f3f5;
  margin-top: 20rpx;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  background: $lemon-primary;
  border-radius: 8rpx;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}
.actions {
  display: flex;
  gap: 16rpx;
}
.btn {
  font-size: 24rpx;
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: $lemon-primary;
  color: #fff;
}
.btn-ghost {
  background: $lemon-primary-light;
  color: #b88a00;
}
.empty {
  text-align: center;
  padding: 120rpx 0;
}
</style>
