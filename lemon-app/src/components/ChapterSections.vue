<script setup lang="ts">
import { ref } from 'vue'
import type { Chapter } from '@/api/types'
import { goPractice } from '@/utils/nav'
import ProgressBar from './ProgressBar.vue'

/** 章节下的“节”：按小节（单选题 / 论述题 / 材料分析题 …）展开，每节单独练习 */
const props = defineProps<{ chapter: Chapter }>()
const open = ref(false)
</script>

<template>
  <view class="sections">
    <view class="bar-row">
      <view v-if="props.chapter.sections.length" class="toggle" @click="open = !open">
        <text>{{ open ? '收起题型' : `按题型练习（${props.chapter.sections.length}）` }}</text>
        <text class="arrow" :class="{ up: open }">›</text>
      </view>
      <view v-else />
      <!-- 右侧：整章 / 整卷操作按钮 -->
      <slot name="actions" />
    </view>
    <view v-if="open" class="list">
      <view
        v-for="s in props.chapter.sections"
        :key="s.name"
        class="item"
        @click="goPractice(props.chapter.id, 'practice', s.name)"
      >
        <view class="item-head">
          <text class="item-name">{{ s.name }}</text>
          <text class="item-btn">{{ s.inProgress ? '继续' : s.doneCount ? '再练' : '开始' }} ›</text>
        </view>
        <ProgressBar :total="s.questionCount" :done="s.doneCount" :correct="s.correctCount" small />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.sections {
  margin-top: 20rpx;
}
.bar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toggle {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  font-size: 24rpx;
  color: $lemon-primary;
}
.arrow {
  display: inline-block;
  transform: rotate(90deg);
  transition: transform 0.2s;
}
.arrow.up {
  transform: rotate(-90deg);
}
.list {
  margin-top: 16rpx;
  border-top: 1px solid $lemon-line;
}
.item {
  padding: 20rpx 0 16rpx;
  border-bottom: 1px solid $lemon-line;
}
.item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.item-name {
  font-size: 28rpx;
}
.item-btn {
  font-size: 24rpx;
  color: $lemon-primary;
}
</style>
