<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getChapters } from '@/api/content'
import type { Chapter } from '@/api/types'
import { goPractice } from '@/utils/nav'
import ProgressBar from '@/components/ProgressBar.vue'
import ChapterSections from '@/components/ChapterSections.vue'

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

/** 本科目总进度 */
const sum = (k: 'questionCount' | 'doneCount' | 'correctCount') => chapters.value.reduce((n, c) => n + c[k], 0)
const total = computed(() => ({ total: sum('questionCount'), done: sum('doneCount'), correct: sum('correctCount') }))
</script>

<template>
  <view>
    <view v-if="chapters.length" class="card">
      <view class="summary-title">总进度</view>
      <ProgressBar :total="total.total" :done="total.done" :correct="total.correct" />
    </view>
    <view v-for="c in chapters" :key="c.id" class="card">
      <view class="name">{{ c.name }}</view>
      <view class="progress">
        <ProgressBar :total="c.questionCount" :done="c.doneCount" :correct="c.correctCount" />
      </view>
      <ChapterSections :chapter="c">
        <template #actions>
          <view class="actions">
            <text class="btn btn-ghost" @click="goPractice(c.id, 'recite')">背题</text>
            <text class="btn" @click="goPractice(c.id, 'practice')">{{ c.inProgressModes.includes(1) ? '继续练习' : '练习' }}</text>
          </view>
        </template>
      </ChapterSections>
    </view>
    <view v-if="loaded && !chapters.length" class="empty muted">暂无章节</view>
  </view>
</template>

<style lang="scss" scoped>
.name {
  font-size: 30rpx;
  font-weight: 500;
}
.progress {
  margin-top: 20rpx;
}
.summary-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
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
