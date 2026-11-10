<script setup lang="ts">
import { computed } from 'vue'

/**
 * 三色进度条：
 * - 底色：总题数
 * - 浅色：已刷题数
 * - 深色：答对题数（叠在已刷之上，深色占浅色的比例就是正确率）
 */
const props = withDefaults(defineProps<{ total: number; done: number; correct: number; small?: boolean; showText?: boolean }>(), {
  small: false,
  showText: true,
})

const pct = (n: number) => (props.total > 0 ? Math.min(100, (Math.min(n, props.total) / props.total) * 100) : 0)
const donePct = computed(() => pct(props.done))
const correctPct = computed(() => pct(props.correct))
const accuracy = computed(() => (props.done > 0 ? Math.round((props.correct / props.done) * 100) : 0))
</script>

<template>
  <view class="progress" :class="{ small }">
    <view class="track">
      <view class="done" :style="{ width: `${donePct}%` }" />
      <view class="correct" :style="{ width: `${correctPct}%` }" />
    </view>
    <view v-if="showText" class="text">
      <text><text class="dot dot-total" />共 {{ total }} 题</text>
      <text><text class="dot dot-done" />已刷 {{ Math.min(done, total) }}</text>
      <text><text class="dot dot-correct" />正确率 {{ done ? `${accuracy}%` : '--' }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
$track: $lemon-line;
$done: var(--done-color);
$correct: $lemon-primary;

.track {
  position: relative;
  height: 16rpx;
  border-radius: 8rpx;
  background: $track;
  overflow: hidden;
}
.small .track {
  height: 10rpx;
}
.done,
.correct {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 14rpx;
  transition: width 0.3s;
}
.done {
  background: $done;
}
.correct {
  background: $correct;
}
.text {
  display: flex;
  gap: 24rpx;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: $lemon-muted;
}
.small .text {
  font-size: 20rpx;
  gap: 16rpx;
  margin-top: 8rpx;
}
.dot {
  display: inline-block;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 6rpx;
  vertical-align: middle;
  position: relative;
  top: -2rpx;
}
.dot-total {
  background: $track;
  box-shadow: inset 0 0 0 1px $lemon-muted;
}
.dot-done {
  background: $done;
}
.dot-correct {
  background: $correct;
}
</style>
