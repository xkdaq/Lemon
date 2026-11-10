<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getBankDetail } from '@/api/content'
import { BankType, type Chapter, type Course, type QuestionBank } from '@/api/types'
import { goPractice } from '@/utils/nav'

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
  uni.setNavigationBarTitle({ title: res.bank.name })
})

function openCourse(c: Course) {
  uni.navigateTo({ url: `/pages/chapter/list?bankId=${c.bankId}&courseId=${c.id}&name=${encodeURIComponent(c.name)}` })
}

</script>

<template>
  <view v-if="bank">
    <template v-if="bank.type === BankType.COURSE">
      <view class="section muted">选择科目</view>
      <view v-for="c in courses" :key="c.id" class="card row" @click="openCourse(c)">
        <text class="name">{{ c.name }}</text>
        <text class="muted">›</text>
      </view>
      <view v-if="!courses.length" class="empty muted">暂无科目</view>
    </template>

    <template v-else>
      <view class="section muted">选择试卷</view>
      <view v-for="p in papers" :key="p.id" class="card">
        <view class="paper-head">
          <view class="name">{{ p.name }}</view>
          <text v-if="p.lastScore != null" class="score">上次 {{ p.lastScore }} 分</text>
        </view>
        <view class="muted meta">
          {{ p.questionCount }} 题<text v-if="p.timeLimit"> · {{ p.timeLimit }} 分钟</text><text v-if="p.totalScore"> · 满分 {{ p.totalScore }}</text>
        </view>
        <view class="paper-actions">
          <text class="btn btn-ghost" @click="goPractice(p.id, 'practice')">{{ p.inProgressModes.includes(1) ? '继续练习' : '逐题练习' }}</text>
          <text class="btn" @click="goPractice(p.id, 'exam')">{{ p.inProgressModes.includes(3) ? '继续考试' : '模拟考试' }}</text>
        </view>
      </view>
      <view v-if="!papers.length" class="empty muted">暂无试卷</view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.section {
  padding: 24rpx 32rpx 0;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.name {
  font-size: 30rpx;
  font-weight: 500;
}
.meta {
  margin-top: 8rpx;
}
.paper-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.score {
  font-size: 24rpx;
  color: #e37318;
}
.paper-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 20rpx;
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
