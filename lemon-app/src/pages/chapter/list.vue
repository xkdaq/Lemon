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
const accuracy = computed(() => (total.value.done ? Math.round((total.value.correct / total.value.done) * 100) : 0))
</script>

<template>
  <view>
    <view v-if="chapters.length" class="hero">
      <view class="hero-label">本科目 · {{ chapters.length }} 章</view>
      <view><text class="hero-num">{{ total.done }}</text><text class="hero-unit">/ {{ total.total }} 题</text></view>
      <view class="hero-sub">{{ total.done ? `正确率 ${accuracy}%` : '还没开始，从第一章刷起' }} · 展开章节可按题型练习</view>
    </view>
    <view v-if="chapters.length" class="card">
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
            <text class="btn btn-small" @click="goPractice(c.id, 'recite')">背题</text>
            <text class="btn btn-small btn-primary" @click="goPractice(c.id, 'practice')">{{ c.inProgressModes.includes(1) ? '继续练习' : '练习' }}</text>
          </view>
        </template>
      </ChapterSections>
    </view>
    <view v-if="loaded && !chapters.length" class="empty">暂无章节</view>
  </view>
</template>

<style lang="scss" scoped>
.name {
  font-size: 32rpx;
  font-weight: 600;
}
.progress {
  margin-top: 20rpx;
}
.actions {
  display: flex;
  gap: 16rpx;
}
</style>
