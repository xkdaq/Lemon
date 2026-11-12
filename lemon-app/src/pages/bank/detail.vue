<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getBankDetail } from '@/api/content'
import { BankType, type Chapter, type Course, type QuestionBank } from '@/api/types'
import { goPractice } from '@/utils/nav'
import ProgressBar from '@/components/ProgressBar.vue'
import ChapterSections from '@/components/ChapterSections.vue'
import AccessBanner from '@/components/AccessBanner.vue'

/**
 * 题库详情：
 * - 科目型：显示科目列表（马原、毛中特…），点进去看章节
 * - 试卷型：直接显示试卷列表
 */
const bank = ref<QuestionBank>()
const courses = ref<Course[]>([])
const papers = ref<Chapter[]>([])

let bankId = 0
onLoad((query) => {
  bankId = Number(query?.bankId)
})

// 从考试页返回时刷新成绩
onShow(async () => {
  const res = await getBankDetail(bankId)
  bank.value = res.bank
  courses.value = res.courses
  papers.value = res.chapters
  // 科目型题库只有一个科目（如“章节1000题”）：直接进章节列表，省一次点击
  if (res.bank.type === BankType.COURSE && res.courses.length === 1) {
    const c = res.courses[0]
    uni.redirectTo({ url: `/pages/chapter/list?bankId=${c.bankId}&courseId=${c.id}&moduleId=${res.bank.moduleId}&name=${encodeURIComponent(res.bank.name)}` })
    return
  }
  uni.setNavigationBarTitle({ title: res.bank.name })
})

/** 回看最近一次考试：成绩页，可继续「查看解析」看每题的作答 */
function openLastExam(p: Chapter) {
  if (p.lastExamId) uni.navigateTo({ url: `/pages/practice/result?sessionId=${p.lastExamId}` })
}

/** 整个题库的总进度（试卷型） */
const sum = (k: 'questionCount' | 'doneCount' | 'correctCount') => papers.value.reduce((n, p) => n + p[k], 0)
const total = computed(() => ({ total: sum('questionCount'), done: sum('doneCount'), correct: sum('correctCount') }))
const accuracy = computed(() => (total.value.done ? Math.round((total.value.correct / total.value.done) * 100) : 0))

function openCourse(c: Course) {
  uni.navigateTo({ url: `/pages/chapter/list?bankId=${c.bankId}&courseId=${c.id}&moduleId=${bank.value?.moduleId ?? ''}&name=${encodeURIComponent(c.name)}` })
}

</script>

<template>
  <view v-if="bank">
    <AccessBanner :module-id="bank.moduleId" />
    <template v-if="bank.type === BankType.COURSE">
      <view class="section-label">选择科目</view>
      <view v-if="courses.length" class="card list-card">
        <view v-for="c in courses" :key="c.id" class="list-row" @click="openCourse(c)">
          <text class="name">{{ c.name }}</text>
          <text class="row-arrow">›</text>
        </view>
      </view>
      <view v-else class="empty">暂无科目</view>
    </template>

    <template v-else>
      <view v-if="papers.length" class="hero">
        <view class="hero-label">{{ bank.name }} · {{ papers.length }} 套</view>
        <view><text class="hero-num">{{ total.done }}</text><text class="hero-unit">/ {{ total.total }} 题</text></view>
        <view class="hero-sub">{{ total.done ? `正确率 ${accuracy}%` : '还没开始，挑一套试试' }} · 展开试卷可按题型练习</view>
      </view>
      <view v-if="papers.length" class="card">
        <ProgressBar :total="total.total" :done="total.done" :correct="total.correct" />
      </view>
      <view class="section-label">选择试卷</view>
      <view v-for="p in papers" :key="p.id" class="card">
        <view class="paper-head">
          <view class="name">{{ p.name }}</view>
          <text v-if="p.lastScore != null" class="tag last-exam" @click="openLastExam(p)">上次 {{ p.lastScore }} 分 · 查看 ›</text>
        </view>
        <view class="muted">
          {{ p.questionCount }} 题<text v-if="p.timeLimit"> · {{ p.timeLimit }} 分钟</text><text v-if="p.totalScore"> · 满分 {{ p.totalScore }}</text>
        </view>
        <view class="paper-progress">
          <ProgressBar :total="p.questionCount" :done="p.doneCount" :correct="p.correctCount" />
        </view>
        <ChapterSections :chapter="p">
          <template #actions>
            <view class="paper-actions">
              <text class="btn btn-small" @click="goPractice(p.id, 'practice')">{{ p.inProgressModes.includes(1) ? '继续练习' : '逐题练习' }}</text>
              <text class="btn btn-small btn-primary" @click="goPractice(p.id, 'exam')">{{ p.inProgressModes.includes(3) ? '继续考试' : '模拟考试' }}</text>
            </view>
          </template>
        </ChapterSections>
      </view>
      <view v-if="!papers.length" class="empty">暂无试卷</view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
/* 可点：回看最近一次考试 */
.last-exam {
  padding: 6rpx 18rpx;
  font-size: 24rpx;
}
.name {
  font-size: 32rpx;
  font-weight: 600;
}
.paper-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}
.paper-progress {
  margin-top: 20rpx;
}
.paper-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
}
</style>
